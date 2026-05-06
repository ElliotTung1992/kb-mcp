# Enterprise-KB MCP 服务实现方案

## 一、背景与目标

### 项目概述
`enterprise-kb` 是一个多租户企业知识库系统，基于 RAG（检索增强生成）架构，支持文档上传、语义搜索、混合搜索和问答功能。

### MCP 是什么
Model Context Protocol（MCP）是 Anthropic 推出的开放标准，允许 AI 模型（如 Claude）通过标准化协议访问外部工具和数据源。实现 MCP Server 后，Claude Desktop、Cursor 等支持 MCP 的客户端可以直接调用知识库的搜索、问答、文档管理等能力。

### 目标
新建独立项目 `kb-mcp`，对外暴露标准 MCP 协议，让 AI 客户端能够：
- 搜索知识库（语义/关键词/混合）
- 对知识库提问并获取引用来源
- 浏览文档和标签
- （可选）上传/管理文档

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      MCP 客户端                               │
│      Claude Desktop / Cursor / 自定义 AI Agent               │
└────────────────────────┬────────────────────────────────────┘
                         │ MCP Protocol (SSE / stdio)
         ┌───────────────▼──────────────────┐
         │     [容器] kb-mcp  :8082          │
         │  ┌──────────┐  ┌──────────────┐  │
         │  │MCP Server│  │ Tool Handler │  │
         │  │(SSE/stdio)│  │(API Key 认证)│  │
         │  └──────────┘  └──────────────┘  │
         └───────────────┬──────────────────┘
                         │ HTTP REST (Docker 内网)
         ┌───────────────▼──────────────────┐
         │     [容器] kb-app  :8080          │
         │  kb-search  kb-document           │
         │  kb-knowledge-graph  kb-user      │
         └──────┬────────────────┬───────────┘
                │                │
  ┌─────────────▼───┐    ┌───────▼──────────┐
  │ [容器] postgres  │    │  [容器] milvus    │
  └─────────────────┘    └──────────────────┘
```

### 关键设计决策：独立容器 + HTTP 调用
`kb-mcp` 作为独立的 Spring Boot 应用单独容器化，通过 Docker 内网 HTTP 调用 `kb-app` 已有的 REST API。

- **不直接连数据库**：复用 `kb-app` 的鉴权、业务逻辑、连接池，避免重复
- **松耦合**：`kb-mcp` 容器可独立重启、扩容，不影响主应用
- **传输方式**：对外暴露 SSE（`:8082`），本地开发用 stdio 指向 Docker 内的 `kb-app`

---

## 三、项目结构：`kb-mcp`

`kb-mcp` 是与 `enterprise-kb` **平级的独立 Spring Boot 项目**，有自己的 `main` 方法，不依赖任何业务模块，通过 HTTP 调用 `kb-app`。

```
kb-mcp/                             # 独立项目，与 enterprise-kb 同级
├── pom.xml                         # 独立 Maven 项目
├── docker-compose.yml
├── .env.example
├── docker/
│   ├── Dockerfile
│   └── maven-settings.xml
└── src/main/
    ├── java/com/enterprise/kb/mcp/
    │   ├── KbMcpApplication.java       # @SpringBootApplication 入口
    │   ├── McpServerConfig.java        # MCP Server + Tool 注册
    │   ├── client/
    │   │   └── KbApiClient.java        # RestClient，调用 kb-app REST API
    │   ├── tools/
    │   │   ├── SearchTool.java
    │   │   ├── QaTool.java
    │   │   ├── DocumentTool.java
    │   │   └── TagTool.java
    │   ├── resources/
    │   │   ├── DocumentResource.java
    │   │   └── SpaceResource.java
    │   └── auth/
    │       └── McpApiKeyFilter.java    # 验证 API Key，获取 JWT 后注入请求上下文
    └── resources/
        └── application.yml
```

### KbApiClient 设计

`KbApiClient` 封装对 `kb-app` 的所有 HTTP 调用，每次 Tool 调用时将已验证的 JWT 透传给 `kb-app`：

```java
@Component
public class KbApiClient {

    private final RestClient restClient;

