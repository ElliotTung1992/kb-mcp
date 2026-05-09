package com.enterprise.kb.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

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

    public JsonNode listWords(Integer difficulty, String wordList, String topicTags, String keyword, int page, int size) {
        String uri = buildUri("/api/ielts/words", "difficulty", difficulty,
                "wordList", wordList, "topicTags", topicTags, "keyword", keyword, "page", page, "size", size);
        return get(uri);
    }

    public JsonNode getWord(String wordId) {
        return get(buildPath("/api/ielts/words", wordId));
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

    public JsonNode listGrammarPoints(Integer difficulty, String category, String topicTags, String keyword,
                                      int page, int size) {
        String uri = buildUri("/api/ielts/grammar-points", "difficulty", difficulty,
                "category", category, "topicTags", topicTags, "keyword", keyword, "page", page, "size", size);
        return get(uri);
    }

    public JsonNode getGrammarPoint(String grammarPointId) {
        return get(buildPath("/api/ielts/grammar-points", grammarPointId));
    }

    // ── 口语 ──────────────────────────────────────────────────────────────────

    public JsonNode listSpeakingTopics(Integer difficulty, Integer part, String topicTags, int page, int size) {
        String uri = buildUri("/api/ielts/speaking-topics", "difficulty", difficulty,
                "part", part, "topicTags", topicTags, "page", page, "size", size);
        return get(uri);
    }

    public JsonNode getSpeakingTopic(String topicId) {
        return get(buildPath("/api/ielts/speaking-topics", topicId));
    }

    // ── 写作 ──────────────────────────────────────────────────────────────────

    public JsonNode listWritingTasks(Integer difficulty, Integer taskType, int page, int size) {
        String uri = buildUri("/api/ielts/writing-tasks", "difficulty", difficulty,
                "taskType", taskType, "page", page, "size", size);
        return get(uri);
    }

    public JsonNode getWritingTask(String taskId) {
        return get(buildPath("/api/ielts/writing-tasks", taskId));
    }

    // ── 学习计划 ──────────────────────────────────────────────────────────────

    public JsonNode getTodayPlan() {
        return get("/api/ielts/study/today");
    }

    public JsonNode getStats() {
        return get("/api/ielts/study/stats");
    }

    public JsonNode getStudyRecords(String status) {
        return get(buildUri("/api/ielts/study/records", "status", status));
    }

    public JsonNode startStudy(Object request) {
        return post("/api/ielts/study/start", request);
    }

    public JsonNode addToTodayPlan(Object request) {
        return post("/api/ielts/study/today/items", request);
    }

    public JsonNode submitReview(Object request) {
        return post("/api/ielts/study/review", request);
    }

    // ── 学习档案 / 仪表盘 / 错题 ────────────────────────────────────────────────

    public JsonNode getProfile() {
        return get("/api/ielts/profile");
    }

    public JsonNode getPlanSuggestion() {
        return get("/api/ielts/profile/plan-suggestion");
    }

    public JsonNode getDashboard() {
        return get("/api/ielts/dashboard");
    }

    public JsonNode getRecentMistakes(Integer limit) {
        return get(buildUri("/api/ielts/mistakes/recent", "limit", limit));
    }

    public JsonNode getMistakeStats(Integer days, Integer limit) {
        return get(buildUri("/api/ielts/mistakes/stats", "days", days, "limit", limit));
    }

    // ── 训练内容 ──────────────────────────────────────────────────────────────

    public JsonNode listListeningItems(Integer difficulty, Integer section, String questionType,
                                       String topicTags, String studyStatus, int page, int size) {
        return get(buildUri("/api/ielts/listening-items",
                "difficulty", difficulty, "section", section, "questionType", questionType,
                "topicTags", topicTags, "studyStatus", studyStatus, "page", page, "size", size));
    }

    public JsonNode listReadingItems(Integer difficulty, String trainingType, String questionType,
                                     String topicTags, String studyStatus, int page, int size) {
        return get(buildUri("/api/ielts/reading-items",
                "difficulty", difficulty, "trainingType", trainingType, "questionType", questionType,
                "topicTags", topicTags, "studyStatus", studyStatus, "page", page, "size", size));
    }

    public JsonNode listPronunciationPoints(Integer difficulty, String category, String studyStatus,
                                            String keyword, int page, int size) {
        return get(buildUri("/api/ielts/pronunciation-points",
                "difficulty", difficulty, "category", category, "studyStatus", studyStatus,
                "keyword", keyword, "page", page, "size", size));
    }

    public JsonNode listParaphraseGroups(Integer difficulty, String topicTags, String studyStatus,
                                         String keyword, int page, int size) {
        return get(buildUri("/api/ielts/paraphrase-groups",
                "difficulty", difficulty, "topicTags", topicTags, "studyStatus", studyStatus,
                "keyword", keyword, "page", page, "size", size));
    }

    public JsonNode listGrammarExercises(Integer difficulty, String questionType, String grammarPointId,
                                         String studyStatus, int page, int size) {
        return get(buildUri("/api/ielts/grammar-exercises",
                "difficulty", difficulty, "questionType", questionType, "grammarPointId", grammarPointId,
                "studyStatus", studyStatus, "page", page, "size", size));
    }

    public JsonNode getTrainingListening(Integer section, String questionType, Integer difficulty, int page, int size) {
        return get(buildUri("/api/ielts/training/listening",
                "section", section, "questionType", questionType, "difficulty", difficulty,
                "page", page, "size", size));
    }

    public JsonNode getTrainingReading(String questionType, Integer difficulty, int page, int size) {
        return get(buildUri("/api/ielts/training/reading",
                "questionType", questionType, "difficulty", difficulty, "page", page, "size", size));
    }

    public JsonNode getTrainingWriting(Integer taskNumber, Integer difficulty, int page, int size) {
        return get(buildUri("/api/ielts/training/writing",
                "taskNumber", taskNumber, "difficulty", difficulty, "page", page, "size", size));
    }

    public JsonNode getTrainingSpeaking(Integer part, Integer difficulty, int page, int size) {
        return get(buildUri("/api/ielts/training/speaking",
                "part", part, "difficulty", difficulty, "page", page, "size", size));
    }

    // ── 写作 / 模考 / 标签 ─────────────────────────────────────────────────────

    public JsonNode listWritingSubmissions(Integer limit) {
        return get(buildUri("/api/ielts/writing-submissions", "limit", limit));
    }

    public JsonNode submitWriting(Object submission) {
        return post("/api/ielts/writing-submissions", submission);
    }

    public JsonNode listMockTests(Integer limit) {
        return get(buildUri("/api/ielts/mock-tests", "limit", limit));
    }

    public JsonNode createMockTest(Object mockTest) {
        return post("/api/ielts/mock-tests", mockTest);
    }

    public JsonNode getMockTestTrends() {
        return get("/api/ielts/mock-tests/trends");
    }

    public JsonNode listTopicTags(String keyword, String category, String skill, Boolean enabled, Integer limit) {
        return get(buildUri("/api/ielts/topic-tags",
                "keyword", keyword, "category", category, "skill", skill,
                "enabled", enabled, "limit", limit));
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
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(base);
        for (int i = 0; i < kvPairs.length; i += 2) {
            Object val = kvPairs[i + 1];
            if (val != null) {
                builder.queryParam(String.valueOf(kvPairs[i]), val);
            }
        }
        return builder.build().encode().toUriString();
    }

    private String buildPath(String base, String pathSegment) {
        return UriComponentsBuilder.fromPath(base)
                .pathSegment(pathSegment)
                .build()
                .encode()
                .toUriString();
    }

    public static class IeltsApiException extends RuntimeException {
        public IeltsApiException(String message, Throwable cause) { super(message, cause); }
    }
}
