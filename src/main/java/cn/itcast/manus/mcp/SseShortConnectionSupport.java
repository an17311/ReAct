package cn.itcast.manus.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpClient.AsyncSpec;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 由于amap的server在sse端链接2min后自动断链，springai不支持短sse方案，故使用此方式每次做初始化，使用完后关闭
 *
 * @author zepu
 *
 */
@Component
@Slf4j
public class SseShortConnectionSupport {

    @Value("${amap.key:0c4e4fccb076468f694bdd243523feaf}")
    private String amapKey;

    /**
     * 获取高德工具回调提供者并回调执行（供 AMAPAgent 调用大模型时携带工具）。
     *
     * @param func 接收工具回调提供者的函数
     * @param <T>  返回类型
     * @return 函数执行结果
     */
    public <T> T requestAmapSse(Function<ToolCallbackProvider, T> func) {
        var provider = amapToolCallbackProvider();
        return func.apply(provider);
    }

    /**
     * 创建一个短连接的 MCP 客户端（先 initialize），在其上执行回调，无论成败都优雅关闭连接。
     *
     * @param func 接收 {@link McpAsyncClient} 的函数
     * @param <T>  返回类型
     * @return 函数执行结果
     */
    public <T> T retrieveFromShortConnectionClient(Function<McpAsyncClient, T> func) {
        var cli = amapMCPClient();
        cli.initialize().doOnError(e -> log.error("error on sse client init", e))
                .doOnSuccess(v -> log.trace("sse client inited.")).block();
        try {
            return func.apply(cli);
        } finally {
            cli.closeGracefully().doOnError(e -> log.error("error on sse client close", e))
                    .doOnSuccess(v -> log.trace("sse client closed.")).block();
        }
    }

    /**
     * 以短连接客户端为参执行无返回值的消费型操作。
     *
     * @param cons 接收 {@link McpAsyncClient} 的消费者
     */
    public void useShortConnectionClient(Consumer<McpAsyncClient> cons) {
        Function<McpAsyncClient, Void> func = c -> {
            cons.accept(c);
            return null;
        };
        retrieveFromShortConnectionClient(func);
    }

    private McpClientTransport amapMCPTransport() {
        return HttpClientSseClientTransport.builder("https://mcp.amap.com")
                .sseEndpoint(String.format("/sse?key=%s", amapKey))
                .objectMapper(new ObjectMapper())
                .build();
    }

    private McpAsyncClient amapMCPClient() {
        var namedTransport = amapMCPTransport();
        McpSchema.Implementation clientInfo = new McpSchema.Implementation("spring-ai-amap-mcp", "1.0.0");

        AsyncSpec spec = McpClient.async(namedTransport)
                .clientInfo(clientInfo)
                .requestTimeout(Duration.ofSeconds(12));
        return spec.build();
    }

    private ToolCallbackProvider amapToolCallbackProvider() {
        return new LazyAsyncMcpToolCallbackProvider();
    }

    /**
     * 懒加载的高德工具回调提供者：每次获取时才通过短连接拉取工具列表。
     */
    class LazyAsyncMcpToolCallbackProvider implements ToolCallbackProvider {

        @Override
        public ToolCallback @NotNull [] getToolCallbacks() {
            List<ToolCallback> toolCallBack = new LinkedList<>();
            useShortConnectionClient(cli -> {
                cli.listTools().map(response -> response.tools()
                        .stream()
                        .map(LazyToolCallback::new)
                        .toList())
                        .block().forEach(toolCallBack::add);
            });
            return toolCallBack.toArray(ToolCallback[]::new);
        }

    }

    /**
     * 懒执行的工具回调：将 MCP 原始工具包装为 Spring AI 的 {@link ToolCallback}，
     * 定义与调用时都临时建立短连接获取信息。
     */
    class LazyToolCallback implements ToolCallback {

        private final Tool tool;

        public LazyToolCallback(Tool tool) {
            this.tool = tool;
        }

        @Override
        public @NotNull ToolDefinition getToolDefinition() {
            String name = retrieveFromShortConnectionClient(cli -> cli.getClientInfo().name());
            return ToolDefinition.builder().name(McpToolUtils.prefixedToolName(name, this.tool.name()))
                    .description(this.tool.description())
                    .inputSchema(ModelOptionsUtils.toJsonString(this.tool.inputSchema())).build();
        }

        @Override
        public @NotNull String call(@NotNull String functionInput) {
            log.info("tool call - [{}]input:{}", tool.name(), functionInput);
            var res = retrieveFromShortConnectionClient(cli -> {
                Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(functionInput);
                // Note that we use the original tool name here, not the adapted one from
                // getToolDefinition
                return cli.callTool(new CallToolRequest(this.tool.name(), arguments)).map(response -> {
                    if (response.isError() != null && response.isError()) {
                        throw new IllegalStateException("Error calling tool: " + response.content());
                    }
                    return ModelOptionsUtils.toJsonString(response.content());
                }).block();
            });
            log.info("tool call - [{}]output:{}", tool.name(), res);
            return res;
        }

        @Override
        public @NotNull String call(@NotNull String toolArguments, @NotNull ToolContext toolContext) {
            return call(toolArguments);
        }

    }
}
