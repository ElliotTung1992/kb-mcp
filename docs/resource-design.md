# MCP Resource 实现逻辑

## 一、请求入口

所有 MCP 请求（包括 `resources/read`、`completion/complete`）统一走 `POST /mcp`，由 `McpAuthFilter` 拦截：

```
POST /mcp
  ↓
McpAuthFilter.doFilterInternal()
  ├─ /mcp/login → 直接放行
  └─ 其他 /mcp 请求
       ├─ tokenService.loadToken() 从文件读 JWT
       ├─ 未登录 → 返回 401
       └─ 已登录 → McpRequestContext.setJwt(token) 存入 ThreadLocal
                   ↓ 继续处理请求
                   finally: McpRequestContext.clear() 防止内存泄漏
```

## 二、注册的 Resource

在 `McpServerConfig.mcpResources()` 里注册四个 `SyncResourceSpecification`：

```
URI                          类型        列表归属
────────────────────────────────────────────────────────────
enterprise://spaces          固定 URI    Resources（左）
space://{spaceId}/info       模板 URI    Resources（左）+ Resource Templates（右）
ielts://today/plan           固定 URI    Resources（左）
ielts://study/stats          固定 URI    Resources（左）
```

SDK 判断规则：URI 含 `{}` → 同时写入两个列表，这是强制行为。

## 三、resources/read 请求流程

客户端发 `resources/read`，SDK 用 `DefaultMcpUriTemplateManager.matches()` 做 URI 匹配：

```
请求 URI: space://abc123/info
  ↓
SDK 遍历已注册资源，找到 space://{spaceId}/info 匹配
  ↓
调用 SpaceResource.read()
  ├─ extractSpaceId("space://abc123/info") → "abc123"
  ├─ 检测是否含 {} → 否，继续
  ├─ McpRequestContext.getJwt() 取出 JWT
  └─ kbApiClient.getSpaceInfo("abc123", jwt) → 格式化返回
```

`enterprise://spaces` / `ielts://today/plan` / `ielts://study/stats` 是固定 URI，精确匹配，直接调对应 read 方法。

> `ielts` 的两个 Resource 调的是 `IeltsApiClient`，不需要 JWT（kb-ielts 无认证），不取 `McpRequestContext`。

## 四、completion/complete 请求流程

客户端检测到模板 URI 里的 `{spaceId}` 时，发起补全请求：

```
completion/complete { ref: {type: "ref/resource", uri: "space://{spaceId}/info"} }
  ↓
SDK 查 completions 表，key = ResourceReference("ref/resource", "space://{spaceId}/info")
  ↓
McpServerConfig.buildSpaceIdCompleter()
  ├─ McpRequestContext.getJwt() 取 JWT
  ├─ kbApiClient.listSpaces(jwt) → 拿所有空间
  ├─ 按 partial 值过滤
  └─ 返回匹配的 spaceId 列表给客户端
```

若未注册 CompletionSpec，SDK 会抛出 `AsyncCompletionSpecification not found` 错误。

## 五、JWT 传递机制

Resource 和 Tool 调用时 Spring AI 不传 `HttpServletRequest`，无法直接拿请求头。解决方案是 `McpRequestContext`（ThreadLocal）：

```
McpAuthFilter（Servlet 线程）
  → setJwt(token)           ← 过滤器阶段存入
  → 执行 read() / tool()    ← 业务逻辑阶段取用
  → clear()                 ← finally 块清理
```

同一请求在同一线程执行，ThreadLocal 贯穿整个调用链。

## 六、完整依赖关系

```
McpServerConfig
  ├─ mcpResources()
  │    ├─ EnterpriseSpacesResource  →  KbApiClient.listSpaces()
  │    ├─ SpaceResource             →  KbApiClient.getSpaceInfo()
  │    ├─ IeltsResource.todayPlan   →  IeltsApiClient.getTodayPlan()
  │    └─ IeltsResource.studyStats  →  IeltsApiClient.getStats()
  │
  └─ mcpCompletions()
       └─ buildSpaceIdCompleter     →  KbApiClient.listSpaces()

McpAuthFilter → TokenService → 读取 /data/.kb-mcp-token
             → McpRequestContext（ThreadLocal）→ KbApiClient（需 JWT 的调用）
```

## 七、设计边界

| 问题 | 现状 |
|------|------|
| `space-info` 出现在 Resources 和 Templates 两个列表 | SDK 强制行为，URI 含 `{}` 时必然同时写入两个列表，无法只注册到 Templates |
| AI 不知道 spaceId 怎么用 `space-info` | 需先读 `enterprise://spaces`，是正常的两步流程 |
| 直接用模板 URI 读 `space-info` | 返回引导提示，不报错 |
