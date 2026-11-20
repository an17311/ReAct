package cn.itcast.manus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息载体，同时用于前端上行与服务器下行。
 * <p>除了文本内容，还可携带图片、文件下载、在线预览等链接地址，前端根据字段是否存在渲染不同展示。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogMessageDTO {

    // 服务器下行消息的类型标识
    public static final String TYPE_SERVER = "server";

    // 消息类型（如 server / user）
    private String type = TYPE_SERVER;
    // 消息正文内容
    private String text;
    // 图片地址（用于展示图片类结果）
    private String imageUrl;
    // 文件下载地址（用于下载生成的文件）
    private String fileUrl;
    // 在线预览地址（在新窗口打开生成的内容）
    private String openUrl;

}
