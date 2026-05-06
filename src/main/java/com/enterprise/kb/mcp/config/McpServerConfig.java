package com.enterprise.kb.mcp.config;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import com.enterprise.kb.mcp.client.KbApiClient;
import com.enterprise.kb.mcp.resources.EnterpriseSpacesResource;
import com.enterprise.kb.mcp.resources.IeltsResource;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 注册所有 MCP Tools、Resources、Completions 和 Streamable HTTP transport。
 *
 * <p><b>Transport 替换原理</b>：Spring AI 1.0.0 默认使用旧版 HTTP+SSE transport。
 * 将自定义 {@link WebMvcStreamableHttpServerTransportProvider} 注册为 Bean 后，
 * Spring AI auto-config 的 {@code @ConditionalOnMissingBean(McpServerTransportProvider.class)}
 * 条件不再满足，默认的 {@code WebMvcSseServerTransportProvider} 退出装配，
 * 从而启用 MCP 2024-11-05 规范的 Streamable HTTP 协议。
 *
 * <p><b>Resources 与 Resource Templates</b>：MCP 协议区分两类资源——
 * 固定 URI（Resources）可直接读取；模板 URI（Resource Templates）需客户端填入参数后才能读取。
 * Spring AI MCP SDK 通过同一个 {@link McpServerFeatures.SyncResourceSpecification} 注册两者：
 * URI 不含 {@code {}} → 仅进入 Resources 列表；URI 含 {@code {}} → 同时进入两个列表。
 * 这是 SDK 的强制行为，无法将模板资源只注册到 Templates 列表。
 */
@Configuration
public class McpServerConfig {

    // ── Transport ────────────────────────────────────────────────────────────

    @Bean
    public McpServerTransportProvider mcpServerTransportProvider(ObjectMapper objectMapper) {
        return new WebMvcStreamableHttpServerTransportProvider(objectMapper, "/mcp");
    }

    // RouterFunctionMapping 需要显式注册的 RouterFunction 才能路由 POST/DELETE /mcp
    @Bean
    public RouterFunction<ServerResponse> streamableHttpRouterFunction(
            McpServerTransportProvider transportProvider) {
        return ((WebMvcStreamableHttpServerTransportProvider) transportProvider).getRouterFunction();
    }

    // ── Tools ────────────────────────────────────────────────────────────────

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

    // ── Resources ────────────────────────────────────────────────────────────

    /**
     * 注册所有 MCP Resources 和 Resource Templates。
     *
     * <p>固定 URI（无 {@code {}}）只出现在 Resources 列表，AI 可直接读取：
     * <ul>
     *   <li>{@code enterprise://spaces} — 知识空间列表，AI 操作知识库前的入口上下文</li>
     *   <li>{@code ielts://today/plan} — 今日学习计划</li>
     *   <li>{@code ielts://study/stats} — 学习统计</li>
     * </ul>
     *
     * <p>模板 URI（含 {@code {spaceId}}）同时出现在 Resources 和 Resource Templates 列表，
     * AI 需先从 {@code enterprise://spaces} 获取 spaceId 后才能构造具体 URI 读取：
     * <ul>
     *   <li>{@code space://{spaceId}/info} — 指定空间的元信息</li>
     * </ul>
     */
    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> mcpResources(
            EnterpriseSpacesResource enterpriseSpacesResource,
            SpaceResource spaceResource,
            IeltsResource ieltsResource) {
        return List.of(
                enterpriseSpacesResource.spec(),
                spaceResource.spec(),
                ieltsResource.todayPlanSpec(),
                ieltsResource.studyStatsSpec()
        );
    }

    // ── Completions ──────────────────────────────────────────────────────────

    /**
     * 为 {@code space://{spaceId}/info} 的 {@code spaceId} 参数提供自动补全。
     *
     * <p>当 MCP 客户端检测到模板 URI 中的 {@code {}} 占位符时，会发起
     * {@code completion/complete} 请求。若服务端没有注册对应的 CompletionSpec，
     * SDK 会抛出 "AsyncCompletionSpecification not found" 错误。
     * 此处注册的处理器通过调用 {@code GET /api/v1/spaces} 返回可用的 spaceId 列表。
     */
    @Bean
    public List<McpServerFeatures.SyncCompletionSpecification> mcpCompletions(KbApiClient kbApiClient) {
        return List.of(
                new McpServerFeatures.SyncCompletionSpecification(
                        new McpSchema.ResourceReference("ref/resource", "space://{spaceId}/info"),
                        buildSpaceIdCompleter(kbApiClient))
        );
    }

    private BiFunction<McpSyncServerExchange, McpSchema.CompleteRequest, McpSchema.CompleteResult>
            buildSpaceIdCompleter(KbApiClient kbApiClient) {
        return (exchange, request) -> {
            String partial = request.argument().value();
            String jwt = McpRequestContext.getJwt();

            JsonNode data = kbApiClient.listSpaces(jwt).path("data");
            // kb-app 可能返回直接数组或分页对象 {items: [...]}
            JsonNode items = data.isArray() ? data : data.path("items");

            List<String> values = new ArrayList<>();
            for (JsonNode space : items) {
                String id = space.path("id").asText();
                if (id.isBlank()) continue;
                if (partial == null || partial.isBlank() || id.startsWith(partial)) {
                    values.add(id);
                }
            }
            return new McpSchema.CompleteResult(
                    new McpSchema.CompleteResult.CompleteCompletion(values, values.size(), false));
        };
    }
}
