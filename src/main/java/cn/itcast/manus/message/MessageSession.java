package cn.itcast.manus.message;

import cn.itcast.manus.dto.DialogMessageDTO;

/**
 * 消息会话抽象：封装一次对话上下文的“收/读/发”能力。
 * <p>它是前端与智能体之间的消息中转桥梁：智能体通过 {@link #sendMessage} 实时将中间结果推送给前端，
 * 而 {@link #receiveMessages}/{@link #readMessage} 用于接收并读取前端输入。</p>
 */
public interface MessageSession {

    /**
     * 接收消息
     *
     * @param messageDTO 消息对象
     */
    void receiveMessages(DialogMessageDTO messageDTO);

    /**
     * 读取消息
     */
    String readMessage();

    /**
     * 发送消息
     */
    void sendMessage(String msg);

    /**
     * 发送消息
     */
    void sendMessage(DialogMessageDTO messageDTO);
}
