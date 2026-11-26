package cn.itcast.manus.message;

import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.dto.DialogMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@link MessageSession} 基于 STOMP WebSocket 的实现。
 * <p>使用线程安全的阻塞队列缓冲上行消息，并通过 {@link SimpMessagingTemplate} 将下行消息定向发送到当前用户的队列。</p>
 * <p>同时承载任务运行状态（单飞）、取消请求与客户端存活检测，供引擎在循环中安全终止任务。</p>
 */
@Slf4j
public class WSSession implements MessageSession {

    // 连续推送失败次数达到该阈值时，判定客户端失活
    private static final int MAX_CONSECUTIVE_SEND_FAILURES = 3;

    // 文本协议中的 Agent 名前缀，如 [ReActBrowserAgent]xxx（[系统] 前缀在 classify 中单独处理）
    private static final Pattern AGENT_NAME_PREFIX = Pattern.compile("^\\[([A-Za-z][A-Za-z0-9_]*)\\]");

    // 消息缓存区，使用线程安全队列
    private final BlockingQueue<DialogMessageDTO> buffer = new ArrayBlockingQueue<>(1024);
    // 会话ID提供者
    private final Supplier<String> sessionIdProvider;

    private final SimpMessagingTemplate messagingTemplate;

    // 发送消息的目标
    private final String destination;

    // 任务单飞标记：同一会话同一时间仅允许一个任务执行
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 任务取消标记（用户主动取消或断连自动取消）
    private volatile boolean cancelled = false;
    // 客户端是否已断开连接
    private volatile boolean disconnected = false;
    // 连续推送失败次数（推送成功时清零）
    private final AtomicInteger consecutiveSendFailures = new AtomicInteger(0);

