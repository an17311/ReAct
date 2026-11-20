package cn.itcast.manus.agent;

import cn.itcast.manus.message.MessageSession;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedList;
import java.util.List;

/**
 * 测试用的最小 ReAct 智能体实现。
 * <p>仅提供一个简单的天气查询工具，用于验证 ReAct 引擎的基本流程。</p>
 */
public class MyReActAgent extends ReActBaseAgent{

    public MyReActAgent(MessageSession messageSession) {
        super(messageSession);
    }

    /**
     * 测试用途：不提供额外的状态上下文。
     *
     * @return 空字符串
     */
    @Override
    protected String getCurrentStatus() {
        return "";
    }

    /**
     * 直接将任务作为用户消息加入输入列表。
     *
     * @param inputList 消息列表
     * @param task      任务描述
     */
    @Override
    protected void addInitInput(LinkedList<Message> inputList, String task) {
        inputList.add(new UserMessage(task));
    }

    /**
     * 将本类自身的 @Tool 方法（如 {@link #getWeather}）注册为可用工具。
     *
     * @return 工具回调提供者列表
     */
    @Override
    protected List<ToolCallbackProvider> toolCallbackProvider() {
        return List.of(() -> ToolCallbacks.from(this));
        // return List.of(new ToolCallbackProvider() {
        //     @Override
        //     public ToolCallback[] getToolCallbacks() {
        //         return ToolCallbacks.from(MyReActBaseAgent.this);
        //     }
        // });
    }

    /**
     * 测试用天气查询工具（返回固定假数据）。
     *
     * @param city 城市名称
     * @return 模拟的天气信息
     */
    @Tool(description = "查询城市天气数据")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        return "今天天气不错，温度30度。";
    }
}
