package com.enterprise.kb.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * kb-app REST API 的 HTTP 客户端。
 * 所有业务方法的 jwt 参数均从 {@link com.enterprise.kb.mcp.auth.McpRequestContext} 取出后传入。
 */
@Slf4j
@Component
public class KbApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KbApiClient(@Value("${kb.app.base-url}") String baseUrl, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── 搜索 ─────────────────────────────────────────────────────────────────

    /**
     * 混合搜索（语义 + 关键词 RRF 融合）。
     */
    public JsonNode hybridSearch(String spaceId, String query, int topK, String jwt) {
        return post("/api/v1/spaces/" + spaceId + "/search/hybrid",
                Map.of("query", query, "topK", topK), jwt);
    }

    /**
     * 语义搜索。
     */
    public JsonNode semanticSearch(String spaceId, String query, int topK, String jwt) {
        return post("/api/v1/spaces/" + spaceId + "/search",
                Map.of("query", query, "topK", topK), jwt);
    }

    /**
     * 关键词搜索。
     */
    public JsonNode keywordSearch(String spaceId, String query, int topK, String jwt) {
        return post("/api/v1/spaces/" + spaceId + "/search/keyword",
                Map.of("query", query, "topK", topK), jwt);
    }

    // ── 问答 ─────────────────────────────────────────────────────────────────

    /**
     * 同步 RAG 问答。
     */
    public JsonNode askQuestion(String spaceId, String question, String sessionId, String jwt) {
        Map<String, Object> body = sessionId != null
                ? Map.of("question", question, "sessionId", sessionId, "topK", 5)
                : Map.of("question", question, "topK", 5);
        return post("/api/v1/spaces/" + spaceId + "/qa/ask", body, jwt);
    }

    // ── 文档 ─────────────────────────────────────────────────────────────────

    /**
     * 分页列出文档。
     */
    public JsonNode listDocuments(String spaceId, int page, int pageSize, String jwt) {
        String uri = "/api/v1/spaces/" + spaceId + "/documents?page=" + page + "&size=" + pageSize;
        return get(uri, jwt);
    }

    /**
     * 获取文档全文内容。
     */
    public String getDocumentContent(String spaceId, String docId, String jwt) {
        JsonNode resp = get("/api/v1/spaces/" + spaceId + "/documents/" + docId + "/content", jwt);
        return resp.path("data").asText();
    }

    // ── 标签 ─────────────────────────────────────────────────────────────────

    /**
     * 获取标签层级树。
     */
    public JsonNode getTagTree(String spaceId, String jwt) {
        return get("/api/v1/spaces/" + spaceId + "/tags/tree", jwt);
    }

    // ── 知识空间 ──────────────────────────────────────────────────────────────

    /**
     * 获取知识空间元信息。
     */
    public JsonNode getSpaceInfo(String spaceId, String jwt) {
        return get("/api/v1/spaces/" + spaceId, jwt);
    }

    /**
     * 列出当前用户有权访问的所有知识空间。
     */
    public JsonNode listSpaces(String jwt) {
        return get("/api/v1/spaces", jwt);
    }

    // ── 文档上传 ──────────────────────────────────────────────────────────────

    /**
     * 上传文档（multipart/form-data）。
     */
    public JsonNode uploadDocument(String spaceId, String filename, byte[] content, String mimeType, String jwt) {
        MultiValueMap<String, Object> formBody = new LinkedMultiValueMap<>();
        formBody.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() { return filename; }
        });

        try {
            String raw = restClient.post()
                    .uri("/api/v1/spaces/" + spaceId + "/documents/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + jwt)
                    .body(formBody)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new KbApiException("Access denied: EDITOR permission required", e);
            }
            log.error("POST /documents/upload failed: {}", e.getMessage());
            throw new KbApiException("Failed to upload document: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("POST multipart /documents/upload failed: {}", e.getMessage());
            throw new KbApiException("Failed to call kb-app: " + e.getMessage(), e);
        }
    }

    // ── 内部 HTTP 工具方法 ────────────────────────────────────────────────────

    private JsonNode post(String uri, Object body, String jwt) {
        try {
            var spec = restClient.post().uri(uri).body(body);
            if (jwt != null) spec = spec.header("Authorization", "Bearer " + jwt);
            String raw = spec.retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("POST {} failed: {}", uri, e.getMessage());
            throw new KbApiException("Failed to call kb-app: " + e.getMessage(), e);
        }
    }

    private JsonNode get(String uri, String jwt) {
        try {
            var spec = restClient.get().uri(uri);
            if (jwt != null) spec = spec.header("Authorization", "Bearer " + jwt);
            String raw = spec.retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("GET {} failed: {}", uri, e.getMessage());
            throw new KbApiException("Failed to call kb-app: " + e.getMessage(), e);
        }
    }

    // ── 异常 ─────────────────────────────────────────────────────────────────

    public static class KbApiException extends RuntimeException {
        public KbApiException(String message, Throwable cause) { super(message, cause); }
    }
}
