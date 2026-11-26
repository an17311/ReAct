package cn.itcast.manus.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器。
 * <p>以会话 id 为键维护 {@link MessageSession} 实例，提供按需懒创建与任务结束后的清理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WSSessionManagement {

    // 用于存放会话id 与 会话对象的 映射关系
    private final Map<String, MessageSession> sessionMap = new ConcurrentHashMap<>();
    // 基本的消息发送模板
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 通过会话id获取会话对象（若不存在则创建一个绑定该 id 的 {@link WSSession}）。
     *
     * @param sessionId WebSocket 会话 id
     * @return 对应的消息会话对象
     */
    public MessageSession sessionDialog(String sessionId) {
        return sessionMap.computeIfAbsent(sessionId, key -> new WSSession(() -> key, "/queue/dialog", messagingTemplate));
    }

    /**
     * 按会话 id 查找已存在的会话对象（不创建）。
     *
     * @param sessionId 会话 id
     * @return 会话对象；不存在时返回空
     */
    public Optional<MessageSession> findSession(String sessionId) {
        return Optional.ofNullable(this.sessionMap.get(sessionId));
    }

    /**
     * 清理会话对象
     *
     * @param sessionId 会话ID
     */
    public void clean(String sessionId) {
        this.sessionMap.remove(sessionId);
    }

    /**
     * 监听 STOMP 断连事件：标记该会话客户端已断开并自动请求取消，
     * 使引擎在最近的检查点终止任务，避免对已离开的用户空烧 token。
     * <p>会话对象不从映射中移除，仍由任务结束时的 {@link #clean} 统一清理。</p>
     */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        var sessionId = event.getSessionId();
        this.findSession(sessionId).ifPresent(session -> {
            if (session instanceof WSSession wsSession) {
                wsSession.markDisconnected();
            } else {
                session.requestCancel();
            }
            log.info("WebSocket 会话断开，已标记取消: {}", sessionId);
        });
    }

}
