package com.enterprise.kb.mcp.tools;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import com.enterprise.kb.mcp.client.KbApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagTool {

    private final KbApiClient kbApiClient;

    @Tool(name = "list_tags",
          description = "获取知识空间的标签层级树，用于了解知识库的分类结构")
    public String listTags(
            @ToolParam(description = "知识空间 ID") String spaceId) {

        String jwt = McpRequestContext.getJwt();
        JsonNode resp = kbApiClient.getTagTree(spaceId, jwt);
        JsonNode tree = resp.path("data");

        if (tree.isEmpty()) return "该空间暂无标签。";

        StringBuilder sb = new StringBuilder("标签层级结构：\n\n");
        renderTree(tree, sb, 0);
        return sb.toString().trim();
    }

    private void renderTree(JsonNode nodes, StringBuilder sb, int depth) {
        for (JsonNode node : nodes) {
            sb.append("  ".repeat(depth))
              .append("- ").append(node.path("name").asText())
              .append("（").append(node.path("tagType").asText("TAG")).append("）")
              .append(" ID: ").append(node.path("id").asText())
              .append("\n");
            JsonNode children = node.path("children");
            if (!children.isEmpty()) renderTree(children, sb, depth + 1);
        }
    }
}
