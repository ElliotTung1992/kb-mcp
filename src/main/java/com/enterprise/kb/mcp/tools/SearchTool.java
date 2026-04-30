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
public class SearchTool {

    private final KbApiClient kbApiClient;

    // description 是 Claude 选择是否调用此 Tool 的依据，措辞直接影响 Claude 的工具选择行为
    @Tool(name = "search_knowledge_base",
          description = "在指定知识空间中搜索相关文档片段，支持 semantic（语义）、keyword（关键词）、hybrid（混合，默认）三种模式")
    public String search(
            @ToolParam(description = "知识空间 ID") String spaceId,
            @ToolParam(description = "搜索查询词") String query,
            @ToolParam(description = "搜索模式：semantic | keyword | hybrid，默认 hybrid", required = false) String mode,
            @ToolParam(description = "返回结果数量，默认 5", required = false) Integer topK) {

        String jwt = McpRequestContext.getJwt();
        int k = topK != null ? topK : 5;
        String m = mode != null ? mode : "hybrid";

        JsonNode resp = switch (m) {
            case "semantic" -> kbApiClient.semanticSearch(spaceId, query, k, jwt);
            case "keyword"  -> kbApiClient.keywordSearch(spaceId, query, k, jwt);
            default         -> kbApiClient.hybridSearch(spaceId, query, k, jwt);
        };

        JsonNode hits = resp.path("data").path("hits");
        if (hits.isEmpty()) return "未找到相关内容。";

        StringBuilder sb = new StringBuilder();
        sb.append("搜索模式：").append(m).append("，找到 ").append(hits.size()).append(" 条结果：\n\n");
        int i = 1;
        for (JsonNode hit : hits) {
            sb.append(i++).append(". **").append(hit.path("documentTitle").asText("未知文档")).append("**\n");
            sb.append("   相关度：").append(String.format("%.3f", hit.path("score").asDouble())).append("\n");
            sb.append("   摘录：").append(hit.path("excerpt").asText()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
