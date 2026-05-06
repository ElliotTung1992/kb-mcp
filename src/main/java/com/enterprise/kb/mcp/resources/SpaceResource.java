package com.enterprise.kb.mcp.resources;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import com.enterprise.kb.mcp.client.KbApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Resource Template：{@code space://{spaceId}/info}
 *
 * <p>URI 含模板参数，SDK 会将其同时放入 Resources 和 Resource Templates 两个列表。
 * AI 无法直接使用，需先读取 {@code enterprise://spaces} 获取 spaceId，
 * 再以 {@code space://实际spaceId/info} 构造具体 URI 发起读取请求。
 */
@Component
@RequiredArgsConstructor
public class SpaceResource {

    private final KbApiClient kbApiClient;

    public McpServerFeatures.SyncResourceSpecification spec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "space://{spaceId}/info",
                "space-info",
                "知识空间元信息，包含名称、描述、slug、首选模型等。需将 {spaceId} 替换为实际 ID",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::read);
    }

    private McpSchema.ReadResourceResult read(McpSyncServerExchange exchange,
                                               McpSchema.ReadResourceRequest request) {
        String uri = request.uri();
        String spaceId = extractSpaceId(uri);

        // 客户端直接用模板 URI 请求时（未替换 {spaceId}），返回引导提示而非报错
        if (spaceId.contains("{") || spaceId.contains("}")) {
            String hint = "请将 {spaceId} 替换为实际的空间 ID，例如：space://your-space-id/info\n"
                        + "可先读取 enterprise://spaces 获取可用的 spaceId 列表。";
            return new McpSchema.ReadResourceResult(
                    List.of(new McpSchema.TextResourceContents(uri, "text/plain", hint))
            );
        }

        JsonNode data = kbApiClient.getSpaceInfo(spaceId, McpRequestContext.getJwt()).path("data");

        String text = "知识空间信息\n"
                + "名称：" + data.path("name").asText("—") + "\n"
                + "slug：" + data.path("slug").asText("—") + "\n"
                + "描述：" + data.path("description").asText("（无描述）") + "\n"
                + "首选模型：" + data.path("preferredModelProvider").asText("—");

        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(uri, "text/plain", text))
        );
    }

    private static String extractSpaceId(String uri) {
        return uri.replace("space://", "").split("/")[0];
    }
}
