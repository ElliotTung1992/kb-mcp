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
class QaToolTest {

    @Mock KbApiClient kbApiClient;
    @InjectMocks QaTool qaTool;

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
    void ask_withCitations_returnsAnswerAndCitations() throws Exception {
        var resp = mapper.readTree("""
                {"data":{
                  "answer":"The answer is 42.",
                  "citations":[
                    {"documentTitle":"Guide","pageNumber":3,"excerpt":"Excerpt here"},
                    {"documentTitle":"Manual","excerpt":"Another excerpt"}
                  ],
                  "sessionId":"sess-abc"
                }}""");
        when(kbApiClient.askQuestion("sp1", "What is the answer?", null, "test-jwt")).thenReturn(resp);

        String result = qaTool.ask("sp1", "What is the answer?", null);

        assertThat(result).contains("The answer is 42.");
        assertThat(result).contains("Guide").contains("第 3 页").contains("Excerpt here");
        assertThat(result).contains("Manual").contains("Another excerpt");
        assertThat(result).contains("sess-abc");
    }

    @Test
    void ask_noCitations_returnsAnswerOnly() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"answer\":\"Simple answer.\",\"citations\":[]}}");
        when(kbApiClient.askQuestion("sp1", "q", null, "test-jwt")).thenReturn(resp);

        String result = qaTool.ask("sp1", "q", null);

        assertThat(result).contains("Simple answer.");
        assertThat(result).doesNotContain("引用来源");
    }

    @Test
    void ask_withSessionId_passesSessionIdToClient() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"answer\":\"ans\",\"citations\":[]}}");
        when(kbApiClient.askQuestion("sp1", "q", "sess-123", "test-jwt")).thenReturn(resp);

        qaTool.ask("sp1", "q", "sess-123");

        verify(kbApiClient).askQuestion("sp1", "q", "sess-123", "test-jwt");
    }

    @Test
    void ask_emptyAnswer_returnsNoAnswerMessage() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"citations\":[]}}");
        when(kbApiClient.askQuestion("sp1", "q", null, "test-jwt")).thenReturn(resp);

        String result = qaTool.ask("sp1", "q", null);

        assertThat(result).contains("暂无答案");
    }
}
