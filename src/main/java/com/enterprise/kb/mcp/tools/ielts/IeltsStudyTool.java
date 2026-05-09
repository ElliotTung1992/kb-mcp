package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IeltsStudyTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_get_today_plan",
          description = "获取今日雅思学习计划，包含计划总数、已完成数和待复习内容列表（含内容类型和状态）")
    public Map<String, Object> getTodayPlan() {
        JsonNode resp = ieltsApiClient.getTodayPlan();
        JsonNode data = resp.path("data");

        int total = data.path("totalItems").asInt(0);
        int completed = data.path("completedItems").asInt(0);

        Map<String, Object> result = IeltsToolResponses.data("今日学习计划", resp);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("planDate", data.path("planDate").asText(null));
        summary.put("totalItems", total);
        summary.put("completedItems", completed);
        summary.put("remainingItems", Math.max(total - completed, 0));
        result.put("summary", summary);
        return result;
    }

    @Tool(name = "ielts_get_study_stats",
          description = "获取雅思学习统计数据，包含学习中/复习中/已掌握数量、今日复习次数、连续学习天数")
    public Map<String, Object> getStats() {
        return IeltsToolResponses.data("学习统计", ieltsApiClient.getStats());
    }

    @Tool(name = "ielts_get_profile",
          description = "获取雅思学习档案，包含目标分数、考试日期、重点技能和备考偏好")
    public Map<String, Object> getProfile() {
        return IeltsToolResponses.data("学习档案", ieltsApiClient.getProfile());
    }

    @Tool(name = "ielts_get_plan_suggestion",
          description = "获取雅思学习计划建议，适合生成当天或近期学习安排")
    public Map<String, Object> getPlanSuggestion() {
        return IeltsToolResponses.data("学习计划建议", ieltsApiClient.getPlanSuggestion());
    }

    @Tool(name = "ielts_get_dashboard",
          description = "获取雅思学习综合概览，包含计划、统计、薄弱项和推荐动作")
    public Map<String, Object> getDashboard() {
        return IeltsToolResponses.data("学习综合概览", ieltsApiClient.getDashboard());
    }

    @Tool(name = "ielts_get_study_records",
          description = "按状态查询雅思学习记录，状态为 LEARNING / REVIEWING / MASTERED")
    public Map<String, Object> getStudyRecords(
            @ToolParam(description = "学习状态：LEARNING / REVIEWING / MASTERED") String status) {
        Map<String, Object> result = IeltsToolResponses.data("学习记录", ieltsApiClient.getStudyRecords(status));
        result.put("filters", IeltsToolResponses.filters("status", status));
        return result;
    }

    @Tool(name = "ielts_start_study",
          description = "开始学习某个 IELTS 内容，系统会为该内容创建学习记录")
    public Map<String, Object> startStudy(
            @ToolParam(description = "内容类型：WORD / PHRASE / PARAPHRASE / PRONUNCIATION / GRAMMAR_POINT / GRAMMAR_EXERCISE / SPEAKING / LISTENING / READING / WRITING")
            String contentType,
            @ToolParam(description = "内容 ID，UUID 字符串") String contentId) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", contentType);
        body.put("contentId", contentId);
        Map<String, Object> result = IeltsToolResponses.data("学习记录创建结果", ieltsApiClient.startStudy(body));
        result.put("message", "学习记录已创建");
        return result;
    }

    @Tool(name = "ielts_add_to_today_plan",
          description = "把一个 IELTS 内容加入今日学习计划，常用于先创建单词，再把 WORD + 单词 ID 加入今日计划")
    public Map<String, Object> addToTodayPlan(
            @ToolParam(description = "内容类型：WORD / PHRASE / PARAPHRASE / PRONUNCIATION / GRAMMAR_POINT / GRAMMAR_EXERCISE / SPEAKING / LISTENING / READING / WRITING")
            String contentType,
            @ToolParam(description = "内容 ID，UUID 字符串") String contentId,
            @ToolParam(description = "今日计划展示摘要，如单词原形；可选", required = false) String summary) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentType", contentType);
        body.put("contentId", contentId);
        if (summary != null && !summary.isBlank()) {
            body.put("summary", summary);
        }

        Map<String, Object> result = IeltsToolResponses.data("加入今日学习计划结果", ieltsApiClient.addToTodayPlan(body));
        result.put("message", "已加入今日学习计划");
        return result;
    }

    @Tool(name = "ielts_submit_review",
          description = "提交某条学习记录的复习评分，评分为 AGAIN / HARD / GOOD / EASY")
    public Map<String, Object> submitReview(
            @ToolParam(description = "学习记录 ID，UUID 字符串") String recordId,
            @ToolParam(description = "复习评分：AGAIN / HARD / GOOD / EASY") String rating,
            @ToolParam(description = "错因类型列表，如 VOCABULARY、GRAMMAR、SPELLING", required = false)
            List<String> mistakeTypes,
            @ToolParam(description = "错因或复习备注", required = false) String note) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordId", recordId);
        body.put("rating", rating);
        if (mistakeTypes != null && !mistakeTypes.isEmpty()) {
            body.put("mistakeTypes", mistakeTypes);
        }
        if (note != null && !note.isBlank()) {
            body.put("note", note);
        }
        Map<String, Object> result = IeltsToolResponses.data("复习提交结果", ieltsApiClient.submitReview(body));
        result.put("message", "复习结果已提交");
        return result;
    }

    @Tool(name = "ielts_get_recent_mistakes",
          description = "查询最近雅思错题和错因记录")
    public Map<String, Object> getRecentMistakes(
            @ToolParam(description = "返回条数，默认 20，最大 100", required = false) Integer limit) {
        int actualLimit = limit != null ? limit : 20;
        Map<String, Object> result = IeltsToolResponses.data("最近错题", ieltsApiClient.getRecentMistakes(actualLimit));
        result.put("filters", IeltsToolResponses.filters("limit", actualLimit));
        return result;
    }

    @Tool(name = "ielts_get_mistake_stats",
          description = "查询雅思错因统计，可按最近天数和返回数量筛选")
    public Map<String, Object> getMistakeStats(
            @ToolParam(description = "最近天数，默认 30", required = false) Integer days,
            @ToolParam(description = "返回条数，默认 10，最大 50", required = false) Integer limit) {
        int actualDays = days != null ? days : 30;
        int actualLimit = limit != null ? limit : 10;
        Map<String, Object> result = IeltsToolResponses.data(
                "错因统计",
                ieltsApiClient.getMistakeStats(actualDays, actualLimit));
        result.put("filters", IeltsToolResponses.filters("days", actualDays, "limit", actualLimit));
        return result;
    }
}