    // 搜索：POST /api/v1/spaces/{spaceId}/search/hybrid
    public SearchResponse hybridSearch(String spaceId, String query, int topK, String jwt) { ... }

    // 问答：POST /api/v1/spaces/{spaceId}/qa/ask
    public QaResponse askQuestion(String spaceId, String question, String sessionId, String jwt) { ... }

    // 文档列表：GET /api/v1/spaces/{spaceId}/documents
    public PageResponse<DocumentDto> listDocuments(String spaceId, int page, String jwt) { ... }

    // 标签树：GET /api/v1/spaces/{spaceId}/tags/tree
    public List<TagTreeDto> getTagTree(String spaceId, String jwt) { ... }
}
```

---

## 四、MCP Tools 设计

每个 Tool 对应一个客户端可调用的原子操作。

### Tool 1：`search_knowledge_base`
搜索知识库，支持三种模式

```json
{
  "name": "search_knowledge_base",
  "description": "在指定知识空间中搜索相关文档片段，支持语义、关键词和混合搜索",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id":    { "type": "string", "description": "知识空间ID" },
      "query":       { "type": "string", "description": "搜索查询词" },
      "mode":        { "type": "string", "enum": ["semantic", "keyword", "hybrid"], "default": "hybrid" },
      "top_k":       { "type": "integer", "default": 5, "description": "返回结果数量" }
    },
    "required": ["space_id", "query"]
  }
}
```

**返回值：** 文档片段列表，含内容、来源文档名、相关度分数

---

### Tool 2：`ask_question`
对知识库提问，返回 AI 生成答案和引用来源

```json
{
  "name": "ask_question",
  "description": "基于知识库内容回答问题，返回答案和引用的原始文档片段",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id":    { "type": "string" },
      "question":    { "type": "string" },
      "session_id":  { "type": "string", "description": "会话ID，用于多轮对话（可选）" }
    },
    "required": ["space_id", "question"]
  }
}
```

**返回值：** `{ answer, citations: [{doc_title, chunk_content, doc_id}] }`

---

### Tool 3：`list_documents`
列出空间内的文档

```json
{
  "name": "list_documents",
  "description": "列出知识空间中的文档，支持按标签或关键词过滤",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id":  { "type": "string" },
      "tag_ids":   { "type": "array", "items": { "type": "string" } },
      "keyword":   { "type": "string" },
      "page":      { "type": "integer", "default": 1 },
      "page_size": { "type": "integer", "default": 20 }
    },
    "required": ["space_id"]
  }
}
```

---

### Tool 4：`get_document_content`
获取指定文档的完整内容（分块拼接）

```json
{
  "name": "get_document_content",
  "description": "获取指定文档的全文内容",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id": { "type": "string" },
      "doc_id":   { "type": "string" }
    },
    "required": ["space_id", "doc_id"]
  }
}
```

---

### Tool 5：`list_tags`
获取知识空间的标签树

```json
{
  "name": "list_tags",
  "description": "获取知识空间的标签层级树，用于了解知识库的分类结构",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id": { "type": "string" }
    },
    "required": ["space_id"]
  }
}
```

---

### Tool 6：`upload_document`（可选，EDITOR权限）
上传文档到知识空间

```json
{
  "name": "upload_document",
  "description": "上传文档到指定知识空间（需要EDITOR权限）",
  "inputSchema": {
    "type": "object",
    "properties": {
      "space_id":  { "type": "string" },
      "filename":  { "type": "string" },
      "content":   { "type": "string", "description": "Base64编码的文件内容" },
      "mime_type": { "type": "string" }
    },
    "required": ["space_id", "filename", "content", "mime_type"]
  }
}
```

---

## 五、MCP Resources 设计

Resources 是只读的上下文数据，客户端可以订阅。

### Resource 1：`space://{spaceId}/info`
知识空间元信息（名称、描述、文档数、成员数）

### Resource 2：`space://{spaceId}/documents`
知识空间文档列表（精简版，含标题、状态、创建时间）

---

## 六、认证方案

MCP 客户端携带 API Key，`kb-mcp` 将其转换为 JWT 后透传给 `kb-app`，鉴权逻辑完全由 `kb-app` 负责。

