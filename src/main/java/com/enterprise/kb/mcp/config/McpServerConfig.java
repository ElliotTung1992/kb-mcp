package com.enterprise.kb.mcp.config;

import com.enterprise.kb.mcp.prompts.IeltsPrompt;
import com.enterprise.kb.mcp.resources.IeltsCompletion;
import com.enterprise.kb.mcp.resources.IeltsResource;
import com.enterprise.kb.mcp.tools.ielts.IeltsContentTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsStudyTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsTrainingTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsWordTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 注册所有 MCP Tools、Resources、Resource Templates 和 Prompts。
 */
@Configuration
public class McpServerConfig {

    // ── Tools ────────────────────────────────────────────────────────────────

    @Bean
    public ToolCallbackProvider ieltsTools(
            IeltsWordTool wordTool,
            IeltsStudyTool studyTool,
            IeltsContentTool contentTool,
            IeltsTrainingTool trainingTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(wordTool, studyTool, contentTool, trainingTool)
                .build();
    }

    // ── Resources ────────────────────────────────────────────────────────────

    /**
     * 注册雅思相关 MCP Resources：
     * <ul>
     *   <li>{@code ielts://today/plan} — 今日学习计划</li>
     *   <li>{@code ielts://study/stats} — 学习统计</li>
     *   <li>{@code ielts://profile/summary} — 学习档案摘要</li>
     *   <li>{@code ielts://dashboard/overview} — 学习仪表盘概览</li>
     *   <li>{@code ielts://mistakes/recent} — 最近错题</li>
     *   <li>{@code ielts://mock-tests/trends} — 模考趋势</li>
     *   <li>{@code ielts://topic-tags} — 话题标签</li>
     *   <li>{@code ielts://words/{wordId}} — 单词详情模板</li>
     *   <li>{@code ielts://writing-tasks/{taskId}} — 写作题详情模板</li>
     *   <li>{@code ielts://speaking-topics/{topicId}} — 口语话题详情模板</li>
     *   <li>{@code ielts://study/records/{status}} — 学习记录状态模板</li>
     *   <li>{@code ielts://grammar-points/{grammarPointId}} — 语法点详情模板</li>
     * </ul>
     */
    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> mcpResources(IeltsResource ieltsResource) {
        return List.of(
                ieltsResource.todayPlanSpec(),
                ieltsResource.studyStatsSpec(),
                ieltsResource.profileSummarySpec(),
                ieltsResource.dashboardOverviewSpec(),
                ieltsResource.recentMistakesSpec(),
                ieltsResource.mockTestTrendsSpec(),
                ieltsResource.topicTagsSpec()
        );
    }

    // ── Resource Templates ──────────────────────────────────────────────────

    @Bean
    public List<McpServerFeatures.SyncResourceTemplateSpecification> mcpResourceTemplates(IeltsResource ieltsResource) {
        return List.of(
                ieltsResource.wordTemplateSpec(),
                ieltsResource.writingTaskTemplateSpec(),
                ieltsResource.speakingTopicTemplateSpec(),
                ieltsResource.studyRecordsTemplateSpec(),
                ieltsResource.grammarPointTemplateSpec()
        );
    }

    // ── Completions ─────────────────────────────────────────────────────────

    @Bean
    public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(IeltsCompletion ieltsCompletion) {
        return List.of(
                ieltsCompletion.wordIdCompletionSpec(),
                ieltsCompletion.writingTaskIdCompletionSpec(),
                ieltsCompletion.speakingTopicIdCompletionSpec(),
                ieltsCompletion.studyRecordStatusCompletionSpec(),
                ieltsCompletion.grammarPointIdCompletionSpec(),
                ieltsCompletion.dailyCoachPromptCompletionSpec(),
                ieltsCompletion.wordDrillPromptCompletionSpec(),
                ieltsCompletion.reviewSessionPromptCompletionSpec(),
                ieltsCompletion.writingPracticePromptCompletionSpec(),
                ieltsCompletion.speakingPracticePromptCompletionSpec(),
                ieltsCompletion.weaknessDiagnosisPromptCompletionSpec(),
                ieltsCompletion.addWordToTodayPlanPromptCompletionSpec()
        );
    }

    @Bean
    public List<McpServerFeatures.AsyncCompletionSpecification> mcpAsyncCompletions(IeltsCompletion ieltsCompletion) {
        return List.of(
                ieltsCompletion.asyncWordIdCompletionSpec(),
                ieltsCompletion.asyncWritingTaskIdCompletionSpec(),
                ieltsCompletion.asyncSpeakingTopicIdCompletionSpec(),
                ieltsCompletion.asyncStudyRecordStatusCompletionSpec(),
                ieltsCompletion.asyncGrammarPointIdCompletionSpec(),
                ieltsCompletion.asyncDailyCoachPromptCompletionSpec(),
                ieltsCompletion.asyncWordDrillPromptCompletionSpec(),
                ieltsCompletion.asyncReviewSessionPromptCompletionSpec(),
                ieltsCompletion.asyncWritingPracticePromptCompletionSpec(),
                ieltsCompletion.asyncSpeakingPracticePromptCompletionSpec(),
                ieltsCompletion.asyncWeaknessDiagnosisPromptCompletionSpec(),
                ieltsCompletion.asyncAddWordToTodayPlanPromptCompletionSpec()
        );
    }

    // ── Prompts ──────────────────────────────────────────────────────────────

    /**
     * 注册雅思学习场景 MCP Prompts。
     */
    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> mcpPrompts(IeltsPrompt ieltsPrompt) {
        return promptSpecs(ieltsPrompt);
    }

    private List<McpServerFeatures.SyncPromptSpecification> promptSpecs(IeltsPrompt ieltsPrompt) {
        return List.of(
                ieltsPrompt.dailyCoachSpec(),
                ieltsPrompt.wordDrillSpec(),
                ieltsPrompt.reviewSessionSpec(),
                ieltsPrompt.writingPracticeSpec(),
                ieltsPrompt.speakingPracticeSpec(),
                ieltsPrompt.weaknessDiagnosisSpec(),
                ieltsPrompt.addWordToTodayPlanSpec()
        );
    }
}
