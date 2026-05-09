package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class IeltsTrainingTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_list_listening_items",
          description = "查询雅思听力练习素材，可按难度、Section、题型、话题标签和学习状态筛选")
    public Map<String, Object> listListeningItems(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "听力 Section：1-4", required = false) Integer section,
            @ToolParam(description = "题型，如 multiple_choice、completion", required = false) String questionType,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "学习状态：NEW / LEARNING / REVIEWING / MASTERED", required = false) String studyStatus,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "听力练习素材",
                ieltsApiClient.listListeningItems(difficulty, section, questionType, topicTags, studyStatus, p, ps),
                IeltsToolResponses.filters(
                        "difficulty", difficulty, "section", section, "questionType", questionType,
                        "topicTags", topicTags, "studyStatus", studyStatus),
                p,
                ps);
    }

    @Tool(name = "ielts_list_reading_items",
          description = "查询雅思阅读练习素材，可按难度、考试类型、题型、话题标签和学习状态筛选")
    public Map<String, Object> listReadingItems(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "考试类型：ACADEMIC / GENERAL", required = false) String trainingType,
            @ToolParam(description = "题型，如 matching、true_false_not_given", required = false) String questionType,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "学习状态：NEW / LEARNING / REVIEWING / MASTERED", required = false) String studyStatus,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "阅读练习素材",
                ieltsApiClient.listReadingItems(difficulty, trainingType, questionType, topicTags, studyStatus, p, ps),
                IeltsToolResponses.filters(
                        "difficulty", difficulty, "trainingType", trainingType, "questionType", questionType,
                        "topicTags", topicTags, "studyStatus", studyStatus),
                p,
                ps);
    }

    @Tool(name = "ielts_list_pronunciation_points",
          description = "查询雅思发音要点，可按难度、分类、关键词和学习状态筛选")
    public Map<String, Object> listPronunciationPoints(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "分类，如 vowel、consonant、intonation", required = false) String category,
            @ToolParam(description = "学习状态：NEW / LEARNING / REVIEWING / MASTERED", required = false) String studyStatus,
            @ToolParam(description = "关键词", required = false) String keyword,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "发音要点",
                ieltsApiClient.listPronunciationPoints(difficulty, category, studyStatus, keyword, p, ps),
                IeltsToolResponses.filters(
                        "difficulty", difficulty, "category", category, "studyStatus", studyStatus, "keyword", keyword),
                p,
                ps);
    }

    @Tool(name = "ielts_list_paraphrase_groups",
          description = "查询雅思同义替换组，可按难度、话题标签、关键词和学习状态筛选")
    public Map<String, Object> listParaphraseGroups(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "学习状态：NEW / LEARNING / REVIEWING / MASTERED", required = false) String studyStatus,
            @ToolParam(description = "关键词", required = false) String keyword,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "同义替换组",
                ieltsApiClient.listParaphraseGroups(difficulty, topicTags, studyStatus, keyword, p, ps),
                IeltsToolResponses.filters(
                        "difficulty", difficulty, "topicTags", topicTags, "studyStatus", studyStatus, "keyword", keyword),
                p,
                ps);
    }

    @Tool(name = "ielts_list_grammar_exercises",
          description = "查询雅思语法练习题，可按难度、题型、语法点 ID 和学习状态筛选")
    public Map<String, Object> listGrammarExercises(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "题型，如 fill_blank、choice", required = false) String questionType,
            @ToolParam(description = "语法点 ID，UUID 字符串", required = false) String grammarPointId,
            @ToolParam(description = "学习状态：NEW / LEARNING / REVIEWING / MASTERED", required = false) String studyStatus,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "语法练习题",
                ieltsApiClient.listGrammarExercises(difficulty, questionType, grammarPointId, studyStatus, p, ps),
                IeltsToolResponses.filters(
                        "difficulty", difficulty, "questionType", questionType,
                        "grammarPointId", grammarPointId, "studyStatus", studyStatus),
                p,
                ps);
    }

    @Tool(name = "ielts_get_training_listening",
          description = "获取雅思听力专项训练列表")
    public Map<String, Object> getTrainingListening(
            @ToolParam(description = "听力 Section：1-4", required = false) Integer section,
            @ToolParam(description = "题型", required = false) String questionType,
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "听力专项训练",
                ieltsApiClient.getTrainingListening(section, questionType, difficulty, p, ps),
                IeltsToolResponses.filters("section", section, "questionType", questionType, "difficulty", difficulty),
                p,
                ps);
    }

    @Tool(name = "ielts_get_training_reading",
          description = "获取雅思阅读专项训练列表")
    public Map<String, Object> getTrainingReading(
            @ToolParam(description = "题型", required = false) String questionType,
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "阅读专项训练",
                ieltsApiClient.getTrainingReading(questionType, difficulty, p, ps),
                IeltsToolResponses.filters("questionType", questionType, "difficulty", difficulty),
                p,
                ps);
    }

    @Tool(name = "ielts_get_training_writing",
          description = "获取雅思写作专项训练列表")
    public Map<String, Object> getTrainingWriting(
            @ToolParam(description = "写作任务类型：1=Task1，2=Task2", required = false) Integer taskNumber,
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "写作专项训练",
                ieltsApiClient.getTrainingWriting(taskNumber, difficulty, p, ps),
                IeltsToolResponses.filters("taskNumber", taskNumber, "difficulty", difficulty),
                p,
                ps);
    }

    @Tool(name = "ielts_get_training_speaking",
          description = "获取雅思口语专项训练列表")
    public Map<String, Object> getTrainingSpeaking(
            @ToolParam(description = "口语 Part：1 / 2 / 3", required = false) Integer part,
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {
        int p = pageOrDefault(page);
        int ps = sizeOrDefault(pageSize);
        return IeltsToolResponses.paged(
                "口语专项训练",
                ieltsApiClient.getTrainingSpeaking(part, difficulty, p, ps),
                IeltsToolResponses.filters("part", part, "difficulty", difficulty),
                p,
                ps);
    }

    @Tool(name = "ielts_list_writing_submissions",
          description = "查询最近雅思作文提交记录")
    public Map<String, Object> listWritingSubmissions(
            @ToolParam(description = "返回条数，默认 20，最大 100", required = false) Integer limit) {
        int actualLimit = limit != null ? limit : 20;
        Map<String, Object> result = IeltsToolResponses.data(
                "作文提交记录",
                ieltsApiClient.listWritingSubmissions(actualLimit));
        result.put("filters", IeltsToolResponses.filters("limit", actualLimit));
        return result;
    }

    @Tool(name = "ielts_submit_writing",
          description = "提交一条雅思作文记录，submission 字段结构与 kb-ielts 的 IeltsWritingSubmission 模型一致")
    public Map<String, Object> submitWriting(
            @ToolParam(description = "作文提交对象，字段结构与 kb-ielts 的 IeltsWritingSubmission 模型一致")
            Map<String, Object> submission) {
        Map<String, Object> result = IeltsToolResponses.data("作文提交结果", ieltsApiClient.submitWriting(submission));
        result.put("message", "作文记录已保存");
        return result;
    }

    @Tool(name = "ielts_list_mock_tests",
          description = "查询最近雅思模考记录")
    public Map<String, Object> listMockTests(
            @ToolParam(description = "返回条数，默认 20，最大 100", required = false) Integer limit) {
        int actualLimit = limit != null ? limit : 20;
        Map<String, Object> result = IeltsToolResponses.data("模考记录", ieltsApiClient.listMockTests(actualLimit));
        result.put("filters", IeltsToolResponses.filters("limit", actualLimit));
        return result;
    }

    @Tool(name = "ielts_create_mock_test",
          description = "新增一条雅思模考记录，mockTest 字段结构与 kb-ielts 的 IeltsMockTest 模型一致")
    public Map<String, Object> createMockTest(
            @ToolParam(description = "模考记录对象，字段结构与 kb-ielts 的 IeltsMockTest 模型一致")
            Map<String, Object> mockTest) {
        Map<String, Object> result = IeltsToolResponses.data("模考创建结果", ieltsApiClient.createMockTest(mockTest));
        result.put("message", "模考记录已创建");
        return result;
    }

    @Tool(name = "ielts_get_mock_test_trends",
          description = "查询雅思模考整体和分项分数趋势")
    public Map<String, Object> getMockTestTrends() {
        return IeltsToolResponses.data("模考趋势", ieltsApiClient.getMockTestTrends());
    }

    private int pageOrDefault(Integer page) {
        return page != null ? page : 1;
    }

    private int sizeOrDefault(Integer pageSize) {
        return pageSize != null ? pageSize : 10;
    }

}
