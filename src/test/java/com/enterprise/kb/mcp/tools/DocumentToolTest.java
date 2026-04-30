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
class DocumentToolTest {

    @Mock KbApiClient kbApiClient;
    @InjectMocks DocumentTool documentTool;

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
    void listDocuments_returnsFormattedList() throws Exception {
        var resp = mapper.readTree("""
                {"data":{
                  "total":2,
                  "items":[
                    {"id":"doc-1","title":"Doc One","status":"COMPLETED","chunkCount":5},
                    {"id":"doc-2","title":"Doc Two","status":"PROCESSING","chunkCount":0}
                  ]
                }}""");
        when(kbApiClient.listDocuments("sp1", 0, 20, "test-jwt")).thenReturn(resp);

        String result = documentTool.listDocuments("sp1", null, null);

        assertThat(result).contains("共 2 个文档").contains("第 1 页");
        assertThat(result).contains("Doc One").contains("doc-1").contains("COMPLETED").contains("5");
        assertThat(result).contains("Doc Two").contains("doc-2").contains("PROCESSING");
    }

    @Test
    void listDocuments_emptySpace_returnsNoDocMessage() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"total\":0,\"items\":[]}}");
        when(kbApiClient.listDocuments("sp1", 0, 20, "test-jwt")).thenReturn(resp);

        String result = documentTool.listDocuments("sp1", null, null);

        assertThat(result).isEqualTo("该空间暂无文档。");
    }

    @Test
    void listDocuments_customPageAndSize_passedToClient() throws Exception {
        var resp = mapper.readTree("{\"data\":{\"total\":0,\"items\":[]}}");
        when(kbApiClient.listDocuments("sp1", 2, 10, "test-jwt")).thenReturn(resp);

        documentTool.listDocuments("sp1", 2, 10);

        verify(kbApiClient).listDocuments("sp1", 2, 10, "test-jwt");
    }

    @Test
    void getDocumentContent_returnsContent() {
        when(kbApiClient.getDocumentContent("sp1", "doc-1", "test-jwt")).thenReturn("Full document text here.");

        String result = documentTool.getDocumentContent("sp1", "doc-1");

        assertThat(result).isEqualTo("Full document text here.");
    }

    @Test
    void getDocumentContent_emptyContent_returnsEmptyMessage() {
        when(kbApiClient.getDocumentContent("sp1", "doc-1", "test-jwt")).thenReturn("");

        String result = documentTool.getDocumentContent("sp1", "doc-1");

        assertThat(result).contains("文档内容为空");
    }

    @Test
    void getDocumentContent_nullContent_returnsEmptyMessage() {
        when(kbApiClient.getDocumentContent("sp1", "doc-1", "test-jwt")).thenReturn(null);

        String result = documentTool.getDocumentContent("sp1", "doc-1");

        assertThat(result).contains("文档内容为空");
    }
}
