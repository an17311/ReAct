package cn.itcast.manus.controller;

import cn.itcast.manus.dto.DialogMessageDTO;
import cn.itcast.manus.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 对话入口控制器。
 * <p>作为整条链路的后端起点：接收前端通过 STOMP 发布的消息，并交由 {@link WebSocketService} 开展后续的智能体调度。</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketService webSocketService;

    /**
     * 处理前端发布的对话消息。
     * <p>{@code @MessageMapping("/enhanced-dialog")} 将目的地 {@code /app/enhanced-dialog} 的消息路由到此方法。</p>
     *
     * @param messageDTO     前端上行的对话消息体
     * @param headerAccessor 消息头访问器，可用于获取当前 WebSocket 会话 id
     */
    @MessageMapping("/enhanced-dialog")
    public void enhancedDialog(DialogMessageDTO messageDTO, SimpMessageHeaderAccessor headerAccessor) {
        log.info("enhanced-dialog: messageDTO = {} ", messageDTO);

        //业务处理
        this.webSocketService.enhancedDialog(messageDTO, headerAccessor);
    }

}
