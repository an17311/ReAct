package cn.itcast.manus.agent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.itcast.manus.agent.bean.ReActInput;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.config.ReActConfig;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.util.JsonFinder;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ReAct（Reasoning + Acting）智能体抽象基类，是引擎核心。
 * <p>负责驱动“思考→行动→观察”循环：定义统一的工具 Schema（{@code AgentOutput}），强制大模型以工具调用
 * 的形式输出结构化动作，再由程序手动解析并执行动作，将观察结果回写历史消息，直到 {@code done} 或超过最大步数。</p>
 * <p>子类通过 {@link #getCurrentStatus()}、{@link #addInitInput} 与 {@link #toolCallbackProvider()} 提供各自的提示词与工具能力。</p>
 */
@Slf4j
public abstract class ReActBaseAgent extends BaseAgent {

    // 统一工具（函数）名称：大模型必须以该工具名输出 ReAct 结构化结果
    public static final String AGENT_OUTPUT_METHOD = "AgentOutput";
    // 主智能体使用的聊天模型（可由子类 chatModel() 覆盖）
    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    // 当前执行步数
    @Getter
    protected int currentStep = 1;
    // ReAct 运行配置（最大步数、每次最大动作数等），prototype 作用域
    @Resource
    protected ReActConfig reActConfig;
    // 主模型配置（包含是否流式等开关）
    @Resource(name = ModelConfig.MAIN_AGENT_MODEL_CONFIG)
    private ModelConfig.BaseModelConfig modelConfig;
    // 是否停止标识
    private boolean agentStop = false;
    // 最终结果
    private String finalResult;
    // 工具定义结构内容（JSON Schema，会随子类工具合并而动态扩充）
    private String toolSchema;
    // 消息会话，用于向客户端实时推送中间结果
    protected final MessageSession messageSession;
    // 由 toolSchema 解析得知的工具定义，供大模型调用
    private ToolDefinition toolDefinition;
    // 定义动作映射，key为动作名称，value为动作执行函数（key：参数，value：执行返回值）
    private final Map<String, Function<Map<String, Object>, String>> actionRegistry = new HashMap<>();

    // 动作执行专用线程池（守护线程），用于动作超时控制
    private static final ExecutorService ACTION_EXECUTOR = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "react-action-worker");
        t.setDaemon(true);
        return t;
    });
    // token 估算器，用于上下文预算控制
    private final TokenCountEstimator tokenEstimator = new JTokkitTokenCountEstimator();
    // 初始输入消息边界（System/任务/示例），上下文压缩时始终保留
    private int initInputSize = 0;
    // 连续解析失败计数
    private int parseFailCount = 0;
    // 最近一次动作观察结果（解析失败达到上限强制终止时，用于兜底说明）
    private String lastObservation = "";

    /**
     * 使用默认的 ReAct 工具 Schema 构造智能体。
     *
     * @param messageSession 消息会话
     */
    public ReActBaseAgent(MessageSession messageSession) {
        this(messageSession, "schema/schemaBaseReAct.json");
    }

    /**
     * 指定工具 Schema 资源路径构造智能体。
     *
     * @param messageSession 消息会话
     * @param toolSchemaPath 类路径下的工具 Schema 文件位置
     */
    public ReActBaseAgent(MessageSession messageSession, String toolSchemaPath) {
        this.messageSession = messageSession;
        this.toolSchema = ResourceUtil.readStr(toolSchemaPath, CharsetUtil.CHARSET_UTF_8);
        this.refreshToolDefinition();
    }

    /**
     * 根据当前的 {@code toolSchema} 重新解析并刷新工具定义 {@code toolDefinition}。
     */
    private void refreshToolDefinition() {
        JSONObject jsonObject = JSONUtil.parseObj(this.toolSchema);
        this.toolDefinition = DefaultToolDefinition.builder()
                .name(jsonObject.getStr("name"))
                .description(jsonObject.getStr("description"))
                .inputSchema(jsonObject.getStr("inputSchema"))
                .build();
    }

    /**
     * 获取当前智能体使用的聊天模型，子类可覆盖以切换不同的模型。
     *
     * @return ChatModel 实例
     */
    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }

    /**
     *  {@link BaseAgent#solve} 的实现，直接委托给 ReAct 主循环 {@link #reActSolve(String)}。
     *
     * @param task 任务描述
     * @return 任务最终结果
     */
    @Override
    protected String solve(String task) {
        return this.reActSolve(task);
    }

    /**
     * ReAct 主循环：驱动“思考→行动→观察”直至任务完成或达到最大步数。
     *
     * @param task 任务描述
     * @return 最终输出结果（done 动作携带的文本）
     */
    public String reActSolve(String task) {
        //1. 初始化动作列表（注册 done 动作，并合并子类提供的工具）
        this.initActionMap();

        //2. 定义输入消息列表（由子类填入 System/User 提示词及示例）
        var inputList = new LinkedList<Message>();
        this.addInitInput(inputList, task);
        // 记录初始输入边界，上下文压缩时始终保留这一段
        this.initInputSize = inputList.size();
        this.parseFailCount = 0;

        //3. 循环执行，结束条件：任务终止（done动作） 或 任务的执行步骤已经超过最大的步骤
        var tokenCount = new AtomicLong(0L);
        // 初始化当前步骤
        this.currentStep = 1;
        while (this.currentStep <= this.reActConfig.getMaxStep() && !this.agentStop) {
            // 取消/客户端失活检查：命中则立即终止，避免对已离开的用户空烧 token
            if (this.checkAborted()) {
                break;
            }
            // 上下文超出 token 预算时，压缩早期历史
            this.compressHistoryIfNeeded(inputList);
            // 将当前的Agent的状态加入到消息列表中（每步提供最新上下文）
            this.buildCurrentParse(inputList);
            // 调用大模型，并在其内部依次触发响应解析与动作执行
            this.callingLLM(inputList, tokenCount);
            log.info("step:{},tokenCount:{}", this.currentStep, tokenCount.get());
            log.info("[{}👆]------------------", getClass().getSimpleName());
            this.currentStep++;
        }

        //4. 检查步骤是否已经超过了最大步数，如果已经超过（且未被取消/失活），就调用大模型来触发执行done动作
        if (this.currentStep >= this.reActConfig.getMaxStep() && !this.agentStop && !this.isAborted()) {
            // 提示模型立即用 done 总结当前成果，避免因超步数而无结果
            String text = """
                    注意，你的执行已达到最大步数限制，请立即使用done操作返回截至到目前的工作成果，
                    需要包含具体的内容，不允许任何省略，最后总结尚未完成的任务并对遇到的问题加以说明
                    """;
            inputList.add(new UserMessage(text));
            this.callingLLM(inputList, tokenCount);
        }

        //5. 返回最终的输出结果（兜底保证非 null：超步数后的强制 done 也可能失败或不产出有效文本）
        if (StrUtil.isBlank(this.finalResult)) {
            return StrUtil.format("[{}]:任务未能正常完成（达到最大步数限制或模型未返回有效结果），请查看服务端日志", this.name());
        }
        return this.finalResult;
    }

    /**
     * 调用大模型：构造强制工具调用参数→发起请求→统计 token→将响应交由 {@link #processResponse} 处理。
     *
     * @param inputList  当前的对话消息列表
     * @param tokenCount 累计 token 消耗（就地累加）
     */
    private void callingLLM(LinkedList<Message> inputList, AtomicLong tokenCount) {
        //1. 构造请求参数
        var chatOptions = OpenAiChatOptions.builder()
                .toolCallbacks(this.getToolCallbacks()) // 设置工具（仅声明 AgentOutput 一个工具）
                .toolChoice(OpenAiApi.ChatCompletionRequest.ToolChoiceBuilder.FUNCTION(AGENT_OUTPUT_METHOD)) // 强制指定工具名称，确保模型必定以 AgentOutput 输出
                .internalToolExecutionEnabled(false) //禁用SpringAI自动执行tool，手动执行（由 agentOutput 自行调度动作）
                .build();

        //2. 创建prompt对象（携带完整历史消息与选项）
        var prompt = new Prompt(inputList, chatOptions);

        //3. 调用AI大模型（最多尝试3次，指数退避1s/2s/4s；耗尽后抛出，由上层兜底为结构化错误）
        ChatResponse chatResponse = null;
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3 && chatResponse == null; attempt++) {
            try {
                if (this.modelConfig.isStream()) {
                    // 流式执行：收集所有分片后取第一个完整响应（因为工具调用需要完整参数）
                    chatResponse = this.chatModel()
                            .stream(prompt)
                            .collectList()
                            .block()
                            .stream()
                            .findFirst()
                            .orElseThrow();
                } else {
                    // 非流式执行
                    chatResponse = this.chatModel().call(prompt);
                }
            } catch (Exception e) {
                lastError = new RuntimeException(e);
                log.warn("调用AI大模型第{}次失败", attempt, e);
                if (attempt < 3) {
                    try {
                        Thread.sleep(1000L * (1 << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastError;
                    }
                }
            }
        }
        if (chatResponse == null) {
            log.error("调用AI大模型时出错，重试耗尽！", lastError);
            throw lastError;
        }

        //4. 解析大模型返回的结果
        //4.1 统计消耗的token数量（累加到总数）
        Optional.of(chatResponse)
                .map(ChatResponse::getMetadata)
                .map(ChatResponseMetadata::getUsage)
                .map(Usage::getTotalTokens)
                .ifPresent(tokenCount::addAndGet);

        //4.2 获取生成的内容，交由 processResponse 解析并执行动作
        Optional.of(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .ifPresent(assistantMessage -> this.processResponse(assistantMessage, inputList));
    }

    /**
     * 解析大模型响应：优先从工具调用（AgentOutput）提取，否则从文本兜底提取 JSON。
     *
     * @param assistantMessage 模型生成的助手消息
     * @param inputList        当前对话消息列表（供后续重建历史）
     */
    private void processResponse(AssistantMessage assistantMessage, List<Message> inputList) {
        var text = assistantMessage.getText();
        log.info("开始处理大模型的响应，内容：{}", text);

        // 情况 A：模型以工具调用形式输出（正常路径，因已强制 toolChoice）
        if (assistantMessage.hasToolCalls()) {
            if (assistantMessage.getToolCalls().size() > 1) {
                log.warn("找到1个以上的工具执行请求列表:{}", assistantMessage.getToolCalls());
            }
            var tool = CollUtil.getFirst(assistantMessage.getToolCalls());
            if (!StrUtil.equalsIgnoreCase(tool.name(), AGENT_OUTPUT_METHOD)) {
                log.warn("工具名称不符:{}", tool.name());
                this.handleParseFailure(assistantMessage, inputList);
                return;
            }
            try {
                // 将工具参数 JSON 反序列化为 ReActInput，进入动作执行
                ReActInput reActInput = JSONUtil.toBean(tool.arguments(), ReActInput.class);
                this.agentOutput(reActInput, inputList, assistantMessage);
            } catch (Exception e) {
                // 参数 JSON 非法或状态字段缺失等：按解析失败处理，由下一轮修正重试
                log.warn("AgentOutput 参数解析失败:{}", e.getMessage());
                this.handleParseFailure(assistantMessage, inputList);
            }
            return;
        }

        // 情况 B：未触发工具调用，则从纯文本中查找第一个合法 JSON 作为兜底解析
        var reActInputOpt = JsonFinder.findFirstJson(text)
                .map(json -> JSONUtil.toBean(json, ReActInput.class));
        if (reActInputOpt.isPresent()) {
            try {
                this.agentOutput(reActInputOpt.get(), inputList, assistantMessage);
            } catch (Exception e) {
                log.warn("文本兜底解析的输出处理失败:{}", e.getMessage());
                this.handleParseFailure(assistantMessage, inputList);
            }
            return;
        }

        // 情况 C：完全无法解析（无工具调用且文本中无合法 JSON）
        this.handleParseFailure(assistantMessage, inputList);
    }

    /**
     * 解析失败处理：重建历史（移除本轮状态消息、保留模型原始输出），追加修正提示由下一轮重试；
     * 连续失败达到上限时强制终止任务，避免死循环空烧 token。
     */
    private void handleParseFailure(AssistantMessage assistantMessage, List<Message> inputList) {
        this.parseFailCount++;
        // 移除本轮由 buildCurrentParse 加入的状态消息，保持历史协议一致
        inputList.remove(inputList.size() - 1);
        // 保留模型原始输出，供模型看到自己上一轮的错误
        inputList.add(assistantMessage);
        if (assistantMessage.hasToolCalls()) {
            // 因禁用了 SpringAI 自动执行，需手动补充工具响应消息，否则下一轮请求会因协议不完整被 API 拒绝
            var toolCall = CollUtil.getFirst(assistantMessage.getToolCalls());
            inputList.add(new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "ignored: invalid tool call"))));
        }

        if (this.parseFailCount >= this.reActConfig.getParseFailRetryLimit()) {
            // 达到修正重试上限：强制终止并返回已有成果说明
            log.warn("连续{}次解析失败，强制终止任务", this.parseFailCount);
            this.done(StrUtil.format("[系统]模型连续{}次未能按要求通过AgentOutput输出结构化结果，任务已终止。最近一次执行观察：{}",
                    this.parseFailCount, StrUtil.truncateUtf8(this.lastObservation, 200)));
            return;
        }

        inputList.add(new UserMessage("错误：你的上一次输出未能解析为有效的AgentOutput工具调用。"
                + "你必须严格通过AgentOutput工具以JSON参数返回，current_state与action字段均不能省略，"
                + "action中只能使用已定义的动作名称。请重新输出。"));
    }

    /**
     * 执行 ReAct 动作并重建对话历史：“观察思考→逐个执行动作→将结果回写历史”。
     *
     * @param reActInput       解析后的模型输出（含状态与动作列表）
     * @param inputList        当前对话消息列表（就地修改历史）
     * @param assistantMessage 模型本次生成的助手消息
     */
    private void agentOutput(ReActInput reActInput, List<Message> inputList, AssistantMessage assistantMessage) {
        // 本轮成功解析，清零连续解析失败计数
        this.parseFailCount = 0;
        var status = reActInput.getStatus();
        // 打印 ReAct 的三个推理要素：目标评估 / 记忆 / 思考
        log.info("🏹 previous goal:{}", status.getEvaluationPreviousGoal());
        log.info("💾 memory:{}", status.getMemory());
        log.info("🧠 thinking:{}", status.getThinking());

        // 向客户端发送消息（将当前思考实时推送前端）
        this.messageSession.sendMessage(String.format("[%s]%s", this.name(), status.getThinking()));

        // 获取动作列表
        var srcActionList = reActInput.getAction();
        log.info("🛠 action:{}", reActInput.getAction());

        // 逐个执行动作，收集结果；已处理列表用于判断是否因状态突变而提前中断
        var resultBuilder = new StringBuilder();
        var processedActionList = new LinkedList<>();
        for (var action : srcActionList) {
            // 取消/客户端失活检查：命中则中止剩余动作，观察结果中告知模型
            if (this.isAborted()) {
                resultBuilder.append("NOTICE:任务已取消或客户端已断开，中止剩余动作\n");
                break;
            }
            processedActionList.add(action);
            // 每个 action 形如 {动作名: 参数}，取第一个 key 作为动作名
            String actName = CollUtil.getFirst(action.keySet());
            // 未知动作显式反馈给模型而非 NPE，交由下一轮根据观察自行纠正
            var actionFunction = this.actionRegistry.get(actName);
            if (actionFunction == null) {
                log.warn("未知动作:{}", actName);
                resultBuilder.append(action).append("->")
                        .append("Error:未知动作 ").append(actName)
                        .append("，可用动作列表:").append(this.actionRegistry.keySet()).append('\n');
                break;
            }
            try {
                // 执行动作（带超时控制），参数转为 Map 传入
                var actionResult = this.executeActionWithTimeout(actionFunction, action.get(actName));
                resultBuilder.append(action).append("->").append(actionResult).append('\n');
            } catch (Exception e) {
                log.error("error in running action:{}", action, e);
                // break loop（动作异常时记录分类后的错误并中断，交由下一轮模型重新决策）
                resultBuilder.append(action).append("->").append(this.describeActionError(e)).append('\n');
                break;
            }

            // 若当前不是最后一个动作且状态发生显著变化，提前终止，避免基于旧上下文执行多余动作
            if (srcActionList.indexOf(action) != srcActionList.size() - 1 && this.isStatusSignificantChanged()) {
                // 如果当前动作不是最后一个动作，并且状态发生了显著变化，则通过 break 提前终止循环。
                break;
            }
        }

        // 若实际执行的动作少于计划，说明上下文已重大变化，提醒模型忽略剩余动作
        if (processedActionList.size() < srcActionList.size()) {
            // 如果已经执行的动作数量小于总动作数量，则说明状态发生了显著变化，需要忽略已经执行的动作。
            // 注意：前面的操作会导致上下文发生重大变化，忽略以下操作：
            resultBuilder.append("NOTICE:previous action leads a significant change on context,ignore following action:");
            var ignore = new ArrayList<>(srcActionList);
            ignore.removeAll(processedActionList);
            ignore.forEach(act -> {
                resultBuilder.append(act);
                resultBuilder.append('\n');
            });
            // 您应该根据最新状态继续操作
            resultBuilder.append("You should continue you step based on the newest status");
        }

        // 2.得到结果（所有动作的观察汇总）
        String actionResult = resultBuilder.toString();
        // 记录最近一次观察，供解析失败强制终止时的兜底说明使用
        this.lastObservation = actionResult;
        log.info("📃 obs:{}", actionResult);

        // 3.按当前结果重建消息列表（历史部分）
        // 删除最新的聊天消息（即上一步 buildCurrentParse 加入的状态 UserMessage）
        inputList.remove(inputList.size() - 1);
        // 把模型的本次响应加入到输入列表中，形成“历史 + 助手回复”
        inputList.add(assistantMessage);
        if (assistantMessage.hasToolCalls()) {
            var toolCall = CollUtil.getFirst(assistantMessage.getToolCalls());
            // 因禁用了 SpringAI 自动执行，需手动补充工具响应消息，保持与工具调用协议一致
            var toolResponseMessage = new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), "success")));
            inputList.add(toolResponseMessage);
        }
        // 将动作观察结果以 UserMessage 形式回写，供下一轮模型参考
        inputList.add(new UserMessage(String.format("思路'%s'的行动结果如下:\n%s", status.getThinking(), actionResult)));
    }

    /**
     * 任务是否已应中止：用户请求取消，或客户端已失活（断连/连续推送失败）。
     */
    private boolean isAborted() {
        return this.messageSession.isCancelled() || !this.messageSession.isAlive();
    }

    /**
     * 中止检查：命中时通过 done 机制标记终止并生成结果说明。
     *
     * @return true 表示任务已中止，循环应立即退出
     */
    private boolean checkAborted() {
        if (!this.isAborted()) {
            return false;
        }
        var reason = this.messageSession.isCancelled() ? "任务已被取消" : "客户端已断开连接";
        log.warn("ReAct 循环中止: {}", reason);
        this.done(StrUtil.format("[系统]{}", reason));
        return true;
    }

    /**
     * 上下文预算控制：估算消息总 token，超出预算时从最早的历史轮次开始压缩，
     * 始终保留初始输入（System/任务/示例）与最近的 {@code keepRecentMessages} 条消息。
     */
    private void compressHistoryIfNeeded(LinkedList<Message> inputList) {
        if (this.estimateTokens(inputList) <= this.reActConfig.getMaxContextTokens()) {
            return;
        }
        int keep = this.reActConfig.getKeepRecentMessages();
        int removableEnd = inputList.size() - keep;
        if (removableEnd <= this.initInputSize) {
            log.warn("上下文超出token预算，但已无可压缩的历史消息");
            return;
        }
        // 从最早的历史轮次开始删除（初始输入与最近消息保留），直到回到预算内或无可删除
        while (inputList.size() - keep > this.initInputSize
                && this.estimateTokens(inputList) > this.reActConfig.getMaxContextTokens()) {
            inputList.remove(this.initInputSize);
        }
        // 在保留历史之前插入压缩说明，让模型知晓早期上下文已被移除
        inputList.add(this.initInputSize, new UserMessage(
                "[系统提示]较早的对话历史已因超出上下文预算被压缩，仅保留系统提示、初始任务与最近的执行记录；"
                        + "如需保留关键信息，请在后续输出的memory字段中记录。"));
        log.info("上下文历史已压缩，当前消息数:{}", inputList.size());
    }

    /**
     * 估算消息列表的总 token 数（文本为空的消息按 0 计）。
     */
    private long estimateTokens(List<Message> messages) {
        long total = 0;
        for (Message message : messages) {
            var text = message.getText();
            if (StrUtil.isNotBlank(text)) {
                total += this.tokenEstimator.estimate(text);
            }
        }
        return total;
    }

    /**
     * 带超时的动作执行：超时按 {@link ActionTimeoutException} 抛出，交由错误分类生成观察。
     * <p>注意：超时仅放弃等待结果，若工具内部不可中断，其线程可能仍在后台运行直至自然结束。</p>
     */
    private String executeActionWithTimeout(Function<Map<String, Object>, String> actionFunction, Object params) {
        var future = CompletableFuture.supplyAsync(() -> actionFunction.apply(BeanUtil.beanToMap(params)), ACTION_EXECUTOR);
        try {
            return future.get(this.reActConfig.getActionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new ActionTimeoutException("action exceeded " + this.reActConfig.getActionTimeoutSeconds() + "s");
        } catch (ExecutionException ee) {
            var cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("action execution interrupted", ie);
        }
    }

    /**
     * 动作错误分类：将异常归类为模型可理解的观察文本（含类型提示与截断后的错误信息）。
     */
    private String describeActionError(Exception e) {
        String type;
        if (e instanceof ActionTimeoutException) {
            type = "动作执行超时(超过" + this.reActConfig.getActionTimeoutSeconds() + "秒)，建议拆分动作参数、减小等待时长或改用其他方式";
        } else if (e instanceof NullPointerException
                || e instanceof IllegalArgumentException
                || e instanceof ClassCastException) {
            type = "动作参数错误，请检查参数名称与取值格式";
        } else {
            type = "动作执行异常，可稍后重试或改用其他方式";
        }
        return StrUtil.format("Error:{} - {}:{}", type, e.getClass().getSimpleName(),
                StrUtil.truncateUtf8(StrUtil.nullToEmpty(e.getMessage()), 200));
    }

    /**
     * 动作执行超时异常，用于错误分类
     */
    private static class ActionTimeoutException extends RuntimeException {
        public ActionTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * 判断执行动作后上下文是否发生显著变化（变化时会中断剩余动作，重新获取模型决策）。
     * <p>子类可覆盖；默认 false，表示同一轮的所有 action 都执行。</p>
     *
     * @return true 表示状态发生重大变化
     */
    protected boolean isStatusSignificantChanged() {
        // 默认返回false，表示所有的action都要执行
        return false;
    }

    /**
     * 构造仅包含 {@code AgentOutput} 声明的工具回调列表。
     * <p>它只用于向大模型“声明”工具，真正的执行由程序手动控制，因此 {@code call} 直接抛出不支持异常。</p>
     *
     * @return 工具回调列表
     */
    private List<ToolCallback> getToolCallbacks() {
        return List.of(new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("toolCalling should controlled by native program.");
            }
        });
    }

    /**
     * 将当前智能体状态（{@link #getCurrentStatus()}）作为一条 UserMessage 追加到消息列表末尾。
     *
     * @param inputList 当前对话消息列表
     */
    private void buildCurrentParse(List<Message> inputList) {
        inputList.add(new UserMessage(this.getCurrentStatus()));
    }

    /**
     * 获取任务当前的状态数据，使用L2M规则每次放入上下文的最末端
     */
    protected abstract String getCurrentStatus();

    /**
     * 1.增加SystemMessage
     * 2.增加主任务对应的UserMessage
     * 3.增加One-Shot Example（可选）
     */
    protected abstract void addInitInput(LinkedList<Message> inputList, String task);

    /**
     * 初始化action方法绑定，应与构造参数中传入的schema匹配
     */
    protected void initActionMap() {
        // 注册内置的 done 动作：模型输出 done 即终止循环并返回最终结果
        actionRegistry.put("done", paramMap -> {
            var text = MapUtil.getStr(paramMap, "text");
            this.done(text);
            return text;
        });

        // 将子类中的工具合并到 toolDefinition 中
        this.toolCallbackProvider().forEach(this::mergeByToolCallbackProvider);
    }

    /**
     * 将已有Tool的定义并入ReAct的Schema中，例如MCP服务提供的能力
     */
    private void mergeByToolCallbackProvider(ToolCallbackProvider provider) {
        // 解析原有的工具定义
        var jsonObject = JSONUtil.parseObj(this.toolSchema);
        var expression = "inputSchema.properties.action.items.properties";

        // 工具的具体action的定义
        var actionEntry = jsonObject.getByPath(expression, JSONObject.class);
        for (var toolCallback : provider.getToolCallbacks()) {
            var def = toolCallback.getToolDefinition();
            actionEntry.set(def.name(), JSONUtil.parseObj(def.inputSchema()));
            actionRegistry.put(def.name(), map -> toolCallback.call(JSONUtil.toJsonStr(map)));
        }

        //回写到jsonObject中
        jsonObject.putByPath(expression, actionEntry);
        this.toolSchema = jsonObject.toString();
        this.refreshToolDefinition();
    }

    /**
     * 子类提供需要合并进 ReAct Schema 的工具回调提供者，默认为空。
     *
     * @return 工具回调提供者列表
     */
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of();
    }

    /**
     * 标记任务完成：停止循环并保存最终结果。
     *
     * @param text done 动作携带的最终文本
     */
    private void done(String text) {
        this.agentStop = true; // 标记当前的任务已经完成
        this.finalResult = text;
    }

    /**
     * 允许外部（如规划智能体）在运行时调整当前智能体的 ReAct 配置（例如最大步数）。
     *
     * @param cons 对 {@link ReActConfig} 的修改回调
     */
    public void resetReActConfig(Consumer<ReActConfig> cons) {
        cons.accept(reActConfig);
    }
}
