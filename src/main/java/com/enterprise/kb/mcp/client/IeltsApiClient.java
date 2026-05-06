package com.enterprise.kb.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * kb-ielts REST API 的 HTTP 客户端。
 * kb-ielts 无需认证，直接调用即可。
 */
@Slf4j
@Component
public class IeltsApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IeltsApiClient(@Value("${ielts.app.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── 单词 ──────────────────────────────────────────────────────────────────

    public JsonNode listWords(Integer difficulty, String wordList, String topicTags, int page, int size) {
        String uri = buildUri("/api/ielts/words", "difficulty", difficulty,
                "wordList", wordList, "topicTags", topicTags, "page", page, "size", size);
        return get(uri);
    }

    public JsonNode createWord(Object word) {
        return post("/api/ielts/words", word);
    }

    public JsonNode batchImportWords(Object words) {
        return post("/api/ielts/words/batch", words);
    }

    // ── 短语 ──────────────────────────────────────────────────────────────────

    public JsonNode listPhrases(Integer difficulty, String topicTags, int page, int size) {
        String uri = buildUri("/api/ielts/phrases", "difficulty", difficulty,
                "topicTags", topicTags, "page", page, "size", size);
        return get(uri);
    }

    // ── 语法 ──────────────────────────────────────────────────────────────────

    public JsonNode listGrammarPoints(Integer difficulty, String category, String topicTags, int page, int size) {
        String uri = buildUri("/api/ielts/grammar-points", "difficulty", difficulty,
                "category", category, "topicTags", topicTags, "page", page, "size", size);
        return get(uri);
    }

    // ── 口语 ──────────────────────────────────────────────────────────────────

    public JsonNode listSpeakingTopics(Integer difficulty, Integer part, String topicTags, int page, int size) {
        String uri = buildUri("/api/ielts/speaking-topics", "difficulty", difficulty,
                "part", part, "topicTags", topicTags, "page", page, "size", size);
        return get(uri);
    }

    // ── 写作 ──────────────────────────────────────────────────────────────────

    public JsonNode listWritingTasks(Integer difficulty, Integer taskType, int page, int size) {
        String uri = buildUri("/api/ielts/writing-tasks", "difficulty", difficulty,
                "taskType", taskType, "page", page, "size", size);
        return get(uri);
    }

    // ── 学习计划 ──────────────────────────────────────────────────────────────

    public JsonNode getTodayPlan() {
        return get("/api/ielts/study/today");
    }

    public JsonNode getStats() {
        return get("/api/ielts/study/stats");
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    private JsonNode get(String uri) {
        try {
            String raw = restClient.get().uri(uri).retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("GET {} failed: {}", uri, e.getMessage());
            throw new IeltsApiException("Failed to call kb-ielts: " + e.getMessage(), e);
        }
    }

    private JsonNode post(String uri, Object body) {
        try {
            String raw = restClient.post().uri(uri).body(body).retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("POST {} failed: {}", uri, e.getMessage());
            throw new IeltsApiException("Failed to call kb-ielts: " + e.getMessage(), e);
        }
    }

    /** 拼接查询参数，跳过 null 值 */
    private String buildUri(String base, Object... kvPairs) {
        StringBuilder sb = new StringBuilder(base).append("?");
        for (int i = 0; i < kvPairs.length; i += 2) {
            Object val = kvPairs[i + 1];
            if (val != null) {
                sb.append(kvPairs[i]).append("=").append(val).append("&");
            }
        }
        return sb.toString().replaceAll("[&?]$", "");
    }

    public static class IeltsApiException extends RuntimeException {
        public IeltsApiException(String message, Throwable cause) { super(message, cause); }
    }
}
