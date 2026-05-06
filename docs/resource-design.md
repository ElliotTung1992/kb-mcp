# MCP Resource 实现逻辑

## 一、请求入口

所有 MCP 请求（包括 `resources/read`）统一走 `POST /mcp`，由 `RateLimitFilter` 拦截：

```
POST /mcp
  ↓
RateLimitFilter.doFilterInternal()
  ├─ /actuator → 直接放行
  └─ 其他请求
       ├─ 令牌桶有令牌 → 继续处理
       └─ 令牌耗尽 → 返回 HTTP 429
            ↓
       MCP Server 路由
            ↓
       IeltsResource.read()
```

## 二、注册的 Resource

在 `McpServerConfig.mcpResources()` 里注册两个 `SyncResourceSpecification`：

```
URI                     类型        说明
─────────────────────────────────────────────────────────
ielts://today/plan      固定 URI    今日学习计划（背景上下文）
ielts://study/stats     固定 URI    学习统计数据（背景上下文）
```

两者均为固定 URI，无模板变量，仅出现在 Resources 列表中。

## 三、resources/read 请求流程

客户端发 `resources/read`，SDK 按 URI 精确匹配：

```
请求 URI: ielts://today/plan
  ↓
SDK 遍历已注册资源，精确匹配
  ↓
IeltsResource.readTodayPlan()
  ├─ IeltsApiClient.getTodayPlan() → kb-ielts GET /api/ielts/study/today
  ├─ 解析 planDate / totalItems / completedItems / dueItems
  └─ 返回 TextResourceContents（text/plain 格式化文本）

请求 URI: ielts://study/stats
  ↓
IeltsResource.readStudyStats()
  ├─ IeltsApiClient.getStats() → kb-ielts GET /api/ielts/study/stats
  ├─ 解析 totalRecords / learningCount / reviewingCount / masteredCount
  └─ 返回 TextResourceContents
```

`kb-ielts` 无认证要求，`IeltsApiClient` 直接发 HTTP 请求，不携带任何凭据。

## 四、完整依赖关系

```
McpServerConfig
  └─ mcpResources()
       ├─ IeltsResource.todayPlanSpec()   →  IeltsApiClient.getTodayPlan()  →  kb-ielts:8083
       └─ IeltsResource.studyStatsSpec()  →  IeltsApiClient.getStats()      →  kb-ielts:8083
```

## 五、设计边界

| 问题 | 现状 |
|------|------|
| Resource 与 Tool 的区别 | Resource 是只读背景上下文，AI 主动读取用于了解当前进度；Tool 是主动操作（查询/写入） |
| Resource 适合什么场景 | 会话开始时先读 `ielts://today/plan`，了解今日计划后再提供针对性学习建议 |
| 为什么返回 text/plain 而非 JSON | AI 直接消费纯文本更自然，减少额外解析步骤 |
