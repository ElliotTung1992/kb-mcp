# kb-mcp 二期规划

## 一、一期成果回顾

一期已交付的核心能力：

| 模块 | 内容 |
|------|------|
| 传输层 | 自定义 Streamable HTTP transport（MCP 2024-11-05 规范），替换 Spring AI 内置旧 SSE |
| 认证 | JWT token 文件方案：`POST /mcp/login` 写入 `/data/.kb-mcp-token`，后续请求自动读取透传 |
| 限流 | Bucket4j 全局令牌桶，60 RPM，触发返回 HTTP 429 |
| 知识库 Tools | `search_knowledge_base`、`kb_ask`（多轮对话）、`kb_documents`、`kb_upload` |
| IELTS Tools | 单词/短语查询与新增、批量导入、语法/口语/写作内容查询、今日计划、学习统计 |
| MCP Resources | `enterprise://spaces`、`enterprise://documents` |
| Skills 定义 | `kb-mcp-skills`：`kb-search`、`kb-ask`、`kb-documents`、`kb-upload`、`ielts-words`、`ielts-study`、`ielts-content` |
| 部署 | 独立容器，统一对外端口 8084，通过 `kb-network` 与 kb-app 互通 |

**一期核心痛点（驱动二期）**：
1. IELTS 缺乏复习交互（SRS 打卡）和测验能力，只能查数据不能"用"数据
2. 知识库 Tool 覆盖不完整（无删除、无标签管理、无文档详情全文获取）
3. 无可观测性：Tool 调用无指标、无告警，生产问题靠日志盲查

---

## 二、二期目标

**以"可用性提升"为主线，分三个方向推进（认证维持单用户 token 文件方案不变）：**

1. **IELTS 学习闭环**：补全复习打卡、测验生成与写作评分，实现完整的学习闭环
2. **知识库功能补全**：文档全生命周期管理，补齐缺失的 Tool
3. **可观测性与稳定性**：指标上报、错误恢复

---

## 三、方向一：IELTS 学习闭环

### 3.1 现状缺口

一期 IELTS 只能**查询数据**（单词列表、学习统计），缺乏：
- 复习打卡（SRS 反馈）
- 单词/内容详情查询
- 测验生成与作答
- 写作评分

### 3.2 新增 Tool：复习打卡（SRS）

`ielts_submit_review`：提交对某条学习记录的复习结果

```json
{
  "name": "ielts_submit_review",
  "description": "提交对某条内容（单词/短语/语法）的复习结果，系统据此更新下次复习时间（间隔重复算法）",
  "inputSchema": {
    "contentType": "WORD | PHRASE | GRAMMAR | SPEAKING | WRITING",
    "contentId":   "string，内容 ID",
    "result":      "REMEMBERED | FORGOT | HARD"
  }
}
```

对应 kb-ielts 接口：`POST /api/ielts/study/review`

### 3.3 新增 Tool：内容详情

`ielts_get_word_detail`：获取单词完整信息（音标、释义、例句、记忆技巧）

```json
{
  "name": "ielts_get_word_detail",
  "description": "获取雅思单词的完整详情，包含双语释义、例句列表、音标和词形变化",
  "inputSchema": {
    "wordId": "string"
  }
}
```

### 3.4 新增 Tool：测验生成

`ielts_generate_quiz`：根据条件随机抽取题目，生成选择题或填空题

```json
{
  "name": "ielts_generate_quiz",
  "description": "生成雅思单词/语法测验，返回题目列表（选项已打乱，不含答案）",
  "inputSchema": {
    "quizType":    "WORD_CHOICE | WORD_FILL | GRAMMAR_FILL",
    "count":       "integer，题目数量，默认 10",
    "difficulty":  "1-3，可选",
    "topicTags":   "string，可选"
  }
}
```

`ielts_submit_quiz`：提交测验答案，返回得分与解析

```json
{
  "name": "ielts_submit_quiz",
  "description": "提交测验答案，返回得分、每题对错和正确答案解析",
  "inputSchema": {
    "quizId":  "string，由 ielts_generate_quiz 返回",
    "answers": "[{questionId, answer}]"
  }
}
```

