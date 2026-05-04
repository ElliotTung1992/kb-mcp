package com.enterprise.kb.mcp.auth;

import com.enterprise.kb.mcp.service.TokenService;
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

/**
 * MCP 认证过滤器：从 token 文件读取 JWT，用于所有 /mcp 请求认证。
 *
 * <p>/mcp/login 接口不需要认证，直接放行。
 * 其他 /mcp 请求从 ~/.kb-mcp-token 文件读取 JWT，存入 {@link McpRequestContext}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.core.annotation.Order(1)
public class McpAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // /mcp/login 不需要认证
        if (uri.equals("/mcp/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 其他 /mcp 请求需要 JWT
        if (uri.startsWith("/mcp")) {
            String token = tokenService.loadToken();
            if (!StringUtils.hasText(token)) {
                sendUnauthorized(response, "请先调用 POST /mcp/login 登录");
                return;
            }
            try {
                McpRequestContext.setJwt(token);
                filterChain.doFilter(request, response);
            } finally {
                McpRequestContext.clear();
            }
            return;
        }

        // 其他路径放行
        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}