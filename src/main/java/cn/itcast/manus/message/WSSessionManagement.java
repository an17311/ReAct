package cn.itcast.manus.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
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
     * 清理会话对象
     *
     * @param sessionId 会话ID
     */
    public void clean(String sessionId) {
        this.sessionMap.remove(sessionId);
    }

}
