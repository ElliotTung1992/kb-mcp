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
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 注册所有 MCP Tools 和 Resources。
 * 传输方式、Server 名称、版本由 application.yml spring.ai.mcp.server.* 配置。
 *
 * <p>Tools 通过 {@link MethodToolCallbackProvider} 注册：框架读取 {@code @Tool} / {@code @ToolParam}
 * 注解自动生成 MCP JSON Schema，Claude 凭此 Schema 决定何时调用哪个 Tool。
 *
 * <p>Resources 通过 {@link McpServerFeatures.SyncResourceSpecification} 注册：需手动构造
 * MCP 协议数据结构，因为 Resource 没有参数 Schema，只有固定 URI 模板和读取回调。
 *
 * <p>kb-app Tools（search_*、ask_*、*_document 等）和 IELTS Tools（ielts_*）
 * 注册为两个独立的 Bean，分别对应不同的后端服务，互不依赖。
 */
@Configuration
public class McpServerConfig {

    /** kb-app 知识库 Tools：搜索、问答、文档管理 */
    @Bean
    public ToolCallbackProvider knowledgeBaseTools(
            SearchTool searchTool, QaTool qaTool,
            DocumentTool documentTool, TagTool tagTool, UploadTool uploadTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(searchTool, qaTool, documentTool, tagTool, uploadTool)
                .build();
    }

    /** kb-ielts 雅思学习 Tools：单词、短语、语法、口语、写作、学习计划 */
    @Bean
    public ToolCallbackProvider ieltsTools(
            IeltsWordTool wordTool, IeltsStudyTool studyTool, IeltsContentTool contentTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(wordTool, studyTool, contentTool)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> knowledgeBaseResources(
            SpaceResource spaceResource, DocumentResource documentResource) {
        return List.of(spaceResource.spec(), documentResource.spec());
    }
}
