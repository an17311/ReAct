package cn.itcast.manus.service;

import cn.itcast.manus.dto.DialogMessageDTO;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

public interface WebSocketService {

    /**
     * 对话处理
     *
     * @param message        消息数据
     * @param headerAccessor 消息头
     */
    void enhancedDialog(DialogMessageDTO message, SimpMessageHeaderAccessor headerAccessor);

    /**
     * 取消当前会话正在执行的任务
     *
     * @param headerAccessor 消息头（用于获取会话 id）
     */
    void cancelDialog(SimpMessageHeaderAccessor headerAccessor);

}
