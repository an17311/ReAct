package cn.itcast.manus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息载体，同时用于前端上行与服务器下行。
 * <p>除了文本内容，还可携带图片、文件下载、在线预览等链接地址，前端根据字段是否存在渲染不同展示。</p>
 * <p>下行消息通过 {@code eventType} 携带结构化事件类型，前端按事件类型渲染不同 UI（气泡/计划面板/产物卡片/系统提示条）；
 * 未设置时前端回退按旧文本协议（[系统]/[Agent名] 前缀）解析，保证兼容。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogMessageDTO {

    // 服务器下行消息的类型标识
    public static final String TYPE_SERVER = "server";

    // ===== 下行结构化事件类型（eventType 取值）=====
    // 任务开始（配合 meta.serverStatusHint=1）
    public static final String EVENT_LIFECYCLE_START = "lifecycle_start";
    // 任务流程结束（配合 meta.serverStatusHint=0）
    public static final String EVENT_LIFECYCLE_END = "lifecycle_end";
    // Agent 思考进度（agentName + text）
    public static final String EVENT_PROGRESS = "progress";
    // 任务最终结果（markdown 文本）
    public static final String EVENT_RESULT = "result";
    // 生成产物（openUrl/fileUrl/imageUrl + artifactType）
    public static final String EVENT_ARTIFACT = "artifact";
    // 系统提示（排队、取消确认等）
    public static final String EVENT_SYSTEM_INFO = "system_info";
    // 系统错误（任务执行异常等）
    public static final String EVENT_SYSTEM_ERROR = "system_error";
    // 任务已被取消
    public static final String EVENT_CANCELLED = "cancelled";
    // 规划子任务链更新（plan 字段）
    public static final String EVENT_PLAN_UPDATE = "plan_update";

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
    // 服务端状态提示（用于控制前端输入框可用性，前端 Dialog.vue 依据 meta.serverStatusHint 切换输入状态）
    private Meta meta;
    // 结构化事件类型（见上方 EVENT_* 常量；前端优先按此渲染，缺失时回退文本前缀解析）
    private String eventType;
    // 产生该消息的 Agent 名称（progress 事件使用）
    private String agentName;
    // 产物类型：chart / table / doc / file（artifact 事件使用）
    private String artifactType;
    // 规划子任务链快照（plan_update 事件使用）
    private List<PlanStep> plan;

    /**
     * 规划子任务链节点，供前端渲染计划面板。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanStep {

        // 承接该子任务的 Agent 名称
        private String agent;
        // 子任务描述
        private String subTask;
        // 状态：PENDING / RUNNING / SUCCESS / FAILED
        private String status;

    }

    /**
     * 服务端状态提示载体。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {

        // 0：恢复输入；1：禁用输入
        private Integer serverStatusHint;

    }

}