### 6.1 API Key 管理（enterprise-kb 侧）
在 `enterprise-kb` 的 `kb-user` 模块中新增：
- `mcp_api_keys` 数据库表（Liquibase migration）：

  ```sql
  CREATE TABLE mcp_api_keys (
      id          BIGSERIAL PRIMARY KEY,
      user_id     BIGINT NOT NULL REFERENCES users(id),
      key_hash    VARCHAR(255) NOT NULL UNIQUE,  -- SHA-256 哈希
      key_prefix  VARCHAR(20) NOT NULL,           -- 展示用前缀，如 "ekb_xxxx"
      name        VARCHAR(100),
      created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
      expires_at  TIMESTAMP,
      deleted_at  TIMESTAMP
  );
  ```
- REST 接口供用户自助生成/撤销 API Key：`POST /api/v1/me/api-keys`、`DELETE /api/v1/me/api-keys/{id}`
- 新增端点 `POST /api/v1/auth/api-key/exchange`：接收 API Key，验证后返回短期 JWT，供 `kb-mcp` 调用

### 6.2 认证流程（kb-mcp 侧）

```
MCP 客户端
  │  X-API-Key: ekb_xxxx
  ▼
McpApiKeyFilter（kb-mcp）
  │  POST /api/v1/auth/api-key/exchange  →  kb-app
  │  ← 返回 JWT
  │  将 JWT 存入 McpRequestContext
  ▼
Tool Handler（kb-mcp）
  │  Authorization: Bearer <jwt>
  ▼
KbApiClient → kb-app 各业务接口
  （鉴权、权限校验均由 kb-app 完成）
```

### 6.3 stdio 模式认证
stdio 模式下 API Key 通过环境变量传入：
```json
{
  "enterprise-kb": {
    "command": "java",
    "args": ["-jar", "/path/to/kb-mcp/target/kb-mcp-*.jar",
             "--spring.ai.mcp.server.transport=stdio"],
    "env": {
      "MCP_API_KEY": "ekb_your_key_here",
      "KB_APP_BASE_URL": "http://localhost:8081"
    }
  }
}
```

---

## 七、技术栈详情

### 7.1 MCP 协议层

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| MCP Server 核心 | `spring-ai-mcp-server-webmvc-spring-boot-starter` | 1.0.0 | Spring AI 官方 MCP Server 实现，支持 stdio 和 SSE 两种传输 |
| MCP SDK 底层 | `io.modelcontextprotocol.sdk:mcp` | 由 Spring AI BOM 管理 | 协议序列化/反序列化，JSON-RPC 处理 |
| SSE 传输实现 | Spring MVC + `SseEmitter` | 内置于 Spring Boot 3.4.1 | 生产环境 HTTP SSE 传输，挂载在 `/mcp/sse` |
| stdio 传输实现 | `McpSyncServer` + `StdioServerTransport` | 同上 | 本地进程通信，供 Claude Desktop 使用 |

> **为什么选 Spring AI MCP 而不是直接用官方 Java SDK？**
> Spring AI MCP Starter 可以零配置注册 Tool、自动处理 JSON Schema 生成，无需手动维护 Tool 描述，与 Spring Bean 体系无缝集成。

---

### 7.2 Web / 传输层

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| Web 框架 | Spring MVC (Servlet) | Spring Boot 3.4.1 | SSE 模式直接复用 |
| HTTP 客户端 | Spring `RestClient` | Spring Boot 3.4.1 | 调用 `kb-app` REST API，内置于 `spring-boot-starter-web` |
| JSON 序列化 | Jackson `ObjectMapper` | 由 Spring Boot 管理 | Tool 输入/输出的 JSON 映射 |
| 虚拟线程 | JDK 21 Virtual Threads | JDK 21 | Tool 调用涉及 HTTP 阻塞，虚拟线程避免线程池耗尽 |

---

### 7.3 认证与安全

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| API Key 验证 | 调用 `kb-app` 的 exchange 接口 | - | kb-mcp 不持有数据库，验证逻辑完全委托给 kb-app |
| JWT 传递 | `McpRequestContext`（ThreadLocal） | - | Filter 解析后存入，Tool Handler 取出透传 |
| 过滤器 | `OncePerRequestFilter` 子类 | Spring Boot 3.4.1 | SSE 模式：HTTP 请求进入 MCP 路由前拦截 |
| stdio 认证 | 环境变量 `MCP_API_KEY` 启动时 exchange 一次 | - | 进程级别固定身份，JWT 过期前自动续期 |

