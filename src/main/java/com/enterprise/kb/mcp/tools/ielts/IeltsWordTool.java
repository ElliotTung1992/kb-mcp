package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IeltsWordTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_list_words",
          description = "查询雅思单词列表，可按难度（1简单/2中等/3困难）、词表（AWL/GSL/IELTS）、话题标签筛选")
    public String listWords(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "词表类型：AWL / GSL / IELTS", required = false) String wordList,
            @ToolParam(description = "话题标签，如 environment、technology", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;

        JsonNode resp = ieltsApiClient.listWords(difficulty, wordList, topicTags, p, ps);
        JsonNode data = resp.path("data");
        JsonNode items = data.path("items");

        if (items.isEmpty()) return "未找到符合条件的单词。";

        long total = data.path("total").asLong();
        StringBuilder sb = new StringBuilder("共 ").append(total).append(" 个单词（第 ").append(p).append(" 页）：\n\n");
        for (JsonNode w : items) {
            sb.append("- **").append(w.path("word").asText()).append("**");
            String phonetic = w.path("phonetic").asText("");
            if (!phonetic.isBlank()) sb.append(" ").append(phonetic);
            sb.append("\n  释义：").append(w.path("definition").asText("—"));
            sb.append("\n  难度：").append(w.path("difficulty").asInt()).append(" | 词表：").append(w.path("wordList").asText("—"));
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(name = "ielts_list_phrases",
          description = "查询雅思常用短语列表，可按难度和话题标签筛选")
    public String listPhrases(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;

        JsonNode resp = ieltsApiClient.listPhrases(difficulty, topicTags, p, ps);
        JsonNode data = resp.path("data");
        JsonNode items = data.path("items");

        if (items.isEmpty()) return "未找到符合条件的短语。";

        long total = data.path("total").asLong();
        StringBuilder sb = new StringBuilder("共 ").append(total).append(" 个短语（第 ").append(p).append(" 页）：\n\n");
        for (JsonNode ph : items) {
            sb.append("- **").append(ph.path("phrase").asText()).append("**\n");
            sb.append("  含义：").append(ph.path("meaning").asText("—")).append("\n");
            String example = ph.path("exampleSentence").asText("");
            if (!example.isBlank()) sb.append("  例句：").append(example).append("\n");
        }
        return sb.toString().trim();
    }
}
