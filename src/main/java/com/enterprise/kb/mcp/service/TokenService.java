package com.enterprise.kb.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Token 管理服务：负责登录验证和 JWT token 文件读写。
 */
@Slf4j
@Service
public class TokenService {

    private static final ObjectMapper OM = new ObjectMapper();

    private final Path tokenFilePath;
    private final RestClient restClient;

    public TokenService(
            @Value("${mcp.token-file:/data/.kb-mcp-token}") String tokenFile,
            @Value("${kb.app.base-url}") String baseUrl) {
        this.tokenFilePath = Path.of(tokenFile);
        log.info("Token 文件路径: {}", tokenFilePath);
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 登录：验证用户名密码，JWT 写入文件。
     */
    public String login(String username, String password) throws Exception {
        try {
            JsonNode resp = restClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", username, "password", password))
                    .retrieve()
                    .body(JsonNode.class);

            String token = resp.path("data").path("accessToken").asText();
            saveToken(token);
            return token;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new Exception("用户名或密码错误");
            }
            throw e;
        }
    }

    /**
     * 保存 token 到文件。
     */
    public void saveToken(String token) throws Exception {
        Files.writeString(tokenFilePath, token);
    }

    /**
     * 从文件读取 token，若已过期返回 null。
     */
    public String loadToken() {
        try {
            if (Files.exists(tokenFilePath)) {
                String token = Files.readString(tokenFilePath).trim();
                if (!token.isEmpty() && !isTokenExpired(token)) {
                    return token;
                }
            }
        } catch (Exception e) {
            log.warn("读取 token 文件失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查是否已登录（有未过期的 token 文件）。
     */
    public boolean isLoggedIn() {
        return loadToken() != null;
    }

    /**
     * 删除 token（登出）。
     */
    public void logout() throws Exception {
        Files.deleteIfExists(tokenFilePath);
    }

    private boolean isTokenExpired(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return true;
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = OM.readTree(payload);
            long exp = claims.path("exp").asLong(0);
            return exp > 0 && exp < System.currentTimeMillis() / 1000;
        } catch (Exception e) {
            log.warn("JWT 解析失败，视为过期: {}", e.getMessage());
            return true;
        }
    }
}