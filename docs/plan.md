# kb-mcp 架构说明

## 一、背景与目标

`kb-mcp` 是雅思学习系统的 MCP 接入层，让 Claude Desktop、Cursor、OpenClaw 等支持 MCP 协议的 AI 客户端能够直接调用雅思学习内容管理和学习计划查询能力。

```
AI 客户端（Claude Desktop / OpenClaw / mcporter）
  │  MCP 协议（Streamable HTTP）
  ▼
kb-mcp :8084
  │  HTTP REST（Docker 内网）
  ▼
kb-ielts :8083  ──► PostgreSQL
```

## 二、项目结构

```
kb-mcp/
├── pom.xml
├── docker-compose.yml
├── .env.example
├── docker/
│   ├── Dockerfile
│   └── maven-settings.xml
└── src/main/
    ├── java/com/enterprise/kb/mcp/
    │   ├── KbMcpApplication.java         # @SpringBootApplication 入口
    │   ├── config/
    │   │   └── McpServerConfig.java      # MCP Server + Tool/Resource 注册
    │   ├── client/
    │   │   └── IeltsApiClient.java       # RestClient，调用 kb-ielts REST API
    │   ├── tools/ielts/
    │   │   ├── IeltsWordTool.java        # 单词/短语 CRUD
    │   │   ├── IeltsStudyTool.java       # 学习计划/统计
    │   │   └── IeltsContentTool.java     # 语法/口语/写作内容查询
    │   ├── resources/
    │   │   └── IeltsResource.java        # ielts://today/plan、ielts://study/stats
    │   └── auth/
    │       └── RateLimitFilter.java      # Bucket4j 全局限流，60 RPM
    └── resources/
        └── application.yml
```

## 三、已注册能力

### Tools（通过 `MethodToolCallbackProvider` 注册）

| Tool 名称 | 说明 |
|---|---|
| `ielts_list_words` | 查询单词列表，支持难度/词表/话题筛选 |
| `ielts_list_phrases` | 查询短语列表 |
| `ielts_create_word` | 新增单词（含音标、释义、例句、关联词） |
| `ielts_batch_import_words` | 批量导入单词数组 |
| `ielts_list_grammar` | 查询语法要点 |
| `ielts_list_speaking_topics` | 查询口语话题 |
| `ielts_list_writing_tasks` | 查询写作题目 |
| `ielts_get_today_plan` | 获取今日学习计划 |
| `ielts_get_study_stats` | 获取学习统计数据 |

### Resources（通过 `SyncResourceSpecification` 注册）

| URI | 说明 |
|---|---|
| `ielts://today/plan` | 今日学习计划（背景上下文） |
| `ielts://study/stats` | 学习统计（背景上下文） |

## 四、技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| MCP Server | `spring-ai-starter-mcp-server-webmvc` | Spring AI 官方，Streamable HTTP 传输 |
| Web 框架 | Spring MVC | Spring Boot 3.4.1 |
| HTTP 客户端 | Spring `RestClient` | 调用 kb-ielts REST API |
| 限流 | Bucket4j 8.10.1 | 全局令牌桶，60 RPM，超限返回 HTTP 429 |
| 虚拟线程 | JDK 21 | 避免 HTTP 阻塞耗尽线程池 |

## 五、IeltsApiClient 设计

封装对 `kb-ielts` 的所有 HTTP 调用，无需鉴权：

```java
@Component
public class IeltsApiClient {
    // 单词列表：GET /api/ielts/words
    public JsonNode listWords(Map<String, String> params) { ... }

    // 新增单词：POST /api/ielts/words
    public JsonNode createWord(ObjectNode body) { ... }

    // 批量导入：POST /api/ielts/words/batch
    public JsonNode batchImport(ArrayNode words) { ... }

    // 今日计划：GET /api/ielts/study/today
    public JsonNode getTodayPlan() { ... }

    // 学习统计：GET /api/ielts/study/stats
    public JsonNode getStats() { ... }

    // 语法/口语/写作查询（类似 listWords 结构）
    public JsonNode listGrammar(Map<String, String> params) { ... }
    public JsonNode listSpeakingTopics(Map<String, String> params) { ... }
    public JsonNode listWritingTasks(Map<String, String> params) { ... }
}
```

## 六、限流实现

`RateLimitFilter` 拦截除 `/actuator` 外的所有请求，使用 Bucket4j 令牌桶：

```
请求 → 令牌桶有令牌？
          ├─ 是 → 扣除令牌，继续处理
          └─ 否 → 返回 HTTP 429 Too Many Requests
```

限流参数通过环境变量 `MCP_RATE_LIMIT_RPM` 控制，默认 60 RPM。

## 七、部署

```
kb-mcp/docker-compose.yml
  services:
    mcp:
      build: { dockerfile: docker/Dockerfile }
      ports: ["8084:8084"]
      environment:
        IELTS_APP_BASE_URL: http://kb-ielts:8083
      networks: [ielts-network]

  networks:
    ielts-network:
      external: true   # 由 kb-ielts docker-compose 创建
```

kb-ielts 必须先启动（负责创建 `ielts-network`），kb-mcp 通过该网络访问 `kb-ielts` 容器。