### 3.5 新增 Tool：写作 AI 评分

`ielts_evaluate_writing`：用户提交作文，通过 Claude API 按官方雅思评分标准打分

```json
{
  "name": "ielts_evaluate_writing",
  "description": "评估雅思写作作文，从任务完成、连贯衔接、词汇资源、语法多样性四个维度打分并给出改进建议",
  "inputSchema": {
    "taskType": "1 或 2",
    "prompt":   "string，写作题目",
    "essay":    "string，用户写的作文"
  }
}
```

**实现方式**：kb-mcp 内部调用 Claude API（claude-sonnet-4-6），使用结构化 Prompt 强制输出 JSON 格式评分结果，不经过 kb-app。

评分输出结构：
```json
{
  "overallBand": 6.5,
  "dimensions": {
    "taskAchievement": { "score": 7, "feedback": "..." },
    "coherenceCohesion": { "score": 6, "feedback": "..." },
    "lexicalResource": { "score": 6.5, "feedback": "..." },
    "grammaticalRange": { "score": 6.5, "feedback": "..." }
  },
  "suggestions": ["...", "..."],
  "revisedParagraph": "针对第一段的改写示例"
}
```

### 3.6 更新 Skills 定义

| Skill 文件 | 新增触发词 |
|---|---|
| `ielts-study/SKILL.md` | 打卡、记住了、没记住、复习完了 |
| `ielts-words/SKILL.md` | 单词详情、查这个单词的例句 |
| 新增 `ielts-quiz/SKILL.md` | 测验、考一考、出题、做题、提交答案 |
| 新增 `ielts-writing/SKILL.md` | 评分、批改、修改作文、写作反馈 |

### 3.7 实现步骤

- [ ] `kb-ielts` 侧：新增 `POST /api/ielts/study/review`、quiz CRUD 接口（或由 kb-mcp 内部生成）
- [ ] `kb-mcp` 侧：`IeltsStudyTool.submitReview()`
- [ ] `kb-mcp` 侧：`IeltsWordTool.getWordDetail()`
- [ ] `kb-mcp` 侧：新建 `IeltsQuizTool`（generateQuiz + submitQuiz）
- [ ] `kb-mcp` 侧：新建 `IeltsWritingTool`（evaluateWriting，集成 Anthropic SDK）
- [ ] `pom.xml`：引入 `com.anthropic:anthropic-java-sdk`
- [ ] 新增 `skills/ielts-quiz/SKILL.md`、`skills/ielts-writing/SKILL.md`

---

## 四、方向二：知识库功能补全

### 4.1 新增 Tool：获取文档全文

`kb_get_document`：获取指定文档的完整内容（分块拼接）

```json
{
  "name": "kb_get_document",
  "description": "获取知识库中指定文档的完整全文内容，适合在 AI 需要阅读整篇文档时调用",
  "inputSchema": {
    "spaceId": "string",
    "docId":   "string"
  }
}
```

**安全限制**：单次返回最多 20000 字符；超出时返回前 N 个分块并提示"内容已截断，可传入 page 参数分页获取"。

### 4.2 新增 Tool：标签管理

`kb_list_tags`：获取知识空间的标签树

```json
{
  "name": "kb_list_tags",
  "description": "获取知识空间的标签层级树，用于了解文档分类结构或在上传时选择标签"
}
```

`kb_add_document_tag`：给文档打标签（需 EDITOR 权限）

```json
{
  "name": "kb_add_document_tag",
  "description": "给文档添加标签（需 EDITOR 权限）",
  "inputSchema": {
    "spaceId": "string",
    "docId":   "string",
    "tagId":   "string"
  }
}
```

### 4.3 新增 Tool：删除文档

`kb_delete_document`：删除指定文档（需 ADMIN 权限，执行前向 AI 客户端确认）

```json
{
  "name": "kb_delete_document",
  "description": "从知识空间删除文档（需要 ADMIN 权限），此操作不可逆",
  "inputSchema": {
    "spaceId": "string",
    "docId":   "string"
  }
}
```

Tool Handler 返回确认摘要，Skill 中要求 AI 在调用此 Tool 前向用户二次确认。

### 4.4 新增 MCP Prompt Templates

