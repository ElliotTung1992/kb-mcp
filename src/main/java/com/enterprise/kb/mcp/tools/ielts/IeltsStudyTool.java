package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IeltsStudyTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_get_today_plan",
          description = "获取今日雅思学习计划，包含计划总数、已完成数和待复习内容列表（含内容类型和状态）")
    public String getTodayPlan() {
        JsonNode resp = ieltsApiClient.getTodayPlan();
        JsonNode data = resp.path("data");

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
        return sb.toString().trim();
    }

    @Tool(name = "ielts_get_study_stats",
          description = "获取雅思学习统计数据，包含学习中/复习中/已掌握数量、今日复习次数、连续学习天数")
    public String getStats() {
        JsonNode resp = ieltsApiClient.getStats();
        JsonNode data = resp.path("data");

        // 状态说明：LEARNING=学习中（初次接触）/ REVIEWING=复习中（间隔复习）/ MASTERED=已掌握
        return "学习统计：\n"
                + "总记录数：" + data.path("totalRecords").asLong(0) + " 项\n"
                + "学习中（LEARNING）：" + data.path("learningCount").asLong(0) + " 项\n"
                + "复习中（REVIEWING）：" + data.path("reviewingCount").asLong(0) + " 项\n"
                + "已掌握（MASTERED）：" + data.path("masteredCount").asLong(0) + " 项\n"
                + "今日已复习：" + data.path("todayReviews").asLong(0) + " 次\n"
                + "连续学习天数：" + data.path("streakDays").asInt(0) + " 天";
    }
}
