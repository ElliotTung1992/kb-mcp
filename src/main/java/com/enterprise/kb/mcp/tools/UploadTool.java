package com.enterprise.kb.mcp.tools;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import com.enterprise.kb.mcp.client.KbApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@RequiredArgsConstructor
public class UploadTool {

    private final KbApiClient kbApiClient;

    @Tool(name = "upload_document",
          description = "上传文档到指定知识空间（需要 EDITOR 权限）。文件内容须以 Base64 编码传入，上传后异步处理")
    public String upload(
            @ToolParam(description = "知识空间 ID") String spaceId,
            @ToolParam(description = "文件名，如 report.pdf 或 guide.md") String filename,
            @ToolParam(description = "Base64 编码的文件内容") String content,
            @ToolParam(description = "MIME 类型，如 application/pdf、text/plain、text/markdown") String mimeType) {

        String jwt = McpRequestContext.getJwt();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            return "错误：文件内容不是有效的 Base64 编码。";
        }

        JsonNode resp = kbApiClient.uploadDocument(spaceId, filename, bytes, mimeType, jwt);
        JsonNode data = resp.path("data");
        return "上传成功！文档 ID：" + data.path("id").asText()
                + "，文件名：" + data.path("originalFilename").asText(filename)
                + "，状态：" + data.path("status").asText()
                + "。文档将在后台异步处理，完成后可通过 get_document_content 获取全文内容。";
    }
}