---

### 7.4 限流

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 限流算法 | **Bucket4j** | 8.10.1 | 令牌桶算法，内存模式，按 API Key 隔离限流；无需 Redis |
| 降级方式 | 返回 MCP 错误 `-32603` | - | 超限时返回 `"Rate limit exceeded, retry after Xs"` |

> **为什么不用 Resilience4j？** Bucket4j 更轻量，只需一个依赖，单节点内存限流足够。

---

### 7.5 测试

| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 单元测试 | JUnit 5 + Mockito | Spring Boot Test 内置 | Mock `KbApiClient`，验证 Tool 的 schema 和结果映射 |
| 集成测试 | `@SpringBootTest` + WireMock | WireMock 独立引入 | 模拟 `kb-app` HTTP 响应，端到端验证 Tool 调用链路 |
| MCP 协议验证 | MCP Inspector (`npx @modelcontextprotocol/inspector`) | 最新 | 浏览器 GUI，直接调用 Tools 验证协议正确性 |

---

### 7.6 打包与部署

| 场景 | 方式 | 说明 |
|------|------|------|
| 本地开发 | `mvn package -DskipTests` | 在 `kb-mcp/` 目录执行，产出 fat jar，直接 `java -jar` 运行 |
| Docker 部署（SSE） | `docker compose up -d` | 在 `kb-mcp/` 目录执行，Dockerfile 内完成编译+打包，SSE 端点暴露在 `:8082` |
| Claude Desktop（stdio） | fat jar + stdio 传输 | 同本地产出的 jar，启动时加 `--spring.ai.mcp.server.transport=stdio` |

两种运行模式共用同一个 jar，传输方式由环境变量 `SPRING_AI_MCP_SERVER_TRANSPORT` 切换，`pom.xml` 只需标准插件：

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

---

### 7.7 kb-mcp 完整依赖

```xml
<!-- Spring Boot Web（内嵌 Tomcat，支持 SSE；含 RestClient 和 Jackson） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- MCP Server（Spring AI 官方） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Actuator（健康检查，供 Docker healthcheck 使用） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- 限流 -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>

<!-- 工具 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.x</version>
    <scope>test</scope>
</dependency>
```

---

## 八、实现步骤

### Phase 1：基础框架搭建（约 2 天）
- [ ] 初始化 `kb-mcp` 独立 Maven 项目，配置依赖
- [ ] 实现 `McpServerConfig`，注册 Server 信息和传输方式
- [ ] 实现 `McpApiKeyFilter`，调用 `kb-app` exchange 接口验证 API Key，获取 JWT
- [ ] 实现 `McpRequestContext`，在 Tool 调用链路中传递 JWT
- [ ] 在 `enterprise-kb` 的 `kb-user` 模块新增 `mcp_api_keys` 表（Liquibase migration）及 API Key CRUD 接口
- [ ] 在 `enterprise-kb` 的 `kb-auth` 模块新增 `POST /api/v1/auth/api-key/exchange` 端点

### Phase 2：核心 Tools 实现（约 3 天）
- [ ] 实现 `KbApiClient`，封装对 `kb-app` 的所有 HTTP 调用
- [ ] 实现 `SearchTool`（通过 `KbApiClient` 调用搜索接口）
- [ ] 实现 `QaTool`（通过 `KbApiClient` 调用问答接口）
- [ ] 实现 `DocumentTool`（list + getContent，通过 `KbApiClient`）
- [ ] 实现 `TagTool`（通过 `KbApiClient` 调用标签接口）
- [ ] 编写每个 Tool 的单元测试（Mock `KbApiClient`）

### Phase 3：Resources 和高级功能（约 1 天）
- [ ] 实现 `SpaceResource` 和 `DocumentResource`
- [ ] 实现流式问答 Tool（SSE 模式下，Q&A 结果流式返回）
- [ ] 添加 Bucket4j 限流，按 API Key 隔离

