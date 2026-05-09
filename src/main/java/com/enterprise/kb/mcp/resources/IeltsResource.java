package com.enterprise.kb.mcp.resources;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.enterprise.kb.mcp.client.IeltsApiClient.IeltsApiException;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * MCP Resources：雅思学习上下文。
 *
 * <p>均为固定 URI，无需参数，AI 可直接读取。
 * 适合作为雅思学习会话的背景上下文，AI 先了解当前进度和计划，再提供针对性的学习建议。
 */
@Component
@RequiredArgsConstructor
public class IeltsResource {

    private final IeltsApiClient ieltsApiClient;

    public McpServerFeatures.SyncResourceSpecification todayPlanSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://today/plan",
                "ielts-today-plan",
                "今日雅思学习计划，包含计划总数、已完成数和待复习内容列表",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readTodayPlan);
    }

    public McpServerFeatures.SyncResourceSpecification studyStatsSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://study/stats",
                "ielts-study-stats",
                "雅思学习统计：学习中/复习中/已掌握数量、今日复习次数、连续学习天数",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readStudyStats);
    }

    public McpServerFeatures.SyncResourceSpecification profileSummarySpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://profile/summary",
                "ielts-profile-summary",
                "雅思学习档案摘要：目标分数、考试日期、重点技能和备考偏好",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readProfileSummary);
    }

    public McpServerFeatures.SyncResourceSpecification dashboardOverviewSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://dashboard/overview",
                "ielts-dashboard-overview",
                "雅思学习仪表盘概览，包含学习计划、统计、薄弱项和建议",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readDashboardOverview);
    }

    public McpServerFeatures.SyncResourceSpecification recentMistakesSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://mistakes/recent",
                "ielts-recent-mistakes",
                "最近雅思错题和错因记录摘要",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readRecentMistakes);
    }

    public McpServerFeatures.SyncResourceSpecification mockTestTrendsSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://mock-tests/trends",
                "ielts-mock-test-trends",
                "雅思模考整体和分项分数趋势",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readMockTestTrends);
    }

    public McpServerFeatures.SyncResourceSpecification topicTagsSpec() {
        McpSchema.Resource resource = new McpSchema.Resource(
                "ielts://topic-tags",
                "ielts-topic-tags",
                "雅思内容话题标签列表，用于辅助筛选单词、题目和训练内容",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceSpecification(resource, this::readTopicTags);
    }

    public McpServerFeatures.SyncResourceTemplateSpecification wordTemplateSpec() {
        McpSchema.ResourceTemplate resource = new McpSchema.ResourceTemplate(
                "ielts://words/{wordId}",
                "ielts-word-detail",
                "雅思单词详情 Resource Template，通过 wordId(UUID) 读取单词完整信息",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceTemplateSpecification(resource, this::readWordDetail);
    }

    public McpServerFeatures.SyncResourceTemplateSpecification writingTaskTemplateSpec() {
        McpSchema.ResourceTemplate resource = new McpSchema.ResourceTemplate(
                "ielts://writing-tasks/{taskId}",
                "ielts-writing-task-detail",
                "雅思写作题详情 Resource Template，通过 taskId(UUID) 读取写作题完整信息",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceTemplateSpecification(resource, this::readWritingTaskDetail);
    }

    public McpServerFeatures.SyncResourceTemplateSpecification speakingTopicTemplateSpec() {
        McpSchema.ResourceTemplate resource = new McpSchema.ResourceTemplate(
                "ielts://speaking-topics/{topicId}",
                "ielts-speaking-topic-detail",
                "雅思口语话题详情 Resource Template，通过 topicId(UUID) 读取口语话题完整信息",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceTemplateSpecification(resource, this::readSpeakingTopicDetail);
    }

    public McpServerFeatures.SyncResourceTemplateSpecification studyRecordsTemplateSpec() {
        McpSchema.ResourceTemplate resource = new McpSchema.ResourceTemplate(
                "ielts://study/records/{status}",
                "ielts-study-records-by-status",
                "雅思学习记录 Resource Template，通过状态读取 LEARNING / REVIEWING / MASTERED 记录",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceTemplateSpecification(resource, this::readStudyRecordsByStatus);
    }

    public McpServerFeatures.SyncResourceTemplateSpecification grammarPointTemplateSpec() {
        McpSchema.ResourceTemplate resource = new McpSchema.ResourceTemplate(
                "ielts://grammar-points/{grammarPointId}",
                "ielts-grammar-point-detail",
                "雅思语法点详情 Resource Template，通过 grammarPointId(UUID) 读取语法点完整信息",
                "text/plain",
                null
        );
        return new McpServerFeatures.SyncResourceTemplateSpecification(resource, this::readGrammarPointDetail);
    }

    private McpSchema.ReadResourceResult readTodayPlan(McpSyncServerExchange exchange,
                                                        McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getTodayPlan().path("data");

        int total = data.path("totalItems").asInt(0);
        int completed = data.path("completedItems").asInt(0);

        StringBuilder sb = new StringBuilder("今日学习计划（").append(data.path("planDate").asText()).append("）：\n\n");
        sb.append("计划总数：").append(total).append(" 项\n");
        sb.append("已完成：").append(completed).append(" 项\n");
        sb.append("剩余：").append(total - completed).append(" 项\n");

        JsonNode dueItems = data.path("dueItems");
        if (!dueItems.isEmpty()) {
            sb.append("\n待处理内容：\n");
            for (JsonNode item : dueItems) {
                sb.append("- [").append(item.path("contentType").asText()).append("] ")
                  .append("状态：").append(item.path("status").asText())
                  .append("，下次复习：").append(item.path("nextReviewAt").asText("今日")).append("\n");
            }
        }

        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(request.uri(), "text/plain", sb.toString().trim()))
        );
    }

    private McpSchema.ReadResourceResult readStudyStats(McpSyncServerExchange exchange,
                                                         McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getStats().path("data");

        String text = "学习统计：\n"
                + "总记录数：" + data.path("totalRecords").asLong(0) + " 项\n"
                + "学习中：" + data.path("learningCount").asLong(0) + " 项\n"
                + "复习中：" + data.path("reviewingCount").asLong(0) + " 项\n"
                + "已掌握：" + data.path("masteredCount").asLong(0) + " 项\n"
                + "今日已复习：" + data.path("todayReviews").asLong(0) + " 次\n"
                + "连续学习天数：" + data.path("streakDays").asInt(0) + " 天";

        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(request.uri(), "text/plain", text))
        );
    }

    private McpSchema.ReadResourceResult readProfileSummary(McpSyncServerExchange exchange,
                                                            McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getProfile().path("data");
        return textResult(request, pretty("学习档案摘要", data));
    }

    private McpSchema.ReadResourceResult readDashboardOverview(McpSyncServerExchange exchange,
                                                               McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getDashboard().path("data");
        return textResult(request, pretty("学习仪表盘概览", data));
    }

    private McpSchema.ReadResourceResult readRecentMistakes(McpSyncServerExchange exchange,
                                                            McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getRecentMistakes(10).path("data");
        return textResult(request, pretty("最近错题记录", data));
    }

    private McpSchema.ReadResourceResult readMockTestTrends(McpSyncServerExchange exchange,
                                                            McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.getMockTestTrends().path("data");
        return textResult(request, pretty("模考趋势", data));
    }

    private McpSchema.ReadResourceResult readTopicTags(McpSyncServerExchange exchange,
                                                       McpSchema.ReadResourceRequest request) {
        JsonNode data = ieltsApiClient.listTopicTags(null, null, null, true, 100).path("data");
        return textResult(request, pretty("话题标签", data));
    }

    private McpSchema.ReadResourceResult readWordDetail(McpSyncServerExchange exchange,
                                                        McpSchema.ReadResourceRequest request) {
        String wordId = uuidPathVariable(request.uri(), "ielts://words/", "wordId");
        return readDetail(request, "单词详情（" + wordId + "）", () -> ieltsApiClient.getWord(wordId).path("data"));
    }

    private McpSchema.ReadResourceResult readWritingTaskDetail(McpSyncServerExchange exchange,
                                                               McpSchema.ReadResourceRequest request) {
        String taskId = uuidPathVariable(request.uri(), "ielts://writing-tasks/", "taskId");
        return readDetail(request, "写作题详情（" + taskId + "）", () -> ieltsApiClient.getWritingTask(taskId).path("data"));
    }

    private McpSchema.ReadResourceResult readSpeakingTopicDetail(McpSyncServerExchange exchange,
                                                                 McpSchema.ReadResourceRequest request) {
        String topicId = uuidPathVariable(request.uri(), "ielts://speaking-topics/", "topicId");
        return readDetail(request, "口语话题详情（" + topicId + "）", () -> ieltsApiClient.getSpeakingTopic(topicId).path("data"));
    }

    private McpSchema.ReadResourceResult readStudyRecordsByStatus(McpSyncServerExchange exchange,
                                                                   McpSchema.ReadResourceRequest request) {
        String status = pathVariable(request.uri(), "ielts://study/records/").toUpperCase();
        JsonNode data = ieltsApiClient.getStudyRecords(status).path("data");
        return textResult(request, pretty("学习记录（" + status + "）", data));
    }

    private McpSchema.ReadResourceResult readGrammarPointDetail(McpSyncServerExchange exchange,
                                                                McpSchema.ReadResourceRequest request) {
        String grammarPointId = uuidPathVariable(request.uri(), "ielts://grammar-points/", "grammarPointId");
        return readDetail(request, "语法点详情（" + grammarPointId + "）", () -> ieltsApiClient.getGrammarPoint(grammarPointId).path("data"));
    }

    private McpSchema.ReadResourceResult textResult(McpSchema.ReadResourceRequest request, String text) {
        return new McpSchema.ReadResourceResult(
                List.of(new McpSchema.TextResourceContents(request.uri(), "text/plain", text))
        );
    }

    private String pretty(String title, JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull() || data.isEmpty()) {
            return title + "：暂无数据";
        }
        return title + "：\n" + data.toPrettyString();
    }

    private String pathVariable(String uri, String prefix) {
        if (!uri.startsWith(prefix) || uri.length() == prefix.length()) {
            throw new IllegalArgumentException("Invalid IELTS resource URI: " + uri);
        }
        return URLDecoder.decode(uri.substring(prefix.length()), StandardCharsets.UTF_8);
    }

    private String uuidPathVariable(String uri, String prefix, String argumentName) {
        String value = pathVariable(uri, prefix);
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(argumentName + " must be a UUID, but got: " + value);
        }
    }

    private McpSchema.ReadResourceResult readDetail(McpSchema.ReadResourceRequest request,
                                                    String title,
                                                    DetailLoader loader) {
        try {
            return textResult(request, pretty(title, loader.load()));
        } catch (IeltsApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return textResult(request, title + "：未找到，请确认 Resource Template 参数使用的是业务数据 UUID。");
            }
            throw e;
        }
    }

    @FunctionalInterface
    private interface DetailLoader {
        JsonNode load();
    }
}
