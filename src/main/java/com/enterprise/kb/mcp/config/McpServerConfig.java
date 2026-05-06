package com.enterprise.kb.mcp.config;

import com.enterprise.kb.mcp.resources.IeltsResource;
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
 * <p><b>Transport 替换原理</b>：Spring AI 1.0.0 默认使用旧版 HTTP+SSE transport。
 * 将自定义 {@link WebMvcStreamableHttpServerTransportProvider} 注册为 Bean 后，
 * Spring AI auto-config 的 {@code @ConditionalOnMissingBean(McpServerTransportProvider.class)}
 * 条件不再满足，默认的 {@code WebMvcSseServerTransportProvider} 退出装配，
 * 从而启用 MCP 2024-11-05 规范的 Streamable HTTP 协议。
 */
@Configuration
public class McpServerConfig {

    // ── Transport ────────────────────────────────────────────────────────────

    @Bean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        return new WebMvcStreamableHttpServerTransportProvider(objectMapper, "/mcp");
    }

    @Bean
    public RouterFunction<ServerResponse> streamableHttpRouterFunction(
            McpServerTransportProvider transportProvider) {
        return ((WebMvcStreamableHttpServerTransportProvider) transportProvider).getRouterFunction();
    }

    // ── Tools ────────────────────────────────────────────────────────────────

    @Bean
    public ToolCallbackProvider ieltsTools(
            IeltsWordTool wordTool, IeltsStudyTool studyTool, IeltsContentTool contentTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(wordTool, studyTool, contentTool)
                .build();
    }

    // ── Resources ────────────────────────────────────────────────────────────

    /**
     * 注册雅思相关 MCP Resources：
     * <ul>
     *   <li>{@code ielts://today/plan} — 今日学习计划</li>
     *   <li>{@code ielts://study/stats} — 学习统计</li>
     * </ul>
     */
    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> mcpResources(IeltsResource ieltsResource) {
        return List.of(
                ieltsResource.todayPlanSpec(),
                ieltsResource.studyStatsSpec()
        );
    }
}
