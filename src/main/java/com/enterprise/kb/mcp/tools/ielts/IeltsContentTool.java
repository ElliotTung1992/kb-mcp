package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class IeltsContentTool {

    private final IeltsApiClient ieltsApiClient;

    @Tool(name = "ielts_list_grammar",
          description = "查询雅思语法要点列表，可按难度、分类（时态/从句/虚拟语气等）和话题标签筛选")
    public Map<String, Object> listGrammar(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "语法分类，如 tense、clause、conditional", required = false) String category,
            @ToolParam(description = "话题标签", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 10;

        JsonNode resp = ieltsApiClient.listGrammarPoints(difficulty, category, topicTags, p, ps);
        return IeltsToolResponses.paged(
                "语法要点",
                resp,
                IeltsToolResponses.filters("difficulty", difficulty, "category", category, "topicTags", topicTags),
                p,
                ps);
    }

    @Tool(name = "ielts_list_speaking_topics",
          description = "查询雅思口语话题列表，可按难度、Part（1/2/3）和话题标签筛选")
    public Map<String, Object> listSpeakingTopics(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "口语 Part：1、2 或 3", required = false) Integer part,
            @ToolParam(description = "话题标签，如 education、travel", required = false) String topicTags,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 10;

        JsonNode resp = ieltsApiClient.listSpeakingTopics(difficulty, part, topicTags, p, ps);
        return IeltsToolResponses.paged(
                "口语话题",
                resp,
                IeltsToolResponses.filters("difficulty", difficulty, "part", part, "topicTags", topicTags),
                p,
                ps);
    }

    @Tool(name = "ielts_list_writing_tasks",
          description = "查询雅思写作题目列表，可按难度和任务类型（1=Task1/2=Task2）筛选")
    public Map<String, Object> listWritingTasks(
            @ToolParam(description = "难度：1=简单，2=中等，3=困难", required = false) Integer difficulty,
            @ToolParam(description = "任务类型：1=Task1（图表描述），2=Task2（议论文）", required = false) Integer taskType,
            @ToolParam(description = "页码，从 1 开始，默认 1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认 10", required = false) Integer pageSize) {

        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 10;

        JsonNode resp = ieltsApiClient.listWritingTasks(difficulty, taskType, p, ps);
        return IeltsToolResponses.paged(
                "写作题目",
                resp,
                IeltsToolResponses.filters("difficulty", difficulty, "taskType", taskType),
                p,
                ps);
    }
}
