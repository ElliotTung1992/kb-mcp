package com.enterprise.kb.mcp;

import com.enterprise.kb.mcp.auth.McpRequestContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class KbMcpApplication {

    public static void main(String[] args) {
        // Spring AI 在 boundedElastic 线程上执行 Tool，普通 ThreadLocal 无法跨线程传递。
        // onScheduleHook 在任务提交给 Reactor 调度器时（仍在 servlet 线程）捕获 JWT，
        // 并在任务实际执行时（boundedElastic 线程）恢复，执行完毕后清理。
        Schedulers.onScheduleHook("kb-jwt-propagation", runnable -> {
            String jwt = McpRequestContext.getJwt();
            if (jwt == null) return runnable;
            return () -> {
                McpRequestContext.setJwt(jwt);
                try {
                    runnable.run();
                } finally {
                    McpRequestContext.clear();
                }
            };
        });
        SpringApplication.run(KbMcpApplication.class, args);
    }
}
