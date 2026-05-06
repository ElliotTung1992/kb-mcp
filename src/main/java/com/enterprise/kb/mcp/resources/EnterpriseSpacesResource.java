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
 * MCP Resource：{@code enterprise://spaces}
 *
 * <p>固定 URI，无需参数，AI 可直接读取。
 * 返回当前登录用户有权访问的所有知识空间（ID、名称、描述），
 * 是所有知识库操作的上下文入口——AI 先读这里拿到 spaceId，
 * 再决定调哪个 Tool 或读哪个 Resource Template。
 */
@Component
@RequiredArgsConstructor
public class EnterpriseSpacesResource {

    private final KbApiClient kbApiClient;

    public McpServerFeatures.SyncResourceSpecification spec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "enterprise://spaces",
                "enterprise-spaces",
                "当前用户有权访问的所有知识空间列表，包含空间 ID、名称、描述",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::read);
    }

    private McpSchema.ReadResourceResult read(McpSyncServerExchange exchange,
                                               McpSchema.ReadResourceRequest request) {
        JsonNode data = kbApiClient.listSpaces(McpRequestContext.getJwt()).path("data");
        // kb-app 返回直接数组或分页对象 {items: [...]}，兼容两种格式
        JsonNode items = data.isArray() ? data : data.path("items");

        StringBuilder sb = new StringBuilder("可用知识空间列表：\n\n");
        for (JsonNode space : items) {
            sb.append("- ID: ").append(space.path("id").asText())
              .append("\n  名称：").append(space.path("name").asText("—"))
              .append("\n  描述：").append(space.path("description").asText("（无描述）"))
              .append("\n");
        }

        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(request.uri(), "text/plain", sb.toString()))
        );
    }
}
