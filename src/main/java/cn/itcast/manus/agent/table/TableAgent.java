package cn.itcast.manus.agent.table;

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
 * 制表专项智能体。
 * <p>基于上下文已有数据调用大模型生成 HTML 表格页面，保存后回推预览/下载链接给客户端。</p>
 * <p>与 ReAct 智能体不同，它是一次性生成，不走“思考→行动→观察”循环。</p>
 */
@Slf4j
public class TableAgent extends BaseAgent {

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    private PromptManagement promptManagement;
    @Resource
    private FileStorageService fileStorageService;
    private MessageSession messageSession;

    public TableAgent(MessageSession messageSession) {
        this.messageSession = messageSession;
    }

    @Override
    protected String solve(String task) {
        //1. 调用AI大模型生成html页面
        String prompt = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.TABLE), Map.of(Constant.TASK, task));
        String html = this.chatModel().call(prompt);

        //2. 保存html页面到文件系统
        String uuid = this.fileStorageService.saveFile(StrUtil.utf8Bytes(html));
        var openUrl = this.fileStorageService.generateOpenUrl(uuid);
        var downloadUrl = this.fileStorageService.generateDownloadUrl("table.html", uuid);

        log.info("生成表格, task = {}, openUrl = {}", task, openUrl);

        //3. 发送消息给客户端
        var msg = DialogMessageDTO.builder()
                .text("[TableAgent]文件生成")
                .fileUrl(downloadUrl)
                .openUrl(openUrl)
                .build();
        this.messageSession.sendMessage(msg);

        //4. 返回表格相关的内容数据（作用：反馈给 ReActPlanningAgent 进行后续的操作）
        return StrUtil.format("""
                [TableAgent]
                生成可打开的url:{}
                生成的可下载url:{}
                """, openUrl, downloadUrl);
    }

    @Override
    public ChatModel chatModel() {
        return this.chatModel;
    }
}
