package com.enterprise.kb.mcp.controller;

import com.enterprise.kb.mcp.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MCP 登录控制器：验证用户名密码，写入 JWT token 文件。
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class LoginController {

    private final TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        try {
            String token = tokenService.login(username, password);
            log.info("用户 {} 登录成功", username);
            return ResponseEntity.ok(Map.of("token", token));
        } catch (Exception e) {
            log.warn("用户 {} 登录失败: {}", username, e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        boolean loggedIn = tokenService.isLoggedIn();
        return ResponseEntity.ok(Map.of("loggedIn", loggedIn));
    }
}