### Phase 4：Docker 与集成（约 1 天）
- [ ] 创建 `kb-mcp/docker/Dockerfile` 和 `kb-mcp/docker-compose.yml`
- [ ] 在 `enterprise-kb/docker-compose.yml` 加入 `kb-network` 网络声明
- [ ] 编写集成测试（WireMock 模拟 `kb-app`）
- [ ] 用 MCP Inspector 端到端验证所有 Tool

---

## 九、关键技术细节

### 9.1 Tool 注册方式
```java
@Configuration
public class McpServerConfig {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> knowledgeBaseTools(
            SearchTool searchTool, QaTool qaTool, DocumentTool documentTool, TagTool tagTool) {
        return List.of(
            searchTool.toolSpec(),
            qaTool.toolSpec(),
            documentTool.listSpec(),
            documentTool.getContentSpec(),
            tagTool.toolSpec()
        );
    }
}
```

### 9.2 JWT 在 Tool 调用链路中的传递
`McpApiKeyFilter` 将 exchange 得到的 JWT 存入 `McpRequestContext`（ThreadLocal），Tool Handler 从中取出，通过 `KbApiClient` 携带在 `Authorization: Bearer` 头中透传给 `kb-app`。`kb-app` 侧无需任何改动，鉴权逻辑完全复用现有 Spring Security 链路。

### 9.3 错误处理
MCP 协议要求错误以结构化 JSON 返回，统一封装：
```java
// API Key 无效   → MCP error code: -32603, message: "Invalid API key"
// 权限不足       → MCP error code: -32603, message: "Access denied to space {spaceId}"
// kb-app 不可达  → MCP error code: -32603, message: "Upstream service unavailable"
// 限流触发       → MCP error code: -32603, message: "Rate limit exceeded, retry after Xs"
```

---

## 十、验证方法

### 本地验证（MCP Inspector）
```bash
# 在 kb-mcp 目录
mvn package -DskipTests
npx @modelcontextprotocol/inspector java \
  -jar target/kb-mcp-*.jar \
  --spring.ai.mcp.server.transport=stdio
# 在浏览器中打开 http://localhost:5173 测试各 Tool
```

### Claude Desktop 集成配置
```json
// ~/Library/Application Support/Claude/claude_desktop_config.json
{
  "mcpServers": {
    "enterprise-kb": {
      "command": "java",
      "args": ["-jar", "/path/to/kb-mcp/target/kb-mcp-*.jar",
               "--spring.ai.mcp.server.transport=stdio"],
      "env": {
        "MCP_API_KEY": "ekb_your_key_here",
        "KB_APP_BASE_URL": "http://localhost:8081"
      }
    }
  }
}
```

---

## 十一、Docker 部署

`kb-mcp` 有自己独立的 `docker-compose.yml`，与 `enterprise-kb` 的 `docker-compose.yml` 完全分离。两者通过共享的外部 Docker 网络 `kb-network` 互通。

### 11.1 文件布局

```
workspace/
├── enterprise-kb/
│   ├── docker-compose.yml      # 已有，需小改（加网络声明）
│   └── docker/
│       ├── Dockerfile
│       └── maven-settings.xml
└── kb-mcp/                     # 独立项目，与 enterprise-kb 平级
    ├── pom.xml
    ├── docker-compose.yml      # kb-mcp 专属 compose 文件
    ├── .env.example
    ├── docker/
    │   ├── Dockerfile
    │   └── maven-settings.xml
    └── src/...
```

---

### 11.2 enterprise-kb/docker-compose.yml 修改（仅加网络声明）

现有文件**只需两处改动**，其他内容不动：

```yaml
# 1. 在 app 服务中加 networks
  app:
    ...
    networks:
      - kb-network        # ← 加入共享网络

# 2. 在文件末尾声明网络
networks:
  kb-network:
    name: kb-network      # 固定网络名，供 kb-mcp 引用
    driver: bridge
```

---

### 11.3 kb-mcp/docker-compose.yml

