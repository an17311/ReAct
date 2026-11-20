package cn.itcast.manus.agent.amap;

import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.BaseAgent;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.dto.DialogMessageDTO;
import cn.itcast.manus.mcp.SseShortConnectionSupport;
import cn.itcast.manus.message.MessageSession;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;
import java.util.Optional;

/**
 * 地图（高德）专项智能体。
 * <p>调用大模型时通过 SSE 短连接接入高德 MCP 工具集，完成路线规划、地址转坐标等地理信息操作。</p>
 */
@Slf4j
public class AMAPAgent extends BaseAgent {

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    private PromptManagement promptManagement;
    @Resource
    private SseShortConnectionSupport sseShortConnectionSupport;
    private MessageSession messageSession;

    public AMAPAgent(MessageSession messageSession) {
        this.messageSession = messageSession;
    }

    @Override
    protected String solve(String task) {
        //1. 构造提示词
        var params = Map.of(Constant.TASK, task);
        var prompt = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.AMAP_SYSTEM), params);

        //2. 调用大模型时，通过sse短连接的方式调用高德的工具
        ChatResponse chatResponse = this.sseShortConnectionSupport.requestAmapSse(provider -> {
            // 通过参数的方式设置高德MCP工具列表
            var options = OpenAiChatOptions.builder()
                    .toolCallbacks(provider.getToolCallbacks())
                    .build();
            var req = new Prompt(prompt, options);
            return this.chatModel().call(req);
        });

        //3. 给客户端输出成功的提示
        this.messageSession.sendMessage(DialogMessageDTO.builder()
                .text("[AMAPAgent]接口调用成功")
                .build());

        //4. 返回大模型调用的结果
        return Optional.of(chatResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .orElseThrow();
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}
