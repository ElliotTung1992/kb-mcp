package com.enterprise.kb.mcp.tools.ielts;

import com.enterprise.kb.mcp.client.IeltsApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IeltsWordToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listWordsParsesCurrentPageResponseShape() throws Exception {
        IeltsApiClient ieltsApiClient = mock(IeltsApiClient.class);
        JsonNode response = objectMapper.readTree("""
                {
                  "success": true,
                  "data": {
                    "content": [
                      {
                        "id": "a0594803-996d-42ef-8bbe-37f4e02f8767",
                        "word": "hello",
                        "definitionZh": "你好",
                        "wordList": "IELTS",
                        "difficulty": 1,
                        "skillTags": "speaking",
                        "examples": []
                      }
                    ],
                    "totalElements": 1,
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                  }
                }
                """);
        when(ieltsApiClient.listWords(null, "IELTS", null, 1, 20)).thenReturn(response);

        IeltsWordTool tool = new IeltsWordTool(ieltsApiClient);

        Map<String, Object> result = tool.listWords(null, "IELTS", null, null, null);

        assertThat(result).containsEntry("success", true);
        assertThat(result.get("message")).isNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) result.get("filters");
        assertThat(filters)
                .containsEntry("difficulty", null)
                .containsEntry("wordList", "IELTS")
                .containsEntry("topicTags", null);

        assertThat(result.get("pagination"))
                .isEqualTo(Map.of(
                        "requestedPage", 1,
                        "requestedPageSize", 20,
                        "page", 0,
                        "size", 20,
                        "totalElements", 1L,
                        "totalPages", 1,
                        "returnedElements", 1
                ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0))
                .containsEntry("id", "a0594803-996d-42ef-8bbe-37f4e02f8767")
                .containsEntry("word", "hello")
                .containsEntry("definitionZh", "你好")
                .containsEntry("wordList", "IELTS")
                .containsEntry("difficulty", 1)
                .containsEntry("skillTags", "speaking");
    }
}