预定义三个 Prompt，供 AI 客户端作为"快捷入口"：

| Prompt 名称 | 描述 | 参数 |
|---|---|---|
| `kb_summary_prompt` | "总结文档内容并提取关键点" | `spaceId`、`docId` |
| `kb_compare_prompt` | "对比两个文档的异同" | `spaceId`、`docId1`、`docId2` |
| `ielts_daily_plan_prompt` | "生成今日雅思学习建议" | 无 |

Prompt Templates 在 `McpServerConfig` 中注册为 `McpServerFeatures.SyncPromptSpecification`。

### 4.5 文档上传状态查询

一期 `kb_upload` 是异步的，无法知道文档是否处理完成。新增轮询支持：

`kb_get_upload_status`：查询上传任务的处理状态

```json
{
  "name": "kb_get_upload_status",
  "description": "查询文档上传后的处理状态（PENDING / PROCESSING / COMPLETED / FAILED）",
  "inputSchema": {
    "spaceId": "string",
    "docId":   "string"
  }
}
```

### 4.6 实现步骤

- [ ] `DocumentTool`：新增 `getDocument()`（含字符数截断逻辑）
- [ ] `TagTool`：新增 `listTags()`、`addDocumentTag()`
- [ ] 新建 `DeleteTool`：实现 `deleteDocument()`
- [ ] 新建 `UploadStatusTool`：实现 `getUploadStatus()`
- [ ] `McpServerConfig`：注册三个 Prompt Templates
- [ ] 更新 `kb-documents/SKILL.md`：补充新 Tool 的触发词和使用说明

---

## 五、方向三：可观测性与稳定性

### 5.1 Tool 调用指标

在 Spring Boot Actuator 基础上，通过 Micrometer 上报 Tool 调用指标：

| 指标名 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `mcp.tool.calls.total` | Counter | `tool`, `status(success/error)` | Tool 调用次数 |
| `mcp.tool.duration.seconds` | Timer | `tool` | Tool 调用耗时分布 |
| `mcp.rate_limit.rejected.total` | Counter | — | 限流拒绝次数 |

实现方式：AOP + `@Around` 切面，在每个 `@Tool` 方法前后自动埋点，无需修改 Tool 代码。

```java
@Aspect
@Component
@RequiredArgsConstructor
public class ToolMetricsAspect {
    private final MeterRegistry meterRegistry;

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object recordMetrics(ProceedingJoinPoint pjp) throws Throwable { ... }
}
```

### 5.2 Prometheus + Grafana 集成

`application.yml` 新增：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

`docker-compose.yml` 新增 Prometheus 和 Grafana 服务（可选，已有监控栈的环境只需添加 scrape config）。

Grafana 看板包含：
- Tool 调用 QPS 趋势（按 tool 分组）
- Tool P95/P99 延迟
- 限流触发率

### 5.3 错误恢复与重试

**RestClient 重试**：对 kb-app / kb-ielts 的 HTTP 调用，配置指数退避重试：
- 触发条件：`503 Service Unavailable`、连接超时、读超时
- 最大重试 3 次，退避系数 2（1s → 2s → 4s）
- 实现：Spring Retry `@Retryable` 注解加在 `KbApiClient` 方法上

**JWT 过期自动续期**：
- `KbApiClient` 收到 kb-app 返回 `401` 时，自动重新登录（`POST /mcp/login`）并重放原请求

**熔断**：
- 引入 Resilience4j CircuitBreaker，kb-app 连续失败 5 次后熔断 30 秒，快速返回 `Upstream service unavailable`

### 5.4 结构化日志

Tool 调用统一输出 JSON 结构化日志（Logback JSON Appender），字段包含：

```json
{
  "timestamp": "2026-05-04T10:00:00Z",
  "level": "INFO",
  "tool": "search_knowledge_base",
  "durationMs": 230,
  "status": "success",
  "spaceId": "uuid",
  "traceId": "..."
}
```

---

## 六、整体技术依赖变化

