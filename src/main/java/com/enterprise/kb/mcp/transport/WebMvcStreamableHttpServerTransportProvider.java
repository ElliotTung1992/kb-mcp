package com.enterprise.kb.mcp.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP Streamable HTTP transport (2024-11-05 spec).
 *
 * <p>단일 엔드포인트(/mcp)로 JSON-RPC request/response를 처리한다.
 * Spring AI 1.0.0은 구 HTTP+SSE transport만 내장하므로, {@link McpServerTransportProvider}를
 * 직접 구현해 {@code @ConditionalOnMissingBean} 조건을 통해 auto-configure를 대체한다.
 *
 * <p>세션 관리: initialize 시 새 세션 생성 → Mcp-Session-Id 헤더 반환.
 * 이후 요청은 헤더로 세션을 찾아 라우팅.
 * 각 요청은 {@link StreamableSessionTransport}의 CompletableFuture로 응답을 캡처.
 */
@Slf4j
public class WebMvcStreamableHttpServerTransportProvider implements McpServerTransportProvider {

    private final ObjectMapper objectMapper;
    private final String mcpEndpoint;

    private final ConcurrentHashMap<String, McpServerSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamableSessionTransport> transports = new ConcurrentHashMap<>();

    private McpServerSession.Factory sessionFactory;

    public WebMvcStreamableHttpServerTransportProvider(ObjectMapper objectMapper, String mcpEndpoint) {
        this.objectMapper = objectMapper;
        this.mcpEndpoint = mcpEndpoint;
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory factory) {
        this.sessionFactory = factory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        // Stateless Streamable HTTP: no server-push channel maintained
        return Mono.empty();
    }

    @Override
    public Mono<Void> closeGracefully() {
        sessions.values().forEach(McpServerSession::close);
        sessions.clear();
        transports.clear();
        return Mono.empty();
    }

    public RouterFunction<ServerResponse> getRouterFunction() {
        return RouterFunctions.route()
                .POST(mcpEndpoint, this::handlePost)
                .DELETE(mcpEndpoint, this::handleDelete)
                .build();
    }

    // ── POST /mcp ─────────────────────────────────────────────────────────

    private ServerResponse handlePost(ServerRequest request) {
        try {
            String body = request.body(String.class);
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);

            String sessionId = request.headers().firstHeader("Mcp-Session-Id");
            boolean isInitialize = message instanceof McpSchema.JSONRPCRequest req
                    && "initialize".equals(req.method());

            McpServerSession session;
            StreamableSessionTransport transport;
            String responseSessionId;

            if (isInitialize) {
                responseSessionId = UUID.randomUUID().toString();
                transport = new StreamableSessionTransport(objectMapper);
                session = sessionFactory.create(transport);
                sessions.put(responseSessionId, session);
                transports.put(responseSessionId, transport);
                log.debug("新 MCP 会话已创建: {}", responseSessionId);
            } else {
                if (sessionId == null || !sessions.containsKey(sessionId)) {
                    return errorResponse(null, -32600, "Missing or invalid Mcp-Session-Id, call initialize first");
                }
                session = sessions.get(sessionId);
                transport = transports.get(sessionId);
                responseSessionId = sessionId;
            }

            // Notifications: fire-and-forget, no response body
            if (message instanceof McpSchema.JSONRPCNotification) {
                session.handle(message).block();
                return ServerResponse.accepted()
                        .header("Mcp-Session-Id", responseSessionId)
                        .build();
            }

            // Requests: bridge reactive result to HTTP response via CompletableFuture
            CompletableFuture<McpSchema.JSONRPCMessage> responseFuture = transport.setCurrentRequest();
            session.handle(message).block();

            McpSchema.JSONRPCMessage response = responseFuture.get(30, TimeUnit.SECONDS);
            return ServerResponse.ok()
                    .header("Mcp-Session-Id", responseSessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            log.error("MCP Streamable HTTP 处理失败", e);
            return errorResponse(null, -32603, "Internal server error: " + e.getMessage());
        }
    }

    // ── DELETE /mcp ───────────────────────────────────────────────────────

    private ServerResponse handleDelete(ServerRequest request) {
        String sessionId = request.headers().firstHeader("Mcp-Session-Id");
        if (sessionId != null) {
            McpServerSession session = sessions.remove(sessionId);
            if (session != null) {
                session.close();
                log.info("MCP 会话已关闭: {}", sessionId);
            }
            transports.remove(sessionId);
        }
        return ServerResponse.ok().build();
    }

    private ServerResponse errorResponse(String id, int code, String message) {
        String idPart = id != null ? "\"" + id + "\"" : "null";
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + idPart
                + ",\"error\":{\"code\":" + code + ",\"message\":\""
                + message.replace("\"", "\\\"") + "\"}}";
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // ── Per-session transport ─────────────────────────────────────────────

    /**
     * 每个 MCP 会话对应一个 transport 实例。
     * 每次 POST 请求通过 {@link #setCurrentRequest()} 注册新 Future，
     * McpServerSession 处理完毕后调用 {@link #sendMessage} 完成 Future，
     * handlePost 阻塞等待后将结果写回 HTTP 响应。
     */
    static class StreamableSessionTransport implements McpServerTransport {

        private final ObjectMapper objectMapper;
        private volatile CompletableFuture<McpSchema.JSONRPCMessage> currentResponseFuture;

        StreamableSessionTransport(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        CompletableFuture<McpSchema.JSONRPCMessage> setCurrentRequest() {
            CompletableFuture<McpSchema.JSONRPCMessage> future = new CompletableFuture<>();
            this.currentResponseFuture = future;
            return future;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            CompletableFuture<McpSchema.JSONRPCMessage> future = currentResponseFuture;
            if (future != null && !future.isDone()) {
                future.complete(message);
            }
            return Mono.empty();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return objectMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            CompletableFuture<McpSchema.JSONRPCMessage> future = currentResponseFuture;
            if (future != null) future.cancel(true);
            return Mono.empty();
        }
    }
}
