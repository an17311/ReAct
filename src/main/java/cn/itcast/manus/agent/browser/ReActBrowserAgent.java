package cn.itcast.manus.agent.browser;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.ReActBaseAgent;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.playwright.PlaywrightComponentFactory;
import cn.itcast.manus.playwright.PlaywrightManagement;
import com.microsoft.playwright.BrowserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 浏览器专项智能体。
 * <p>基于 ReAct 范式，使用 Playwright 驱动真实浏览器完成导航、点击、输入等通用网页操作；
 * 动作能力由 {@link PageSession} 提供的 @Tool 方法构成，页面状态通过 {@link #getCurrentStatus()} 反馈模型。</p>
 */
@Slf4j
public class ReActBrowserAgent extends ReActBaseAgent {

    @Resource
    private PromptManagement promptManagement;
    // Playwright 生命周期管理（提供浏览器上下文）
    @Resource
    private PlaywrightManagement playwrightManagement;
    // PageSession 组件工厂（prototype）
    @Resource
    private PlaywrightComponentFactory playwrightComponentFactory;
    // 当前浏览器页面会话，承载页面导航/元素分析与工具动作
    private PageSession pageSession;

    public ReActBrowserAgent(MessageSession messageSession) {
        super(messageSession);
    }

    /**
     * 重写执行入口：在独立的浏览器上下文中运行任务，任务结束后自动释放浏览器资源。
     *
     * @param task 任务描述
     * @return 执行结果
     */
    @Override
    public String reActSolve(String task) {
        return this.playwrightManagement
                .browserContextOperation(browserContext -> this.runWithContext(task, browserContext));
    }

    /**
     * 在给定的浏览器上下文内创建页面会话并进入 ReAct 主循环，无论成败都清理页面。
     *
     * @param task           任务描述
     * @param ctx            浏览器上下文
     * @return 执行结果或异常信息
     */
    private String runWithContext(String task, BrowserContext ctx) {
        try {
            // 基于上下文创建页面会话（后续工具动作均在其上执行）
            this.pageSession = this.playwrightComponentFactory.pageSession(ctx);
            var result = super.reActSolve(task);
            // 关闭本次操作新开的页面，释放资源
            this.pageSession.cleanUp();
            return result;
        } catch (Exception e) {
            log.error("ReActBrowserAgent runWithContext error", e);
            return ExceptionUtil.getMessage(e);
        }
    }

    /**
     * 提供浏览器工具回调：将 {@link PageSession} 上的 @Tool 方法（导航/点击/输入等）合并进 ReAct Schema。
     *
     * @return 工具回调提供者列表
     */
    @Override
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of(() -> ToolCallbacks.from(this.pageSession));
    }

    /**
     * 获取当前页面状态（URL、可见元素、滚动信息等），作为下一轮模型推理的上下文。
     *
     * @return 页面状态文本，异常时返回错误描述
     */
    @Override
    protected String getCurrentStatus() {
        try {
            // 清空页面标志
            this.pageSession.clearFlag();
            // 获取当前页面状态
            return this.pageSession.getCurrentPageStatus(currentStep, super.reActConfig.getMaxStep());
        } catch (Exception e) {
            return "Exception in getCurrent page status:" + ExceptionUtil.getMessage(e);
        }
    }

    /**
     * 构造初始输入：浏览器系统提示 + 用户任务 + 一个 One-Shot 示例（演示工具调用与观察回写流程）。
     *
     * @param inputList 消息列表
     * @param task      任务描述
     */
    @Override
    protected void addInitInput(LinkedList<Message> inputList, String task) {
        // 添加系统消息
        inputList.add(SystemMessage.builder()
                .text(this.getPrompt(Constant.Prompts.BROWSER_SYSTEM, Map.of(Constant.MAX_ACTIONS, super.reActConfig.getMaxActionPerCall())))
                .build());

        // 添加用户消息
        inputList.add(UserMessage.builder()
                .text(this.getPrompt(Constant.Prompts.BROWSER_USER_TASK, Map.of(Constant.TASK, task)))
                .build());

        // 添加一个用户示例
        inputList.add(new UserMessage("下面是一个例子:"));

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "1",
                "function",
                AGENT_OUTPUT_METHOD,
                "{'action': [{'clickElement': {'index': 0}}], 'current_state': {'evaluation_previous_goal': 'Success - 基于页面信息，我判断成功打开了某个页面', 'memory': '现在1/10的任务已完成', 'thinking': '接下来需要点击索引为0的元素'}}");
        inputList.add(new AssistantMessage("", Map.of(), List.of(toolCall)));
        inputList.add(new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("1", AGENT_OUTPUT_METHOD, "SUCCESS"))));
        inputList.add(new UserMessage("[以下是任务历史记录]"));
    }

    private String getPrompt(String name, Map<String, Object> params) {
        String prompt = this.promptManagement.getPrompt(name);
        return StrUtil.format(prompt, params);
    }

    /**
     * 浏览器场景中，一次动作可能导致页面导航/新页开启/标签切换，需先观察再决策，故状态变化时中断剩余动作。
     *
     * @return 页面状态是否发生显著变化
     */
    @Override
    protected boolean isStatusSignificantChanged() {
        return this.pageSession.isStatusSignificantChanged();
    }
}
