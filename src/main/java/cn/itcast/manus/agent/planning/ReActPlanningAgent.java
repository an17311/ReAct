package cn.itcast.manus.agent.planning;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.Agent;
import cn.itcast.manus.agent.AgentFactory;
import cn.itcast.manus.agent.ReActBaseAgent;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.enums.AgentTypeEnum;
import cn.itcast.manus.message.MessageSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 任务规划智能体，多智能体协作的调度中枢。
 * <p>基于 ReAct 范式将复杂目标拆解为子任务链，每个子任务指派给合适的专项智能体（浏览器/制表/画图/网页/地图）执行；
 * 通过在 {@link #getCurrentStatus()} 中调度子智能体并将结果回喂模型，逐步推进任务直到完成。</p>
 */
@Slf4j
public class ReActPlanningAgent extends ReActBaseAgent {

    // 规划智能体使用专用的计划模型（区别于主模型）
    @Resource(name = ModelConfig.PLAN_AGENT)
    private ChatModel chatModel;

    @Resource
    private PromptManagement promptManagement;
    // 存放agent，key：agentName，value：功能描述
    private final Map<String, String> agentInfo = new HashMap<>();
    // 线程安全的动态数组，用来存储子任务链
    private final CopyOnWriteArrayList<SubTaskNode> subTaskChain = new CopyOnWriteArrayList<SubTaskNode>();
    // 目标任务
    private String targetTask;
    // 子任务中间结果
    private final Map<String, String> subTaskMidResult = new ConcurrentHashMap<>();

    public ReActPlanningAgent(MessageSession messageSession) {
        super(messageSession);
    }

    @PostConstruct
    void init() {
        // 将需要的智能体名称和描述添加到智能体信息中
        agentInfo.put(AgentTypeEnum.BROWSER_AGENT.getAgentName(), AgentTypeEnum.BROWSER_AGENT.getDesc());
        agentInfo.put(AgentTypeEnum.TABLE_AGENT.getAgentName(), AgentTypeEnum.TABLE_AGENT.getDesc());
        agentInfo.put(AgentTypeEnum.CHART_AGENT.getAgentName(), AgentTypeEnum.CHART_AGENT.getDesc());
        agentInfo.put(AgentTypeEnum.HTML_DOC_AGENT.getAgentName(), AgentTypeEnum.HTML_DOC_AGENT.getDesc());
        agentInfo.put(AgentTypeEnum.AMAP_AGENT.getAgentName(), AgentTypeEnum.AMAP_AGENT.getDesc());
    }

    /**
     * 重写 ReAct 入口：记录目标任务并清空上一次的子任务链与中间结果，再进入父类主循环。
     *
     * @param task 目标任务
     * @return 任务最终结果
     */
    @Override
    public String reActSolve(String task) {
        this.targetTask = task;
        this.subTaskChain.clear();
        this.subTaskMidResult.clear();
        return super.reActSolve(task);
    }

    /**
     * 构造当前状态提示：先调度子任务链最后一个任务对应的专项智能体执行，再将子任务链拼接为状态文本供模型决策下一步。
     *
     * @return 填充到状态提示词模板中的当前状态文本
     */
    @Override
    protected String getCurrentStatus() {
        // 获取任务链中的最后一个任务，查找到对应的Agent进行执行
        this.subTaskChain.stream()
                .reduce((x, y) -> y)
                .ifPresent(last -> {
                    // 获取当前任务
                    String originalTask = last.getSubTask();
                    // 将原任务与目标任务合并
                    String subTaskWithCtx = this.taskMerging(targetTask, originalTask, subTaskMidResult);

                    // 查找对应的智能体，修改最大步数为当前任务的最大步数
                    Function<MessageSession, Agent> agentFunction = AgentFactory.getAgent(AgentTypeEnum.agentNameOf(last.getAgent()));
                    if (ObjectUtil.isEmpty(agentFunction)) {
                        return;
                    }
                    var agent = agentFunction.apply(super.messageSession);
                    if (agent instanceof ReActBaseAgent base) {
                        // 修改任务的最大步数为最后一个任务的最大步数
                        base.resetReActConfig(c -> c.setMaxStep(last.getMaxStep()));
                    }

                    // 调用智能体，获取结果
                    String result = agent.solveTask(subTaskWithCtx);
                    if (agent instanceof ReActBaseAgent base) {
                        // 设置结果的步数，就是当前的任务的步数
                        last.setResultStep(base.getCurrentStep());
                    }
                    // 将结果保存到中间结果中
                    subTaskMidResult.put(originalTask, result);
                    last.setResult(result);
                });

        // 将子任务链中的结果以字符串拼接，拼接格式为：子任务1->子任务2->子任务3
        var chainStr = subTaskChain.stream()
                .map(SubTaskNode::strInStatus)
                .reduce((x, y) -> x + "->" + y)
                .orElse("[Empty]");

        // 将子任务链中最后一个任务的结果拼接
        var latestStr = subTaskChain.stream()
                .reduce((x, y) -> y)
                .map(SubTaskNode::getResult)
                .orElse("[Empty]");

        // 将结果拼接到模板中，作为下次调用大模型的历史对话
        var params = Map.of(
                "stepData", StrUtil.format("{}/{}", super.currentStep, super.reActConfig.getMaxStep()),
                "dateTime", DateUtil.now(),
                "subTaskChain", chainStr,
                "latestResult", latestStr);
        return StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.PLANNING_STATUS), params);
    }

    /**
     * 将目标任务、上下文中间结果与当前子任务合并，生成交给子智能体的完整任务描述。
     *
     * @param targetTask 总目标
     * @param subTask    当前子任务
     * @param context    已有的中间结果上下文
     * @return 合并后的任务描述
     */
    private String taskMerging(String targetTask, String subTask, Object context) {
        String prompt = this.promptManagement.getPrompt(Constant.Prompts.PLANNING_TASK_MERGING);
        var params = Map.of(
                "targetTask", targetTask,
                "subTask", subTask,
                "context", Convert.toStr(context));
        return StrUtil.format(prompt, params);
    }

    /**
     * 规划智能体每轮只执行一个 action，后面的 action 不再执行（需先观察子智能体结果）。
     *
     * @return 始终返回 true
     */
    @Override
    protected boolean isStatusSignificantChanged() {
        // 计划Agent每次的action只需要执行一个，后面的action不再执行
        return true;
    }

    /**
     * 供大模型调用的工具：规划下一个子任务节点并加入子任务链。
     *
     * @param agent    负责执行的智能体名称
     * @param subTask  子任务内容
     * @param maxStep  完成该子任务的最大步数
     * @return 规划结果描述（含上一个子任务的执行结果）
     */
    @Tool(description = "规划下一个子任务节点")
    public String generateNext(@ToolParam(description = "agent名称") String agent,
                               @ToolParam(description = "子任务内容") String subTask,
                               @ToolParam(description = "完成任务的最大步数") int maxStep) {
        var res = subTaskChain.stream()
                .reduce((x, y) -> y) // 获取最后一个元素
                .map(p -> { // 对最后一个任务进行描述，生成了新的子任务
                    var result = "子任务{}的执行结果:\n{}\n基于此结果成功规划下一个子任务节点:{}";
                    return StrUtil.format(result, p.getSubTask(), p.getResult(), subTask);
                })
                // 没有子任务，就生成新的任务
                .orElseGet(() -> StrUtil.format("成功规划下一个子任务节点:{}", subTask));

        // 生成新的子任务节点
        SubTaskNode subTaskNode = SubTaskNode.builder()
                .agent(agent)
                .subTask(subTask)
                .maxStep(maxStep)
                .build();
        // 将子任务加入到任务链中
        this.subTaskChain.add(subTaskNode);
        return res;
    }

    @Override
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of(() -> ToolCallbacks.from(this));
    }

    /**
     * 构造初始输入：系统提示 + 用户任务（附可用智能体信息）+ 历史记忆起始标记。
     *
     * @param inputList 消息列表
     * @param task      目标任务
     */
    @Override
    protected void addInitInput(LinkedList<Message> inputList, String task) {
        //1. 添加系统prompt
        inputList.add(new SystemMessage(this.promptManagement.getPrompt(Constant.Prompts.PLANNING_SYSTEM)));

        //2. 添加用户消息
        inputList.add(new UserMessage(this.getPromptPlanningUserTask(task)));

        //3. 添加说明消息
        inputList.add(new UserMessage("[你的历史记忆从此处开始]"));
    }

    private String getPromptPlanningUserTask(String task) {
        String prompt = this.promptManagement.getPrompt(Constant.Prompts.PLANNING_USER_TASK);
        var param = Map.of(
                "task", task,
                "agentData", String.valueOf(agentInfo)
        );
        return StrUtil.format(prompt, param);
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }

    /**
     * 子任务节点，保存单个子任务的执行信息（负责智能体、内容、最大步数、结果等）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTaskNode {
        private String agent;
        private String subTask;
        private int maxStep;
        private String result;
        private int resultStep = -1;

        public String strInStatus() {
            return StrUtil.format("[{},Task:{},Result:{},resultStep:{}]",
                    agent,
                    subTask,
                    StrUtil.truncateUtf8(result, 20),
                    resultStep);
        }
    }
}
