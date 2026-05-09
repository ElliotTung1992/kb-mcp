package com.enterprise.kb.mcp.prompts;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class IeltsPrompt {

    public McpServerFeatures.SyncPromptSpecification dailyCoachSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_daily_coach",
                "每日雅思学习教练：读取计划和统计后生成当天学习安排",
                List.of(
                        arg("availableMinutes", "今天可用学习时间，单位分钟", false),
                        arg("targetBand", "目标雅思分数", false),
                        arg("focusSkill", "重点技能：listening / reading / writing / speaking / vocabulary / grammar", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::dailyCoach);
    }

    public McpServerFeatures.SyncPromptSpecification wordDrillSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_word_drill",
                "雅思单词和短语训练：按难度、话题和数量组织练习",
                List.of(
                        arg("difficulty", "难度：1 / 2 / 3", false),
                        arg("topicTags", "话题标签", false),
                        arg("count", "训练数量", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::wordDrill);
    }

    public McpServerFeatures.SyncPromptSpecification reviewSessionSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_review_session",
                "雅思间隔复习会话：根据今日计划和学习记录引导复习",
                List.of(
                        arg("durationMinutes", "复习时长，单位分钟", false),
                        arg("contentType", "内容类型，如 WORD / PHRASE / GRAMMAR_POINT", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::reviewSession);
    }

    public McpServerFeatures.SyncPromptSpecification writingPracticeSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_writing_practice",
                "雅思写作练习：选择题目、引导构思、收集作文并保存记录",
                List.of(
                        arg("taskType", "写作任务类型：1=Task1，2=Task2", false),
                        arg("targetBand", "目标雅思写作分数", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::writingPractice);
    }

    public McpServerFeatures.SyncPromptSpecification speakingPracticeSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_speaking_practice",
                "雅思口语模拟考官：选择 Part 和话题后进行口语练习",
                List.of(
                        arg("part", "口语 Part：1 / 2 / 3", false),
                        arg("topicTags", "话题标签", false),
                        arg("targetBand", "目标雅思口语分数", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::speakingPractice);
    }

    public McpServerFeatures.SyncPromptSpecification weaknessDiagnosisSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_weakness_diagnosis",
                "雅思薄弱项诊断：结合学习统计、错题和模考趋势给出改进方向",
                List.of(arg("days", "分析最近多少天的数据，默认 30", false))
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::weaknessDiagnosis);
    }

    public McpServerFeatures.SyncPromptSpecification addWordToTodayPlanSpec() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "ielts_add_word_to_today_plan",
                "新增雅思单词并加入今日学习计划：先创建单词，再把 WORD 内容加入今日计划",
                List.of(
                        arg("word", "单词原形", true),
                        arg("definitionZh", "中文释义", true),
                        arg("sourceText", "原始单词材料，可包含音标、词性、例句、标签等", false)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, this::addWordToTodayPlan);
    }

    private McpSchema.GetPromptResult dailyCoach(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思每日学习教练。请先读取这些上下文：
                - Resource: ielts://today/plan
                - Resource: ielts://study/stats
                - Resource: ielts://profile/summary

                如需更完整建议，可以调用：
                - ielts_get_plan_suggestion
                - ielts_get_dashboard

                参数：
                - availableMinutes: %s
                - targetBand: %s
                - focusSkill: %s

                输出要求：
                - 今日时间分配
                - 学习内容和复习动作
                - 完成标准
                - 使用的数据来源和调用过的 Tool
                不要编造学习记录；需要真实数据时先读取 Resource 或调用 Tool。
                """.formatted(
                value(request, "availableMinutes"),
                value(request, "targetBand"),
                value(request, "focusSkill")
        );
        return result("每日雅思学习教练", text);
    }

    private McpSchema.GetPromptResult wordDrill(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思词汇训练助手。请根据参数调用：
                - ielts_list_words
                - ielts_list_phrases

                参数：
                - difficulty: %s
                - topicTags: %s
                - count: %s

                训练方式：
                1. 先给出词义回忆或英译中问题。
                2. 用户回答后给出简短反馈和例句。
                3. 如果用户明确给出复习评分，再调用 ielts_submit_review。
                4. 结束时简要说明使用过的 Tool 和下一步建议。
                """.formatted(
                value(request, "difficulty"),
                value(request, "topicTags"),
                value(request, "count")
        );
        return result("雅思词汇训练", text);
    }

    private McpSchema.GetPromptResult reviewSession(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思间隔复习助手。请先读取：
                - Resource: ielts://today/plan
                - Resource: ielts://study/stats

                可调用：
                - ielts_get_study_records
                - ielts_submit_review

                参数：
                - durationMinutes: %s
                - contentType: %s

                按待复习优先级组织复习。每次只呈现少量内容，用户反馈 AGAIN / HARD / GOOD / EASY 后再提交复习结果。
                结束时汇总已复习内容、已调用 Tool、下一步建议。
                """.formatted(
                value(request, "durationMinutes"),
                value(request, "contentType")
        );
        return result("雅思间隔复习会话", text);
    }

    private McpSchema.GetPromptResult writingPractice(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思写作练习助手。请调用 ielts_list_writing_tasks 或 ielts_get_training_writing 选择题目。

                参数：
                - taskType: %s
                - targetBand: %s

                流程：
                1. 给出题目和简短审题方向。
                2. 引导用户列提纲。
                3. 用户提交作文后，从 Task Response、Coherence、Lexical Resource、Grammar 四个方向给反馈。
                4. 如需保存记录，调用 ielts_submit_writing。
                5. 结束时说明使用的题目来源、已调用 Tool、下一步修改重点。
                """.formatted(
                value(request, "taskType"),
                value(request, "targetBand")
        );
        return result("雅思写作练习", text);
    }

    private McpSchema.GetPromptResult speakingPractice(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思口语模拟考官。请调用 ielts_list_speaking_topics 或 ielts_get_training_speaking 选择话题。

                参数：
                - part: %s
                - topicTags: %s
                - targetBand: %s

                流程：
                1. 按真实口语考试节奏提问。
                2. 每轮只问一个问题。
                3. 用户回答后给出自然表达、词汇和语法建议。
                4. 避免一次性给出过长讲解。
                5. 结束时说明使用的话题来源、已调用 Tool、下一步练习建议。
                """.formatted(
                value(request, "part"),
                value(request, "topicTags"),
                value(request, "targetBand")
        );
        return result("雅思口语练习", text);
    }

    private McpSchema.GetPromptResult weaknessDiagnosis(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思薄弱项诊断助手。请读取或调用：
                - Resource: ielts://study/stats
                - Resource: ielts://mistakes/recent
                - Resource: ielts://mock-tests/trends
                - ielts_get_mistake_stats
                - ielts_get_dashboard

                参数：
                - days: %s

                输出：
                - 当前最明显的 2-3 个薄弱项
                - 证据来自哪些数据
                - 已读取的 Resource 和已调用的 Tool
                - 下一步训练建议
                - 今天可执行的最小任务
                """.formatted(value(request, "days"));
        return result("雅思薄弱项诊断", text);
    }

    private McpSchema.GetPromptResult addWordToTodayPlan(McpSyncServerExchange exchange, McpSchema.GetPromptRequest request) {
        String text = """
                你是雅思单词入库助手。请把用户提供的新单词整理成 kb-ielts 可接收的数据结构。

                参数：
                - word: %s
                - definitionZh: %s
                - sourceText: %s

                目标：
                把这个单词新增到词库，并加入今日学习计划。

                执行步骤：
                1. 如果 word 或 definitionZh 缺失，先向用户询问，不要猜测后创建。
                2. 清洗字段，补齐词性、音标、例句、标签等可确定信息；不确定的信息留空。
                3. 调用 ielts_create_word 创建单词。
                4. 使用返回的 word.id 调用 ielts_add_to_today_plan，contentType 传 WORD，summary 传单词原形。
                5. 返回创建结果、今日计划加入结果、已调用 Tool；不要编造 ID 或学习记录。
                """.formatted(
                value(request, "word"),
                value(request, "definitionZh"),
                value(request, "sourceText")
        );
        return result("新增单词并加入今日学习计划", text);
    }

    private McpSchema.PromptArgument arg(String name, String description, boolean required) {
        return new McpSchema.PromptArgument(name, description, required);
    }

    private McpSchema.GetPromptResult result(String description, String text) {
        return new McpSchema.GetPromptResult(
                description,
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text.trim())))
        );
    }

    private String value(McpSchema.GetPromptRequest request, String name) {
        Map<String, Object> arguments = request.arguments();
        if (arguments == null || !arguments.containsKey(name) || arguments.get(name) == null) {
            return "未指定";
        }
        return String.valueOf(arguments.get(name));
    }
}
