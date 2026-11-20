package cn.itcast.manus.agent.chart;

import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.BaseAgent;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.dto.DialogMessageDTO;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.service.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * 画图（统计图）专项智能体。
 * <p>基于上下文已有数据调用大模型生成图表 HTML 页面，保存后回推预览/下载链接给客户端。</p>
 */
@Slf4j
public class ChartAgent extends BaseAgent {

    @Resource
    private PromptManagement promptManagement;
    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    private final MessageSession messageSession;
    @Resource
    private FileStorageService fileStorageService;

    public ChartAgent(MessageSession messageSession) {
        this.messageSession = messageSession;
    }

    @Override
    protected String solve(String task) {
        //1. LLM生成图表 html页面
        var params = Map.of(Constant.TASK, task);
        var prompt = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.CHART), params);
        var html = this.chatModel().call(prompt);

        //2. 存储文件，生成下载地址和预览地址
        var uuid = this.fileStorageService.saveFile(StrUtil.utf8Bytes(html));
        var openUrl = this.fileStorageService.generateOpenUrl(uuid);
        var downloadUrl = this.fileStorageService.generateDownloadUrl("chat.html", uuid);

        //3. 通过messageSession发送数据给客户端
        this.messageSession.sendMessage(DialogMessageDTO.builder()
                .text("[ChartAgent]图表文件生成成功.")
                .openUrl(openUrl)
                .fileUrl(downloadUrl)
                .build());

        //4. 返回图表相关的内容数据（作用：反馈给 ReActPlanningAgent 进行后续的操作）
        return StrUtil.format("""
                [ChartAgent]
                生成可打开的url:{}
                生成的可下载url:{}
                """, openUrl, downloadUrl);
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}
