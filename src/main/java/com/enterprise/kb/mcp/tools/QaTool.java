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
public class QaTool {

    private final KbApiClient kbApiClient;

    // sessionId 由 kb-app 生成并在响应中返回，客户端回传即可延续多轮对话上下文
    @Tool(name = "ask_question",
          description = "基于知识库内容回答问题，返回 AI 生成的答案和引用来源文档片段")
    public String ask(
            @ToolParam(description = "知识空间 ID") String spaceId,
            @ToolParam(description = "要提问的问题") String question,
            @ToolParam(description = "多轮对话会话 ID，传入上次返回的 sessionId 可延续上下文（可选）", required = false) String sessionId) {

        String jwt = McpRequestContext.getJwt();
        JsonNode resp = kbApiClient.askQuestion(spaceId, question, sessionId, jwt);
        JsonNode data = resp.path("data");

        StringBuilder sb = new StringBuilder();
        sb.append("**答案：**\n").append(data.path("answer").asText("暂无答案")).append("\n");

        JsonNode citations = data.path("citations");
        if (!citations.isEmpty()) {
            sb.append("\n**引用来源：**\n");
            int i = 1;
            for (JsonNode c : citations) {
                sb.append(i++).append(". ").append(c.path("documentTitle").asText("未知文档"));
                int page = c.path("pageNumber").asInt(-1);
                if (page >= 0) sb.append("（第 ").append(page).append(" 页）");
                sb.append("\n   > ").append(c.path("excerpt").asText()).append("\n");
            }
        }

        String sid = data.path("sessionId").asText("");
        if (!sid.isEmpty()) sb.append("\n*sessionId: ").append(sid).append("*");

        return sb.toString().trim();
    }
}