| 新增依赖 | 用途 |
|---|---|
| `com.anthropic:anthropic-java-sdk` | 写作评分调用 Claude API |
| `io.micrometer:micrometer-registry-prometheus` | Prometheus 指标导出 |
| `org.springframework.retry:spring-retry` | RestClient 重试 |
| `io.github.resilience4j:resilience4j-spring-boot3` | 熔断器 |

---

## 七、实现阶段

### Phase 1：IELTS 学习闭环（约 4 天）
- [ ] `IeltsApiClient`：新增 review、word detail、quiz 接口调用
- [ ] `IeltsStudyTool.submitReview()`
- [ ] `IeltsWordTool.getWordDetail()`
- [ ] 新建 `IeltsQuizTool`（generateQuiz + submitQuiz）
- [ ] 新建 `IeltsWritingTool`（evaluateWriting，集成 Claude API）
- [ ] 新增 `ielts-quiz/SKILL.md`、`ielts-writing/SKILL.md`
- [ ] 单元测试（Mock IeltsApiClient + Mock Claude API）

### Phase 2：知识库功能补全（约 2 天）
- [ ] `DocumentTool.getDocument()`（含截断）
- [ ] `TagTool.listTags()`、`TagTool.addDocumentTag()`
- [ ] 新建 `DeleteTool.deleteDocument()`
- [ ] 新建 `UploadStatusTool.getUploadStatus()`
- [ ] `McpServerConfig`：注册 3 个 Prompt Templates
- [ ] 更新 SKILL.md

### Phase 3：可观测性与稳定性（约 2 天）
- [ ] 新建 `ToolMetricsAspect`
- [ ] `KbApiClient`：`@Retryable` + CircuitBreaker + 401 自动续期
- [ ] `application.yml`：prometheus 端点
- [ ] Logback JSON Appender
- [ ] 端到端验证：模拟 kb-app 故障，确认熔断 + 重试行为符合预期

---

## 八、风险与约束

| 风险 | 影响 | 缓解 |
|---|---|---|
| `ielts_evaluate_writing` 调用 Claude API 延迟较高（10–30s） | MCP 客户端超时 | 在 Tool 描述中说明"评估较慢，请耐心等待"；客户端侧配置足够长的 timeout |
| 写作评分输出不稳定（Claude 非结构化输出） | 解析失败 | 强制 JSON 输出（tool_use 模式），解析失败时返回原始文本提示手动查看 |
| `kb_delete_document` 误删 | 数据丢失 | Tool 描述标注不可逆；SKILL.md 要求 AI 执行前展示文档信息并二次确认 |

---

## 附录：二期后 Tool 全景

| Tool 名称 | 状态 | 所属模块 |
|---|---|---|
| `search_knowledge_base` | 一期已有 | 知识库 |
| `kb_ask` | 一期已有 | 知识库 |
| `kb_documents` | 一期已有 | 知识库 |
| `kb_upload` | 一期已有 | 知识库 |
| `kb_get_document` | **二期新增** | 知识库 |
| `kb_list_tags` | **二期新增** | 知识库 |
| `kb_add_document_tag` | **二期新增** | 知识库 |
| `kb_delete_document` | **二期新增** | 知识库 |
| `kb_get_upload_status` | **二期新增** | 知识库 |
| `ielts_list_words` | 一期已有 | IELTS 单词 |
| `ielts_list_phrases` | 一期已有 | IELTS 单词 |
| `ielts_create_word` | 一期已有 | IELTS 单词 |
| `ielts_batch_import_words` | 一期已有 | IELTS 单词 |
| `ielts_get_word_detail` | **二期新增** | IELTS 单词 |
| `ielts_list_grammar` | 一期已有 | IELTS 内容 |
| `ielts_list_speaking_topics` | 一期已有 | IELTS 内容 |
| `ielts_list_writing_tasks` | 一期已有 | IELTS 内容 |
| `ielts_get_today_plan` | 一期已有 | IELTS 学习 |
| `ielts_get_study_stats` | 一期已有 | IELTS 学习 |
| `ielts_submit_review` | **二期新增** | IELTS 学习 |
| `ielts_generate_quiz` | **二期新增** | IELTS 测验 |
| `ielts_submit_quiz` | **二期新增** | IELTS 测验 |
| `ielts_evaluate_writing` | **二期新增** | IELTS 写作 |
