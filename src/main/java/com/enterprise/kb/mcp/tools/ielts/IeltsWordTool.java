package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IeltsWordTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_list_words",
          description = "查询雅思单词列表，可按难度（1简单/2中等/3困难）、词表（AWL/GSL/IELTS）、话题标签筛选")
    public Map<String, Object> listWords(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "词表类型：AWL / GSL / IELTS", required = false) String wordList,
            @ToolParam(description = "话题标签，如 environment、technology", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;

        JsonNode resp = ieltsApiClient.listWords(difficulty, wordList, topicTags, p, ps);
        return IeltsToolResponses.paged(
                "单词",
                resp,
                IeltsToolResponses.filters("difficulty", difficulty, "wordList", wordList, "topicTags", topicTags),
                p,
                ps,
                this::wordItem);
    }

    @Tool(name = "ielts_list_phrases",
          description = "查询雅思常用短语列表，可按难度和话题标签筛选")
    public Map<String, Object> listPhrases(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 20", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;

        JsonNode resp = ieltsApiClient.listPhrases(difficulty, topicTags, p, ps);
        return IeltsToolResponses.paged(
                "短语",
                resp,
                IeltsToolResponses.filters("difficulty", difficulty, "topicTags", topicTags),
                p,
                ps);
    }

    @Tool(name = "ielts_create_word",
          description = "新增一个雅思单词，需提供单词原形和中文释义，其余字段可选")
    public Map<String, Object> createWord(
            @ToolParam(description = "单词原形，如 ambiguous") String word,
            @ToolParam(description = "中文释义，多义词用换行分隔") String definitionZh,
            @ToolParam(description = "英文释义", required = false) String definitionEn,
            @ToolParam(description = "英式音标，如 /æmˈbɪɡjuəs/", required = false) String phoneticUk,
            @ToolParam(description = "美式音标", required = false) String phoneticUs,
            @ToolParam(description = "词性，如 adj. / n. / v.", required = false) String partOfSpeech,
            @ToolParam(description = "词表来源：AWL / GSL / IELTS", required = false) String wordList,
            @ToolParam(description = "难度：1=基础，2=中级，3=高级", required = false) Integer difficulty,
            @ToolParam(description = "话题标签，逗号分隔，如 environment,technology", required = false) String topicTags,
            @ToolParam(description = "适用技能，逗号分隔，如 reading,writing", required = false) String skillTags,
            @ToolParam(description = "关联词，逗号分隔，如同义词/反义词等", required = false) String relatedWords,
            @ToolParam(description = "例句列表，每项包含 sentence（英文例句）和 translation（中文翻译）", required = false) List<Map<String, String>> examples) {

        JsonNode resp = ieltsApiClient.createWord(wordBody(
                word, definitionZh, definitionEn, phoneticUk, phoneticUs, partOfSpeech,
                wordList, difficulty, topicTags, skillTags, relatedWords, examples));
        Map<String, Object> result = IeltsToolResponses.data("单词创建结果", resp);
        result.put("message", "单词创建成功");
        return result;
    }

    @Tool(name = "ielts_batch_import_words",
          description = "批量导入雅思单词，每个单词至少包含 word（原形）和 definitionZh（中文释义）")
    public Map<String, Object> batchImportWords(
            @ToolParam(description = "单词列表，JSON 数组，每项字段同 ielts_create_word") List<Map<String, Object>> words) {

        if (words == null || words.isEmpty()) {
            return IeltsToolResponses.error("批量导入单词", "单词列表为空，未导入任何数据。");
        }

        JsonNode resp = ieltsApiClient.batchImportWords(words);
        Map<String, Object> result = IeltsToolResponses.data("批量导入单词结果", resp);
        result.put("message", "批量导入完成");
        return result;
    }

    private Map<String, Object> wordBody(
            String word,
            String definitionZh,
            String definitionEn,
            String phoneticUk,
            String phoneticUs,
            String partOfSpeech,
            String wordList,
            Integer difficulty,
            String topicTags,
            String skillTags,
            String relatedWords,
            List<Map<String, String>> examples) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("word", word);
        body.put("definitionZh", definitionZh);
        if (definitionEn  != null) body.put("definitionEn", definitionEn);
        if (phoneticUk    != null) body.put("phoneticUk", phoneticUk);
        if (phoneticUs    != null) body.put("phoneticUs", phoneticUs);
        if (partOfSpeech  != null) body.put("partOfSpeech", partOfSpeech);
        if (wordList      != null) body.put("wordList", wordList);
        if (difficulty    != null) body.put("difficulty", difficulty);
        if (topicTags     != null) body.put("topicTags", topicTags);
        if (skillTags     != null) body.put("skillTags", skillTags);
        if (relatedWords  != null) body.put("relatedWords", relatedWords);
        if (examples != null && !examples.isEmpty()) {
            body.put("examples", examples);
        }
        return body;
    }

    private Map<String, Object> wordItem(JsonNode w) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", textOrNull(w, "id"));
        item.put("word", textOrNull(w, "word"));
        item.put("phonetic", phonetic(w));
        item.put("phoneticUk", textOrNull(w, "phoneticUk"));
        item.put("phoneticUs", textOrNull(w, "phoneticUs"));
        item.put("partOfSpeech", textOrNull(w, "partOfSpeech"));
        item.put("definitionZh", textOrNull(w, "definitionZh"));
        item.put("definitionEn", textOrNull(w, "definitionEn"));
        item.put("frequencyLevel", integerOrNull(w, "frequencyLevel"));
        item.put("wordList", textOrNull(w, "wordList"));
        item.put("difficulty", integerOrNull(w, "difficulty"));
        item.put("skillTags", textOrNull(w, "skillTags"));
        item.put("topicTags", textOrNull(w, "topicTags"));
        item.put("relatedWords", textOrNull(w, "relatedWords"));
        item.put("studyStatus", textOrNull(w, "studyStatus"));
        item.put("linkCount", integerOrNull(w, "linkCount"));
        item.put("examples", examples(w.path("examples")));
        return item;
    }

    private List<Map<String, Object>> examples(JsonNode examples) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!examples.isArray()) {
            return result;
        }
        for (JsonNode example : examples) {
            Map<String, Object> item = new LinkedHashMap<>();
            example.fields().forEachRemaining(entry -> item.put(entry.getKey(), scalar(entry.getValue())));
            result.add(item);
        }
        return result;
    }

    private Object scalar(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private Integer integerOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asInt();
    }

    private String phonetic(JsonNode word) {
        String phonetic = word.path("phonetic").asText("");
        if (!phonetic.isBlank()) {
            return phonetic;
        }

        String phoneticUk = word.path("phoneticUk").asText("");
        String phoneticUs = word.path("phoneticUs").asText("");
        if (!phoneticUk.isBlank() && !phoneticUs.isBlank()) {
            return "UK " + phoneticUk + " / US " + phoneticUs;
        }
        if (!phoneticUk.isBlank()) {
            return "UK " + phoneticUk;
        }
        if (!phoneticUs.isBlank()) {
            return "US " + phoneticUs;
        }
        return "";
    }
}