```yaml
version: '3.9'

services:
  mcp:
    build:
      context: .                         # 构建上下文就是 kb-mcp/ 根目录
      dockerfile: docker/Dockerfile
    container_name: kb-mcp
    ports:
      - "8082:8080"
    env_file:
      - .env
    environment:
      KB_APP_BASE_URL: http://kb-app:8080   # 通过共享网络访问 kb-app 容器
      SPRING_AI_MCP_SERVER_NAME: enterprise-kb
      SPRING_AI_MCP_SERVER_VERSION: 1.0.0
      SPRING_AI_MCP_SERVER_TRANSPORT: sse
      MCP_RATE_LIMIT_RPM: ${MCP_RATE_LIMIT_RPM:-60}
    networks:
      - kb-network
    restart: unless-stopped

networks:
  kb-network:
    external: true                       # 引用已由 enterprise-kb compose 创建的网络
    name: kb-network
```

> `kb-app` 容器名固定为 `kb-app`，`kb-mcp` 直接用 `http://kb-app:8080` 访问，无需 `depends_on`——网络连通即可，重试由 `RestClient` 的重试策略兜底。

---

### 11.4 kb-mcp/docker/Dockerfile

```dockerfile
# Stage 1: Build
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

COPY docker/maven-settings.xml /root/.m2/settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM amazoncorretto:21-al2023-headless
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

---

### 11.5 kb-mcp/src/main/resources/application.yml

```yaml
server:
  port: 8080

spring:
  ai:
    mcp:
      server:
        name: ${SPRING_AI_MCP_SERVER_NAME:enterprise-kb}
        version: ${SPRING_AI_MCP_SERVER_VERSION:1.0.0}
        transport: ${SPRING_AI_MCP_SERVER_TRANSPORT:sse}

kb:
  app:
    base-url: ${KB_APP_BASE_URL:http://localhost:8081}

mcp:
  rate-limit:
    rpm: ${MCP_RATE_LIMIT_RPM:60}
```

---

### 11.6 kb-mcp/.env.example

```dotenv
MCP_RATE_LIMIT_RPM=60
```

---

### 11.7 启动方式

```bash
# 第一步：在 enterprise-kb 目录启动主应用（同时创建 kb-network）
cd enterprise-kb
docker compose up -d

# 第二步：在 kb-mcp 目录启动 MCP 服务（接入已有 kb-network）
cd ../kb-mcp
docker compose up -d

# 查看日志
docker compose logs -f
```

停止时各自独立：
```bash
# 在 kb-mcp 目录
docker compose down           # 只停 MCP

# 在 enterprise-kb 目录
docker compose down           # 停主应用及基础设施
```

---

### 11.8 容器端口与访问

| 容器 | 对外端口 | 用途 |
|------|----------|------|
| `kb-app` | 8081 | 主应用 REST API |
| `kb-mcp` | 8082 | MCP SSE 端点 |

MCP 客户端连接配置：
```json
{
  "mcpServers": {
    "enterprise-kb": {
      "url": "http://<host>:8082/mcp/sse",
      "headers": { "X-API-Key": "ekb_your_key_here" }
    }
  }
}
```

---

## 十二、风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AI 客户端高频调用搜索接口 | Milvus 压力大 | Bucket4j 按 API Key 限流，默认 60 RPM |
| `get_document_content` 返回超大文本 | 超出 LLM 上下文窗口 | 限制最大返回字符数（如 10000 字），超出提示分段查询 |
| stdio 模式 API Key 泄露 | 权限被滥用 | Key 存在环境变量，不写入配置文件；支持随时撤销 |
| kb-app 不可达时 Tool 调用失败 | MCP 客户端报错 | RestClient 配置超时和重试；错误信息统一封装为 MCP 错误码 |
| JWT 过期（stdio 模式长期运行） | 所有 Tool 调用 401 | McpApiKeyFilter 检测到 401 时自动重新 exchange |

---

## 十三、后续扩展方向

- **Prompt Templates**：预定义常用问答 Prompt，供 AI 客户端直接引用
- **知识图谱工具**：暴露文档关系图查询，支持 AI 做关联分析
- **批量操作**：支持批量搜索多个 query，提升 AI Agent 效率
- **Webhook 通知**：文档处理完成后主动推送给 MCP 客户端（需 MCP 支持）
