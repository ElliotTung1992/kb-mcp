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

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadToolTest {

    @Mock KbApiClient kbApiClient;
    @InjectMocks UploadTool uploadTool;

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
    void upload_validBase64_returnsSuccessMessage() throws Exception {
        String encoded = Base64.getEncoder().encodeToString("hello world".getBytes());
        var resp = mapper.readTree("""
                {"data":{"id":"doc-99","originalFilename":"test.txt","status":"PENDING"}}""");
        when(kbApiClient.uploadDocument(eq("sp1"), eq("test.txt"), any(byte[].class),
                eq("text/plain"), eq("test-jwt"))).thenReturn(resp);

        String result = uploadTool.upload("sp1", "test.txt", encoded, "text/plain");

        assertThat(result).contains("上传成功").contains("doc-99").contains("PENDING");
    }

    @Test
    void upload_invalidBase64_returnsErrorMessage() {
        String result = uploadTool.upload("sp1", "test.txt", "!!!not-base64!!!", "text/plain");

        assertThat(result).contains("错误").contains("Base64");
    }
}
