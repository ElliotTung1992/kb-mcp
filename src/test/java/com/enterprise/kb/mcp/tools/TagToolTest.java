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

@ExtendWith(MockitoExtension.class)
class TagToolTest {

    @Mock KbApiClient kbApiClient;
    @InjectMocks TagTool tagTool;

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
    void listTags_returnsHierarchicalTree() throws Exception {
        var resp = mapper.readTree("""
                {"data":[
                  {"id":"t1","name":"Engineering","tagType":"CATEGORY","children":[
                    {"id":"t2","name":"Backend","tagType":"TAG","children":[]},
                    {"id":"t3","name":"Frontend","tagType":"TAG","children":[]}
                  ]},
                  {"id":"t4","name":"HR","tagType":"CATEGORY","children":[]}
                ]}""");
        org.mockito.Mockito.when(kbApiClient.getTagTree("sp1", "test-jwt")).thenReturn(resp);

        String result = tagTool.listTags("sp1");

        assertThat(result).contains("Engineering").contains("CATEGORY");
        assertThat(result).contains("Backend").contains("Frontend").contains("TAG");
        assertThat(result).contains("HR");
        // children should be indented
        assertThat(result).contains("  - Backend");
    }

    @Test
    void listTags_emptyTree_returnsNoTagsMessage() throws Exception {
        var resp = mapper.readTree("{\"data\":[]}");
        org.mockito.Mockito.when(kbApiClient.getTagTree("sp1", "test-jwt")).thenReturn(resp);

        String result = tagTool.listTags("sp1");

        assertThat(result).isEqualTo("该空间暂无标签。");
    }
}
