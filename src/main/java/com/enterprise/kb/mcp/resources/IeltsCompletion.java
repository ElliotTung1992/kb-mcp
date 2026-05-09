package com.enterprise.kb.mcp.resources;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MCP Completions：为 Resource Template 和 Prompt 参数提供候选值。
 */
@Component
@RequiredArgsConstructor
public class IeltsCompletion {

    private static final int LIMIT = 20;

    private final IeltsApiClient ieltsApiClient;

    public McpServerFeatures.SyncCompletionSpecification wordIdCompletionSpec() {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.ResourceReference("ielts://words/{wordId}"),
                this::completeWordId
        );
    }

    public McpServerFeatures.SyncCompletionSpecification writingTaskIdCompletionSpec() {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.ResourceReference("ielts://writing-tasks/{taskId}"),
                this::completeWritingTaskId
        );
    }

    public McpServerFeatures.SyncCompletionSpecification speakingTopicIdCompletionSpec() {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.ResourceReference("ielts://speaking-topics/{topicId}"),
                this::completeSpeakingTopicId
        );
    }

    public McpServerFeatures.SyncCompletionSpecification studyRecordStatusCompletionSpec() {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.ResourceReference("ielts://study/records/{status}"),
                this::completeStudyRecordStatus
        );
    }

    public McpServerFeatures.SyncCompletionSpecification grammarPointIdCompletionSpec() {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.ResourceReference("ielts://grammar-points/{grammarPointId}"),
                this::completeGrammarPointId
        );
    }

    public McpServerFeatures.SyncCompletionSpecification dailyCoachPromptCompletionSpec() {
        return promptCompletion("ielts_daily_coach");
    }

    public McpServerFeatures.SyncCompletionSpecification wordDrillPromptCompletionSpec() {
        return promptCompletion("ielts_word_drill");
    }

    public McpServerFeatures.SyncCompletionSpecification reviewSessionPromptCompletionSpec() {
        return promptCompletion("ielts_review_session");
    }

    public McpServerFeatures.SyncCompletionSpecification writingPracticePromptCompletionSpec() {
        return promptCompletion("ielts_writing_practice");
    }

    public McpServerFeatures.SyncCompletionSpecification speakingPracticePromptCompletionSpec() {
        return promptCompletion("ielts_speaking_practice");
    }

    public McpServerFeatures.SyncCompletionSpecification weaknessDiagnosisPromptCompletionSpec() {
        return promptCompletion("ielts_weakness_diagnosis");
    }

    public McpServerFeatures.SyncCompletionSpecification addWordToTodayPlanPromptCompletionSpec() {
        return promptCompletion("ielts_add_word_to_today_plan");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncWordIdCompletionSpec() {
        return asyncResourceCompletion("ielts://words/{wordId}", this::completeWordId);
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncWritingTaskIdCompletionSpec() {
        return asyncResourceCompletion("ielts://writing-tasks/{taskId}", this::completeWritingTaskId);
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncSpeakingTopicIdCompletionSpec() {
        return asyncResourceCompletion("ielts://speaking-topics/{topicId}", this::completeSpeakingTopicId);
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncStudyRecordStatusCompletionSpec() {
        return asyncResourceCompletion("ielts://study/records/{status}", this::completeStudyRecordStatus);
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncGrammarPointIdCompletionSpec() {
        return asyncResourceCompletion("ielts://grammar-points/{grammarPointId}", this::completeGrammarPointId);
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncDailyCoachPromptCompletionSpec() {
        return asyncPromptCompletion("ielts_daily_coach");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncWordDrillPromptCompletionSpec() {
        return asyncPromptCompletion("ielts_word_drill");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncReviewSessionPromptCompletionSpec() {
        return asyncPromptCompletion("ielts_review_session");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncWritingPracticePromptCompletionSpec() {
        return asyncPromptCompletion("ielts_writing_practice");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncSpeakingPracticePromptCompletionSpec() {
        return asyncPromptCompletion("ielts_speaking_practice");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncWeaknessDiagnosisPromptCompletionSpec() {
        return asyncPromptCompletion("ielts_weakness_diagnosis");
    }

    public McpServerFeatures.AsyncCompletionSpecification asyncAddWordToTodayPlanPromptCompletionSpec() {
        return asyncPromptCompletion("ielts_add_word_to_today_plan");
    }

    private McpSchema.CompleteResult completeWordId(McpSyncServerExchange exchange,
                                                    McpSchema.CompleteRequest request) {
        String keyword = normalizedValue(request);
        JsonNode resp = ieltsApiClient.listWords(null, null, null, keyword, 1, LIMIT);
        return completeIds(resp, keyword, "id", "word", "definitionZh", "definitionEn");
    }

    private McpSchema.CompleteResult completeWritingTaskId(McpSyncServerExchange exchange,
                                                           McpSchema.CompleteRequest request) {
        String keyword = normalizedValue(request);
        JsonNode resp = ieltsApiClient.listWritingTasks(null, null, 1, LIMIT);
        return completeIds(resp, keyword, "id", "title", "prompt", "question");
    }

    private McpSchema.CompleteResult completeSpeakingTopicId(McpSyncServerExchange exchange,
                                                             McpSchema.CompleteRequest request) {
        String keyword = normalizedValue(request);
        JsonNode resp = ieltsApiClient.listSpeakingTopics(null, null, null, 1, LIMIT);
        return completeIds(resp, keyword, "id", "title", "topic", "question");
    }

    private McpSchema.CompleteResult completeStudyRecordStatus(McpSyncServerExchange exchange,
                                                               McpSchema.CompleteRequest request) {
        String keyword = normalizedValue(request).toUpperCase(Locale.ROOT);
        return completeValues(List.of("LEARNING", "REVIEWING", "MASTERED").stream()
                .filter(status -> keyword.isBlank() || status.startsWith(keyword))
                .toList());
    }

    private McpSchema.CompleteResult completeGrammarPointId(McpSyncServerExchange exchange,
                                                            McpSchema.CompleteRequest request) {
        String keyword = normalizedValue(request);
        JsonNode resp = ieltsApiClient.listGrammarPoints(null, null, null, keyword, 1, LIMIT);
        return completeIds(resp, keyword, "id", "title", "name", "category");
    }

    private McpServerFeatures.SyncCompletionSpecification promptCompletion(String promptName) {
        return new McpServerFeatures.SyncCompletionSpecification(
                new McpSchema.PromptReference(promptName),
                this::completePromptArgument
        );
    }

    private McpServerFeatures.AsyncCompletionSpecification asyncResourceCompletion(String uri,
                                                                                   CompletionHandler handler) {
        return new McpServerFeatures.AsyncCompletionSpecification(
                new McpSchema.ResourceReference(uri),
                (McpAsyncServerExchange exchange, McpSchema.CompleteRequest request) -> Mono.fromSupplier(() -> handler.complete(null, request))
        );
    }

    private McpServerFeatures.AsyncCompletionSpecification asyncPromptCompletion(String promptName) {
        return new McpServerFeatures.AsyncCompletionSpecification(
                new McpSchema.PromptReference(promptName),
                (exchange, request) -> Mono.fromSupplier(() -> completePromptArgument(null, request))
        );
    }

    private McpSchema.CompleteResult completePromptArgument(McpSyncServerExchange exchange,
                                                            McpSchema.CompleteRequest request) {
        String argumentName = request.argument() == null ? "" : request.argument().name();
        String keyword = normalizedValue(request).toLowerCase(Locale.ROOT);
        return switch (argumentName) {
            case "focusSkill" -> completeByPrefix(keyword,
                    "listening", "reading", "writing", "speaking", "vocabulary", "grammar");
            case "difficulty" -> completeByPrefix(keyword, "1", "2", "3");
            case "contentType" -> completeByPrefix(keyword,
                    "WORD", "PHRASE", "GRAMMAR_POINT", "WRITING_TASK", "SPEAKING_TOPIC");
            case "taskType" -> completeByPrefix(keyword, "1", "2");
            case "part" -> completeByPrefix(keyword, "1", "2", "3");
            case "targetBand" -> completeByPrefix(keyword, "5.5", "6.0", "6.5", "7.0", "7.5", "8.0");
            case "count" -> completeByPrefix(keyword, "5", "10", "15", "20");
            case "durationMinutes", "availableMinutes" -> completeByPrefix(keyword, "15", "30", "45", "60", "90");
            case "days" -> completeByPrefix(keyword, "7", "14", "30", "60");
            default -> completeValues(List.of());
        };
    }

    private McpSchema.CompleteResult completeIds(JsonNode resp, String keyword, String idField, String... searchableFields) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        List<String> values = new ArrayList<>();
        JsonNode content = resp.path("data").path("content");
        if (!content.isArray()) {
            return completeValues(values);
        }
        for (JsonNode item : content) {
            String id = item.path(idField).asText("");
            if (id.isBlank()) {
                continue;
            }
            if (normalizedKeyword.isBlank() || id.toLowerCase(Locale.ROOT).startsWith(normalizedKeyword)
                    || containsAny(item, normalizedKeyword, searchableFields)) {
                values.add(id);
            }
            if (values.size() >= LIMIT) {
                break;
            }
        }
        return completeValues(values);
    }

    private boolean containsAny(JsonNode item, String keyword, String... fields) {
        for (String field : fields) {
            if (item.path(field).asText("").toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private McpSchema.CompleteResult completeValues(List<String> values) {
        return new McpSchema.CompleteResult(
                new McpSchema.CompleteResult.CompleteCompletion(values, values.size(), false)
        );
    }

    private McpSchema.CompleteResult completeByPrefix(String keyword, String... candidates) {
        return completeValues(List.of(candidates).stream()
                .filter(candidate -> keyword.isBlank() || candidate.toLowerCase(Locale.ROOT).startsWith(keyword))
                .toList());
    }

    private String normalizedValue(McpSchema.CompleteRequest request) {
        String value = request.argument() == null ? "" : request.argument().value();
        if (value == null || "`".equals(value)) {
            return "";
        }
        return value.trim();
    }

    @FunctionalInterface
    private interface CompletionHandler {
        McpSchema.CompleteResult complete(McpSyncServerExchange exchange, McpSchema.CompleteRequest request);
    }
}
