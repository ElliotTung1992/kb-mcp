package com.enterprise.kb.mcp.auth;

import com.enterprise.kb.mcp.client.KbApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * MCP 请求认证过滤器：把客户端的 API Key 换成 kb-app JWT，存入 {@link McpRequestContext}。
 *
 * <p>每次请求都向 kb-app 换取 JWT（不缓存），确保 Key 吊销后立即生效。
 * JWT 由 kb-app 签发，携带用户身份和空间权限，后续 Tool 方法凭此调用 kb-app 接口。
 */
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final KbApiClient kbApiClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // actuator/health 不需要认证
        if (request.getRequestURI().startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!StringUtils.hasText(apiKey)) {
            sendUnauthorized(response, "Missing X-API-Key header");
            return;
        }

        try {
            String jwt = kbApiClient.exchangeApiKey(apiKey);
            McpRequestContext.setJwt(jwt);
            filterChain.doFilter(request, response);
        } catch (KbApiClient.UnauthorizedException e) {
            sendUnauthorized(response, "Invalid or expired API key");
        } finally {
            McpRequestContext.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
