package cn.itcast.manus.message;

import cn.itcast.manus.dto.DialogMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

/**
 * {@link MessageSession} 基于 STOMP WebSocket 的实现。
 * <p>使用线程安全的阻塞队列缓冲上行消息，并通过 {@link SimpMessagingTemplate} 将下行消息定向发送到当前用户的队列。</p>
 */
@Slf4j
public class WSSession implements MessageSession {

    // 消息缓存区，使用线程安全队列
    private final BlockingQueue<DialogMessageDTO> buffer = new ArrayBlockingQueue<>(1024);
    // 会话ID提供者
    private final Supplier<String> sessionIdProvider;

    private final SimpMessagingTemplate messagingTemplate;

    // 发送消息的目标
    private final String destination;

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
     *
     * @param messageDTO 消息对象
     */
    @Override
    public void sendMessage(DialogMessageDTO messageDTO) {
        messageDTO.setType(DialogMessageDTO.TYPE_SERVER);
        this.sendToClient(messageDTO);
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
        } catch (Exception e) {
            // 记录在发送消息过程中发生的异常
            log.error("Exception in sendToClient, messageDTO = {}", messageDTO, e);
        }
    }
}
