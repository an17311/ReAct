package cn.itcast.manus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.agent.AgentFactory;
import cn.itcast.manus.dto.DialogMessageDTO;
import cn.itcast.manus.enums.AgentTypeEnum;
import cn.itcast.manus.message.MessageSession;
import cn.itcast.manus.message.WSSessionManagement;
import cn.itcast.manus.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link WebSocketService} 实现：对接 WebSocket 会话与智能体执行。
 * <p>链路作用：接收前端消息→获取/初始化会话→创建任务规划智能体→异步执行任务→将结果回推前端。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final WSSessionManagement sessionManagement;
    // 缓存线程池，用于将长时间运行的智能体任务与 STOMP 消息处理线程隔离，异步执行
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 处理前端上行的对话消息，并在独立线程中驱动智能体完成任务。
     *
     * @param message        前端消息数据
     * @param headerAccessor 消息头访问器，用于获取会话 id
     */
    @Override
    public void enhancedDialog(DialogMessageDTO message, SimpMessageHeaderAccessor headerAccessor) {
        // 空消息直接忽略，避免无效启动一次任务流程
        if (StrUtil.isEmpty(message.getText())) {
            log.info("receive empty message!!!");
            return;
        }

        // 获取会话id
        var sessionId = headerAccessor.getSessionId();

        // 通过会话id，获取到会话对象
        var messageSession = this.sessionManagement.sessionDialog(sessionId);

        // 接收消息数据
        messageSession.receiveMessages(message);
        // log.info("receive message: {}", messageSession.readMessage());

        // 给客户端发送消息
        // messageSession.sendMessage("消息已收到！");
        // 从工厂获取“任务规划智能体”的构造函数，并绑定当前会话创建一个智能体实例
        var agent = AgentFactory.getAgent(AgentTypeEnum.RE_ACT_PLANNING_AGENT).apply(messageSession);
        Runnable src = () -> {
            try {
                // 获取客户端发来的消息
                var rawInput = messageSession.readMessage();
                // 通过规划agent进行执行（内部会拆解任务并调度各专项智能体）
                var finalResult = agent.solveTask(rawInput);
                log.info("complete with final result:{}", finalResult);
                // 将最终结果与结束标志回推给客户端
                messageSession.sendMessage(finalResult);
                messageSession.sendMessage("任务流程结束");
            } finally {
                // 无论成功或异常，都清理会话，避免会话对象泄漏
                sessionManagement.clean(sessionId);
            }
        };

        // 提交到线程池异步执行，不阻塞 STOMP 消息处理线程
        executor.execute(src);

    }

}
