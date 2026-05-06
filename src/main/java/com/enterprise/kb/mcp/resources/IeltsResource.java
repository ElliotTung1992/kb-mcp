package com.enterprise.kb.mcp.resources;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Resources：{@code ielts://today/plan} 和 {@code ielts://study/stats}
 *
 * <p>两者均为固定 URI，无需参数，AI 可直接读取。
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
}
