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

import java.util.*;
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

        //3. 循环执行，结束条件：任务终止（done动作） 或 任务的执行步骤已经超过最大的步骤
        var tokenCount = new AtomicLong(0L);
        // 初始化当前步骤
        this.currentStep = 1;
        while (this.currentStep <= this.reActConfig.getMaxStep() && !this.agentStop) {
            // 将当前的Agent的状态加入到消息列表中（每步提供最新上下文）
            this.buildCurrentParse(inputList);
            // 调用大模型，并在其内部依次触发响应解析与动作执行
            this.callingLLM(inputList, tokenCount);
            log.info("step:{},tokenCount:{}", this.currentStep, tokenCount.get());
            log.info("[{}👆]------------------", getClass().getSimpleName());
            this.currentStep++;
        }

        //4. 检查步骤是否已经超过了最大步数，如果已经超过，就调用大模型来触发执行done动作
        if (this.currentStep >= this.reActConfig.getMaxStep() && !this.agentStop) {
            // 提示模型立即用 done 总结当前成果，避免因超步数而无结果
            String text = """
                    注意，你的执行已达到最大步数限制，请立即使用done操作返回截至到目前的工作成果，
                    需要包含具体的内容，不允许任何省略，最后总结尚未完成的任务并对遇到的问题加以说明
                    """;
            inputList.add(new UserMessage(text));
            this.callingLLM(inputList, tokenCount);
        }

        //5. 返回最终的输出结果
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

        //3. 调用AI大模型
        ChatResponse chatResponse;
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
            log.error("调用AI大模型时出错！", e);
            throw new RuntimeException(e);
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
            if (StrUtil.equalsIgnoreCase(tool.name(), AGENT_OUTPUT_METHOD)) {
                // 将工具参数 JSON 反序列化为 ReActInput，进入动作执行
                ReActInput reActInput = JSONUtil.toBean(tool.arguments(), ReActInput.class);
                this.agentOutput(reActInput, inputList, assistantMessage);
            } else {
                log.error("未知工具名称:{}", tool.name());
            }
            return;
        }

        // 情况 B：未触发工具调用，则从纯文本中查找第一个合法 JSON 作为兜底解析
        JsonFinder.findFirstJson(text)
                .map(json -> JSONUtil.toBean(json, ReActInput.class))
                .ifPresent(reActInput -> this.agentOutput(reActInput, inputList, assistantMessage));
    }

    /**
     * 执行 ReAct 动作并重建对话历史：“观察思考→逐个执行动作→将结果回写历史”。
     *
     * @param reActInput       解析后的模型输出（含状态与动作列表）
     * @param inputList        当前对话消息列表（就地修改历史）
     * @param assistantMessage 模型本次生成的助手消息
     */
    private void agentOutput(ReActInput reActInput, List<Message> inputList, AssistantMessage assistantMessage) {
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
            processedActionList.add(action);
            // 每个 action 形如 {动作名: 参数}，取第一个 key 作为动作名
            String actName = CollUtil.getFirst(action.keySet());
            try {
                // 从注册表取得对应动作函数并执行，参数转为 Map 传入
                var actionResult = this.actionRegistry.get(actName).apply(BeanUtil.beanToMap(action.get(actName)));
                resultBuilder.append(action).append("->").append(actionResult).append('\n');
            } catch (Exception e) {
                log.error("error in running action:{}", action, e);
                // break loop（动作异常时记录错误并中断，交由下一轮模型重新决策）
                resultBuilder.append(action).append("->").append("Exception:").append(StrUtil.truncateUtf8(e.getMessage(), 32)).append('\n');
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
