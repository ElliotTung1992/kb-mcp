package com.enterprise.kb.mcp.auth;

/**
 * 在过滤器和 Tool 方法之间传递 JWT。
 *
 * <p>Spring AI 调用 Tool 方法时不传递 HttpServletRequest，因此无法直接从请求头取 JWT。
 * 过滤器在认证成功后把 JWT 存入 ThreadLocal，Tool 方法执行期间通过 {@link #getJwt()} 取出，
 * 请求结束时由过滤器的 finally 块调用 {@link #clear()} 防止内存泄漏。
 */
public class McpRequestContext {

    private static final ThreadLocal<String> JWT_HOLDER = new ThreadLocal<>();

    public static void setJwt(String jwt) {
        JWT_HOLDER.set(jwt);
    }

    public static String getJwt() {
        return JWT_HOLDER.get();
    }

    public static void clear() {
        JWT_HOLDER.remove();
    }
}
