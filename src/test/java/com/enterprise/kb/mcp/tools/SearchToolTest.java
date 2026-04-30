package com.enterprise.kb.mcp.tools;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import com.enterprise.kb.mcp.client.KbApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchToolTest {

    @Mock KbApiClient kbApiClient;
    @InjectMocks SearchTool searchTool;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        McpRequestContext.setJwt("test-jwt");
    }

    @AfterEach
    void tearDown() {
        McpRequestContext.clear();
    }

    @Test
    void search_defaultHybridMode_returnsFormattedResults() throws Exception {
        var resp = mapper.readTree("""
                {"data":{"hits":[
                  {"documentTitle":"Doc A","score":0.95,"excerpt":"Some content"},
                  {"documentTitle":"Doc B","score":0.80,"excerpt":"Other content"}
                ]}}""");
        when(kbApiClient.hybridSearch("sp1", "query", 5, "test-jwt")).thenReturn(resp);

        String result = searchTool.search("sp1", "query", null, null);

        assertThat(result).contains("Doc A").contains("0.950").contains("Some content");
        assertThat(result).contains("Doc B").contains("0.800");
        assertThat(result).contains("hybrid").contains("找到 2 条结果");
    }

    @Test
    void search_noResults_returnsNoContentMessage() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"hits\":[]}}");
        when(kbApiClient.hybridSearch("sp1", "q", 5, "test-jwt")).thenReturn(resp);

        String result = searchTool.search("sp1", "q", "hybrid", 5);

        assertThat(result).isEqualTo("未找到相关内容。");
    }

    @Test
    void search_semanticMode_callsSemanticSearch() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"hits\":[]}}");
        when(kbApiClient.semanticSearch("sp1", "q", 3, "test-jwt")).thenReturn(resp);

        searchTool.search("sp1", "q", "semantic", 3);

        verify(kbApiClient).semanticSearch("sp1", "q", 3, "test-jwt");
        verify(kbApiClient, never()).hybridSearch(any(), any(), anyInt(), any());
    }

    @Test
    void search_keywordMode_callsKeywordSearch() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"hits\":[]}}");
        when(kbApiClient.keywordSearch("sp1", "q", 10, "test-jwt")).thenReturn(resp);

        searchTool.search("sp1", "q", "keyword", 10);

        verify(kbApiClient).keywordSearch("sp1", "q", 10, "test-jwt");
        verify(kbApiClient, never()).hybridSearch(any(), any(), anyInt(), any());
    }

    @Test
    void search_unknownMode_fallsBackToHybrid() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"hits\":[]}}");
        when(kbApiClient.hybridSearch("sp1", "q", 5, "test-jwt")).thenReturn(resp);

        searchTool.search("sp1", "q", "unknown", null);

        verify(kbApiClient).hybridSearch("sp1", "q", 5, "test-jwt");
    }
}
