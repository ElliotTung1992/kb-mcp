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
 * MCP Resource：space://{spaceId}/info
 * 提供知识空间元信息（名称、描述、slug 等）。
 */
@Component
@RequiredArgsConstructor
public class SpaceResource {

    private final KbApiClient kbApiClient;

    public McpServerFeatures.SyncResourceSpecification spec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "space://{spaceId}/info",
                "space-info",
                "知识空间元信息，包含名称、描述、slug 等",
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

        JsonNode data = kbApiClient.getSpaceInfo(spaceId, jwt).path("data");

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
        // space://spaceId/info  →  spaceId
        return uri.replace("space://", "").split("/")[0];
    }
}
