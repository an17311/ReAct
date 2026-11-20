package cn.itcast.manus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 消息配置。
 * <p>负责注册客户端连接端点、配置上/下行消息的路由前缀，是整个对话链路的通信基础。</p>
 */
@Configuration
@EnableWebSocketMessageBroker  // 启用websocket消息代理功能
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理：划分客户端上行与服务端下行的路径前缀。
     *
     * @param config 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 服务端向客户端发送数据的路径前缀（启用内存简单代理，客户端订阅 /queue 开头的目的地）
        config.enableSimpleBroker("/queue");
        // 客户端向服务端发送数据的路径前缀（@MessageMapping 方法会映射到 /app/xxx）
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 注册 STOMP 连接端点，客户端通过该端点建立 WebSocket 连接。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个STOMP的端点，允许任何客户端访问
        registry.addEndpoint("/bs-dialog-websocket")
                .setAllowedOriginPatterns("*");
    }
}
