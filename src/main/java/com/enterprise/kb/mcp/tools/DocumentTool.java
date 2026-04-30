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
public class DocumentTool {

    private final KbApiClient kbApiClient;

    @Tool(name = "list_documents",
          description = "列出知识空间中的文档，返回标题、状态、创建时间等元数据")
    public String listDocuments(
            @ToolParam(description = "知识空间 ID") String spaceId,
            @ToolParam(description = "页码，从 0 开始，默认 0", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {

        String jwt = McpRequestContext.getJwt();
        int p = page != null ? page : 0;
        int ps = pageSize != null ? pageSize : 20;

        JsonNode resp = kbApiClient.listDocuments(spaceId, p, ps, jwt);
        JsonNode data = resp.path("data");
        JsonNode items = data.path("items");

        if (items.isEmpty()) return "该空间暂无文档。";

        long total = data.path("total").asLong();
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 个文档（第 ").append(p + 1).append(" 页）：\n\n");
        for (JsonNode doc : items) {
            sb.append("- **").append(doc.path("title").asText("未命名")).append("**")
              .append("（ID: ").append(doc.path("id").asText()).append("）")
              .append(" 状态: ").append(doc.path("status").asText())
              .append(" 分块数: ").append(doc.path("chunkCount").asInt(0))
              .append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(name = "get_document_content",
          description = "获取指定文档的全文内容（所有分块拼接），内容超过 10000 字时自动截断")
    public String getDocumentContent(
            @ToolParam(description = "知识空间 ID") String spaceId,
            @ToolParam(description = "文档 ID") String docId) {

        String jwt = McpRequestContext.getJwt();
        String content = kbApiClient.getDocumentContent(spaceId, docId, jwt);
        if (content == null || content.isBlank()) return "文档内容为空或尚未处理完成。";
        return content;
    }
}
