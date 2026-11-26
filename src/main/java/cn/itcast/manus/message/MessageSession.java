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

    /**
     * 尝试标记任务开始（单飞控制）：同一会话同一时间仅允许一个任务执行。
     *
     * @return true 标记成功；false 表示已有任务在执行，本次应拒绝
     */
    default boolean tryMarkRunning() {
        return true;
    }

    /**
     * 标记任务结束（与 {@link #tryMarkRunning} 配对使用）
     */
    default void markFinished() {
    }

    /**
     * 请求取消当前会话正在执行的任务
     */
    default void requestCancel() {
    }

    /**
     * 任务是否已被请求取消
     */
    default boolean isCancelled() {
        return false;
    }

    /**
     * 客户端连接是否存活（已断开或连续推送失败视为失活，引擎据此终止任务避免空烧资源）
     */
    default boolean isAlive() {
        return true;
    }
}
