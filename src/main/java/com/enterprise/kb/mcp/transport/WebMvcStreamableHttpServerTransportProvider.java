package com.enterprise.kb.mcp.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * MCP Streamable HTTP transport，单端点 /mcp 处理 JSON-RPC 请求/响应。
 *
 * <p>Spring AI 1.0.0 只内置旧 HTTP+SSE transport，通过实现 {@link McpServerTransportProvider}
 * 并利用 {@code @ConditionalOnMissingBean} 替换自动配置。
 *
 * <p>会话管理：initialize 时创建新会话并在响应头返回 Mcp-Session-Id，
 * 后续请求凭 header 路由到对应会话。每个 POST 请求通过 CompletableFuture 同步等待响应。
 *
 * <p>已知限制：SDK 0.10.0 只支持协议 2024-11-05，initialize 响应会将客户端请求的版本号回显，
 * 使 Inspector 等较新客户端不因版本不匹配而拒绝连接。
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
                .GET(mcpEndpoint, this::handleGet)
                .DELETE(mcpEndpoint, this::handleDelete)
                .build();
    }

    // ── GET /mcp (SSE stream for server push — not supported, return 405) ──

    private ServerResponse handleGet(ServerRequest request) {
        // MCP 2025-11-25: client opens GET SSE for server-initiated messages.
        // We don't support server push; 405 tells the client to use POST-only mode.
        return ServerResponse.status(405)
                .header("Allow", "POST, DELETE")
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

            // Requests: bridge reactive result to HTTP response via CompletableFuture keyed by request id
            Object requestId = ((McpSchema.JSONRPCRequest) message).id();
            CompletableFuture<McpSchema.JSONRPCMessage> responseFuture = transport.registerRequest(requestId);
            session.handle(message).block();

            McpSchema.JSONRPCMessage response = responseFuture.get(30, TimeUnit.SECONDS);
            String responseJson = objectMapper.writeValueAsString(response);

            // SDK only supports 2024-11-05 but clients may request a newer version.
            // Echo the client's requested protocolVersion so Inspector doesn't reject the connection.
            if (isInitialize) {
                responseJson = patchProtocolVersion(responseJson, message);
            }

            return ServerResponse.ok()
                    .header("Mcp-Session-Id", responseSessionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);

        } catch (Exception e) {
            log.error("MCP Streamable HTTP 处理失败", e);
            return errorResponse(null, -32603, "Internal server error: " + e.getMessage());
        }
    }

    // ── 协议版本补丁 ──────────────────────────────────────────────────────────

    private String patchProtocolVersion(String responseJson, McpSchema.JSONRPCMessage initRequest) {
        try {
            McpSchema.JSONRPCRequest req = (McpSchema.JSONRPCRequest) initRequest;
            JsonNode params = objectMapper.valueToTree(req.params());
            String clientProtocol = params.path("protocolVersion").asText(null);
            if (clientProtocol == null) return responseJson;

            ObjectNode root = (ObjectNode) objectMapper.readTree(responseJson);
            JsonNode result = root.get("result");
            if (result instanceof ObjectNode resultObj && resultObj.has("protocolVersion")) {
                String serverProtocol = resultObj.path("protocolVersion").asText();
                resultObj.put("protocolVersion", clientProtocol);
                log.info("initialize 协议版本: {} → {}", serverProtocol, clientProtocol);
                return objectMapper.writeValueAsString(root);
            }
        } catch (Exception e) {
            log.warn("协议版本补丁失败，使用原始响应: {}", e.getMessage());
        }
        return responseJson;
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
     * 每次 POST 请求通过 {@link #registerRequest(Object)} 注册 Future（以 request id 为 key），
     * McpServerSession 处理完毕后 {@link #sendMessage} 按 id 路由到正确的 Future，
     * handlePost 阻塞等待后将结果写回 HTTP 响应。
     * 使用 ConcurrentHashMap 支持同一会话的并发请求（如 Inspector 同时发 tools/list + resources/list）。
     */
    static class StreamableSessionTransport implements McpServerTransport {

        private final ObjectMapper objectMapper;
        private final ConcurrentHashMap<Object, CompletableFuture<McpSchema.JSONRPCMessage>> pendingRequests =
                new ConcurrentHashMap<>();

        StreamableSessionTransport(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        CompletableFuture<McpSchema.JSONRPCMessage> registerRequest(Object requestId) {
            CompletableFuture<McpSchema.JSONRPCMessage> future = new CompletableFuture<>();
            pendingRequests.put(requestId, future);
            return future;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            if (message instanceof McpSchema.JSONRPCResponse response && response.id() != null) {
                CompletableFuture<McpSchema.JSONRPCMessage> future = pendingRequests.remove(response.id());
                if (future != null) {
                    future.complete(message);
                }
            }
            return Mono.empty();
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return objectMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            pendingRequests.values().forEach(f -> f.cancel(true));
            pendingRequests.clear();
            return Mono.empty();
        }
    }
}
