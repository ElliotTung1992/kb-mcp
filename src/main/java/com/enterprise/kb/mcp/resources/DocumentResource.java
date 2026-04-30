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
 * MCP Resource：space://{spaceId}/documents
 * 提供知识空间文档列表（精简版：标题、状态、创建时间）。
 *
 * <p>最多返回 50 条，避免 Resource 内容撑爆 Claude 的 context window。
 * 需要完整分页列表时应使用 {@code list_documents} Tool。
 */
@Component
@RequiredArgsConstructor
public class DocumentResource {

    private final KbApiClient kbApiClient;

    public McpServerFeatures.SyncResourceSpecification spec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "space://{spaceId}/documents",
                "space-documents",
                "知识空间文档列表（精简版），含标题、状态、创建时间",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::read);
    }

    private McpSchema.ReadResourceResult read(McpSyncServerExchange exchange,
                                               McpSchema.ReadResourceRequest request) {
        String uri = request.uri();
        String spaceId = extractSpaceId(uri);
        String jwt = McpRequestContext.getJwt();

        JsonNode resp = kbApiClient.listDocuments(spaceId, 0, 50, jwt);
        JsonNode data = resp.path("data");
        JsonNode items = data.path("items");
        long total = data.path("total").asLong();

        StringBuilder sb = new StringBuilder("文档列表（共 ").append(total).append(" 个）：\n\n");
        for (JsonNode doc : items) {
            sb.append("- ").append(doc.path("title").asText("未命名"))
              .append("（ID: ").append(doc.path("id").asText()).append("）")
              .append(" 状态: ").append(doc.path("status").asText())
              .append(" 创建时间: ").append(doc.path("createdAt").asText("—"))
              .append("\n");
        }

        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(uri, "text/plain", sb.toString()))
        );
    }

    private static String extractSpaceId(String uri) {
        // space://spaceId/documents  →  spaceId
        return uri.replace("space://", "").split("/")[0];
    }
}