    public WSSession(Supplier<String> sessionIdProvider, String destination, SimpMessagingTemplate messagingTemplate) {
        this.sessionIdProvider = sessionIdProvider;
        this.destination = destination;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 接收前端上行消息：先存入阻塞队列，供后续 {@link #readMessage()} 取出。
     *
     * @param messageDTO 消息对象
     */
    @Override
    public void receiveMessages(DialogMessageDTO messageDTO) {
        try {
            // 接收到数据，先存放到队列中（put 在队列满时会阻塞）
            buffer.put(messageDTO);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * 从队列头部读取一条消息的文本内容（队列为空时会阻塞等待）。
     *
     * @return 消息文本
     */
    @Override
    public String readMessage() {
        try {
            // 读取队列中的消息（take 会移除队首元素，空队列时阻塞）
            return buffer.take().getText();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * 以纯文本形式向客户端发送消息（内部包装为 {@link DialogMessageDTO}）。
     *
     * @param msg 消息文本
     */
    @Override
    public void sendMessage(String msg) {
        this.sendMessage(DialogMessageDTO.builder()
                .text(msg)
                .build());
    }

    /**
     * 向客户端发送消息，统一标记为服务器类型后下行。
     * <p>下行前若调用方未显式设置 eventType，则按旧文本协议（[系统]/[Agent名] 前缀、产物链接）自动归类，
     * 使所有 Agent 的既有调用无需修改即可携带结构化事件类型。</p>
     *
     * @param messageDTO 消息对象
     */
    @Override
    public void sendMessage(DialogMessageDTO messageDTO) {
        messageDTO.setType(DialogMessageDTO.TYPE_SERVER);
        this.classify(messageDTO);
        this.sendToClient(messageDTO);
    }

    /**
     * 文本协议分类器：依据消息内容推断结构化事件类型（显式设置过 eventType 的消息不重复解析）。
     */
    private void classify(DialogMessageDTO messageDTO) {
        if (StrUtil.isNotBlank(messageDTO.getEventType())) {
            return;
        }
        var text = StrUtil.nullToEmpty(messageDTO.getText());

        // 1. 携带产物链接 → artifact 事件（如 ChartAgent/TableAgent/HtmlDocAgent 的成品通知）
        if (StrUtil.isNotBlank(messageDTO.getOpenUrl())
                || StrUtil.isNotBlank(messageDTO.getFileUrl())
                || StrUtil.isNotBlank(messageDTO.getImageUrl())) {
            messageDTO.setEventType(DialogMessageDTO.EVENT_ARTIFACT);
            var agentName = extractAgentName(text);
            messageDTO.setAgentName(agentName);
            messageDTO.setArtifactType(resolveArtifactType(agentName));
            return;
        }

        // 2. [系统] 前缀 → 取消确认 / 系统错误 / 系统提示
        if (text.startsWith("[系统]")) {
            var body = text.substring("[系统]".length());
            if (body.contains("已被取消")) {
                messageDTO.setEventType(DialogMessageDTO.EVENT_CANCELLED);
            } else if (body.contains("异常") || body.contains("失败") || body.contains("错误")) {
                messageDTO.setEventType(DialogMessageDTO.EVENT_SYSTEM_ERROR);
            } else {
                messageDTO.setEventType(DialogMessageDTO.EVENT_SYSTEM_INFO);
            }
            return;
        }

        // 3. [Agent名] 前缀 → 思考进度事件
        var agentName = extractAgentName(text);
        if (agentName != null) {
            messageDTO.setEventType(DialogMessageDTO.EVENT_PROGRESS);
            messageDTO.setAgentName(agentName);
        }
    }

    /**
     * 从文本中提取 [Agent名] 前缀里的 Agent 名称（支持英文 Agent 名，如 [ReActBrowserAgent]）。
     *
     * @return Agent 名称；无匹配前缀时返回 null
     */
    private String extractAgentName(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        var matcher = AGENT_NAME_PREFIX.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 依据 Agent 名称推断产物类型，供前端选择卡片图标与文案。
     */
    private String resolveArtifactType(String agentName) {
        if (agentName == null) {
            return "file";
        }
        if (agentName.contains("Chart")) {
            return "chart";
        }
        if (agentName.contains("Table")) {
            return "table";
        }
        if (agentName.contains("HtmlDoc")) {
            return "doc";
        }
        return "file";
    }

    /**
     * 将消息发送给客户端
     * <p>
     * 该方法负责将一个对话消息DTO（DialogMessageDTO）发送给特定客户端它通过获取当前会话ID（sessionId），
     * 创建适当的消息头，并使用Spring的MessagingTemplate将消息实际发送到客户端如果在发送过程中发生任何异常，
     * 它将捕获并记录错误
     *
     * @param messageDTO 包含要发送给客户端的消息信息的数据传输对象
     */
    private void sendToClient(DialogMessageDTO messageDTO) {
        // 1. 获取当前会话ID
        var sessionId = this.sessionIdProvider.get();

        // 2. 构造下行消息头
        SimpMessageHeaderAccessor downHeader = SimpMessageHeaderAccessor.create();
        downHeader.setSessionId(sessionId);
        downHeader.setLeaveMutable(true);

        try {
            // 3. 发送消息
            this.messagingTemplate.convertAndSendToUser(sessionId, this.destination, messageDTO, downHeader.getMessageHeaders());
            // 推送成功，清零连续失败计数
            this.consecutiveSendFailures.set(0);
        } catch (Exception e) {
            // 记录在发送消息过程中发生的异常，并累计连续失败次数（供 isAlive 判定客户端失活）
            log.error("Exception in sendToClient, messageDTO = {}", messageDTO, e);
            this.consecutiveSendFailures.incrementAndGet();
        }
    }

    /**
     * 尝试标记任务开始（单飞控制）：CAS 保证同一会话仅一个任务可标记成功。
     */
    @Override
    public boolean tryMarkRunning() {
        return this.running.compareAndSet(false, true);
    }

    /**
     * 标记任务结束，允许该会话接收下一个任务。
     */
    @Override
    public void markFinished() {
        this.running.set(false);
    }

    /**
     * 请求取消当前任务：引擎在每轮循环与每个动作前检查该标记。
     */
    @Override
    public void requestCancel() {
        this.cancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * 标记客户端已断开连接（由会话管理器监听断连事件后调用），同时自动请求取消。
     */
    public void markDisconnected() {
        this.disconnected = true;
        this.cancelled = true;
    }

    /**
     * 客户端是否存活：已断开，或连续推送失败达到阈值，均视为失活。
     */
    @Override
    public boolean isAlive() {
        return !this.disconnected && this.consecutiveSendFailures.get() < MAX_CONSECUTIVE_SEND_FAILURES;
    }
}
