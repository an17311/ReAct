package cn.itcast.manus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.prompt.PromptManagement;
import cn.itcast.manus.config.ModelConfig;
import cn.itcast.manus.constants.Constant;
import cn.itcast.manus.service.PageContentExtractService;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link PageContentExtractService} 实现：在 token 限制内选择合适的内容形式，再调用大模型按目标抽取内容。
 */
@Slf4j
@Service
public class PageContentExtractServiceImpl implements PageContentExtractService {

    @Resource(name = ModelConfig.MAIN_AGENT)
    private ChatModel chatModel;
    @Resource
    private PromptManagement promptManagement;

    /**
     * 设置最大的token数量，默认为：64k
     */
    @Value("${extraction.max-token:64000}")
    private int maxToken;

    /**
     * 提取页面内容：按优先级选择未超 token 限制的内容形式（原始HTML &gt; Markdown &gt; 纯文本 &gt; 可见状态），再交给大模型抽取。
     *
     * @param originalContent 原始页面内容
     * @param pageInStatus    当前页面可见状态
     * @param goal            提取目标
     * @return 提取结果文本
     */
    @Override
    public String extractContent(String originalContent, String pageInStatus, String goal) {
        var result = new StringBuilder();
        result.append("下面的数据包括文本提取结果，你需要结合两部分数据进行综合考量\n");
        result.append("文本提取结果如下:\n");

        // token数量评估
        var tokenCountEstimator = new JTokkitTokenCountEstimator();
        var doc = Jsoup.parse(originalContent);
        var markDown = FlexmarkHtmlConverter.builder().build().convert(originalContent);
        // 优先级顺序
        // 1.原始html（包含所有内容，冗余信息较多，容易超token限制）
        // 2.markdown保留连接、图片地址等信息、
        // 3.text纯文本内容
        // 4.可见的基础标签及文本（同pageStatus)
        var op = Stream.of(doc.body().html(), markDown, doc.body().text(), pageInStatus)
                .filter(s -> tokenCountEstimator.estimate(s) < maxToken)
                .findFirst();
        if (op.isPresent()) {
            var targetContent = op.get();
            var textBased = this.textExtraction(goal, targetContent);
            result.append(textBased);
        } else {
            result.append("文本Token数量超过最大值，放弃文本分析\n");
        }

        return result.toString();
    }

    /**
     * 文本抽取：拼接提示词并调用大模型，从目标内容中提取所需信息。
     *
     * @param goal          提取目标
     * @param targetContent 待分析的目标内容
     * @return 大模型抽取的结果
     */
    public String textExtraction(String goal, String targetContent) {
        var params = Map.of(
                "page", targetContent,
                "goal", Optional.ofNullable(goal).orElse(StrUtil.EMPTY));
        String message = StrUtil.format(this.promptManagement.getPrompt(Constant.Prompts.EXTRA_PAGE_CONTENT), params);
        return this.chatModel.call(message);
    }
}
