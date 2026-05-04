package com.enterprise.kb.mcp.config;

import com.enterprise.kb.mcp.resources.DocumentResource;
import com.enterprise.kb.mcp.resources.SpaceResource;
import com.enterprise.kb.mcp.tools.DocumentTool;
import com.enterprise.kb.mcp.tools.QaTool;
import com.enterprise.kb.mcp.tools.SearchTool;
import com.enterprise.kb.mcp.tools.TagTool;
import com.enterprise.kb.mcp.tools.UploadTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsContentTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsStudyTool;
import com.enterprise.kb.mcp.tools.ielts.IeltsWordTool;
import com.enterprise.kb.mcp.transport.WebMvcStreamableHttpServerTransportProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

/**
 * 注册所有 MCP Tools、Resources 和 Streamable HTTP transport。
 *
 * <p>Spring AI 1.0.0 仅内置旧 HTTP+SSE transport。通过实现 {@link McpServerTransportProvider}
 * 并定义为 Bean，触发 Spring AI auto-config 的 {@code @ConditionalOnMissingBean} 条件，
 * 替换默认的 {@code WebMvcSseServerTransportProvider}，使 MCP 使用 Streamable HTTP 协议。
 */
@Configuration
public class McpServerConfig {

    // ── MCP Transport (Streamable HTTP) ──────────────────────────────────

    /**
     * 替换 Spring AI 内置的 SSE transport，启用 MCP Streamable HTTP 协议。
     * {@code @ConditionalOnMissingBean(McpServerTransportProvider.class)} 会使
     * Spring AI 的 WebMvcSseServerTransportProvider 退出装配。
     */
    @Bean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        return new WebMvcStreamableHttpServerTransportProvider(objectMapper, "/mcp");
    }

    /**
     * 注册 RouterFunction，使 Spring MVC 的 RouterFunctionMapping 能路由
     * POST /mcp 和 DELETE /mcp 到 Streamable HTTP transport。
     */
    @Bean
    public RouterFunction<ServerResponse> streamableHttpRouterFunction(
            McpServerTransportProvider transportProvider) {
        return ((WebMvcStreamableHttpServerTransportProvider) transportProvider).getRouterFunction();
    }

    // ── MCP Tools ─────────────────────────────────────────────────────────

    @Bean
    public ToolCallbackProvider knowledgeBaseTools(
            SearchTool searchTool, QaTool qaTool,
            DocumentTool documentTool, TagTool tagTool, UploadTool uploadTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(searchTool, qaTool, documentTool, tagTool, uploadTool)
                .build();
    }

    @Bean
    public ToolCallbackProvider ieltsTools(
            IeltsWordTool wordTool, IeltsStudyTool studyTool, IeltsContentTool contentTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(wordTool, studyTool, contentTool)
                .build();
    }

    // ── MCP Resources ─────────────────────────────────────────────────────

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> knowledgeBaseResources(
            SpaceResource spaceResource, DocumentResource documentResource) {
        return List.of(spaceResource.spec(), documentResource.spec());
    }
}
