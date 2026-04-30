package com.enterprise.kb.mcp.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 API Key 限流，使用内存令牌桶（Bucket4j）。
 *
 * <p>{@code @Order(2)} 使本过滤器在 {@link McpApiKeyFilter}（无 Order，默认最低优先级）之前执行。
 * 限流桶存在内存中，重启后重置；若需跨实例限流，需替换为 Redis 后端。
 */
@Slf4j
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int rpm;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${mcp.rate-limit.rpm:60}") int rpm) {
        this.rpm = rpm;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(apiKey, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(rpm)
                                .refillGreedy(rpm, Duration.ofMinutes(1))
                                .build())
                        .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for key prefix: {}", apiKey.substring(0, Math.min(8, apiKey.length())));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Rate limit exceeded, retry after 60s\"}");
        }
    }
}
