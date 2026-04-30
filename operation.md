# kb-mcp 操作手册

## 目录

1. [概述](#1-概述)
2. [快速启动](#2-快速启动)
3. [API Key 管理](#3-api-key-管理)
4. [客户端接入](#4-客户端接入)
5. [MCP Tools 使用说明](#5-mcp-tools-使用说明)
6. [MCP Resources 使用说明](#6-mcp-resources-使用说明)
7. [限流说明](#7-限流说明)
8. [运维操作](#8-运维操作)
9. [常见问题](#9-常见问题)

---

## 1. 概述

`kb-mcp` 是企业知识库的 MCP（Model Context Protocol）接入层，让 Claude Desktop、Cursor 等 AI 客户端能够直接调用知识库的搜索、问答、文档管理等能力。

### 架构位置

```
AI 客户端（Claude Desktop / Cursor / MCP Inspector）
  │  MCP 协议（SSE 或 stdio）
  ▼
kb-mcp :8082          ← 本项目（认证 + 限流 + Tool/Resource 注册）
  │  HTTP REST（Docker 内网）
  ▼
kb-app :8081          ← 企业知识库主应用（鉴权、业务逻辑均由此处理）
  │
  ├─► PostgreSQL       — 用户/空间/文档元数据
  └─► Milvus           — 向量索引
```

### 已注册能力

| 类型 | 名称 | 说明 |
|------|------|------|
| Tool | `search_knowledge_base` | 语义/关键词/混合搜索 |
| Tool | `ask_question` | 基于知识库的 AI 问答（含引用） |
| Tool | `list_documents` | 列出空间内文档 |
| Tool | `get_document_content` | 获取文档全文 |
| Tool | `list_tags` | 获取标签层级树 |
| Tool | `upload_document` | 上传文档（需 EDITOR 权限） |
| Resource | `space://{spaceId}/info` | 知识空间元信息 |
| Resource | `space://{spaceId}/documents` | 空间文档列表（精简版） |

---

## 2. 快速启动

### 前置条件

- `enterprise-kb` 已启动，并已创建 `kb-network` Docker 网络
- 已获取 API Key（见 [第 3 节](#3-api-key-管理)）

### 2.1 Docker 方式（推荐）

```bash
# 在 kb-mcp 目录
cd kb-mcp

# 复制并按需修改环境变量（通常保持默认即可）
cp .env.example .env

# 启动 MCP 服务
docker compose up -d

# 查看日志
docker compose logs -f
```

### 2.2 本地 JAR 方式（开发调试）

```bash
cd kb-mcp

# 打包（需要 JDK 21）
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn package -DskipTests

# SSE 模式启动（默认）
java -jar target/kb-mcp-*.jar \
  --kb.app.base-url=http://localhost:8081

# stdio 模式启动（供 Claude Desktop 直连）
java -jar target/kb-mcp-*.jar \
  --spring.ai.mcp.server.stdio=true \
  --kb.app.base-url=http://localhost:8081
```

### 2.3 验证服务健康

```bash
curl http://localhost:8082/actuator/health
# {"status":"UP"}
```

### 2.4 服务端口

| 容器/服务 | 对外端口 | 说明 |
|-----------|----------|------|
| kb-mcp | 8082 | MCP SSE 端点、健康检查 |
| kb-app | 8081 | 知识库主应用 REST API |

### 2.5 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `KB_APP_BASE_URL` | `http://localhost:8081` | kb-app 地址（Docker 内网填 `http://kb-app:8080`） |
| `SPRING_AI_MCP_SERVER_NAME` | `enterprise-kb` | MCP 服务器名称（客户端可见） |
| `SPRING_AI_MCP_SERVER_VERSION` | `1.0.0` | MCP 服务器版本 |
| `SPRING_AI_MCP_SERVER_STDIO` | `false` | `true` 切换为 stdio 传输模式 |
| `SPRING_AI_MCP_SERVER_SSE_ENDPOINT` | `/sse` | SSE 端点路径 |
| `MCP_RATE_LIMIT_RPM` | `60` | 每个 API Key 每分钟最大请求数 |

---

## 3. API Key 管理

API Key 在 `enterprise-kb` 侧管理（需先登录 kb-app）。Key 格式为 `ekb_xxxx`，创建后**仅展示一次**，请立即保存。

### 3.1 创建 API Key

```bash
curl -X POST http://localhost:8081/api/v1/me/api-keys \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Claude Desktop - 张三",
    "expiresAt": "2027-01-01T00:00:00Z"
  }'
```

> `expiresAt` 可选，不填则永不过期。

**返回示例**：

```json
{
  "data": {
    "key": "ekb_a1b2c3d4e5f6...",
    "meta": {
      "id": "uuid",
      "keyPrefix": "ekb_a1b2",
      "name": "Claude Desktop - 张三",
      "expiresAt": "2027-01-01T00:00:00Z",
      "createdAt": "2026-04-24T10:00:00Z"
    }
  },
  "message": "API key created. Save the key now — it will not be shown again."
}
```

### 3.2 查看已创建的 API Key

```bash
curl http://localhost:8081/api/v1/me/api-keys \
  -H "Authorization: Bearer {accessToken}"
```

出于安全考虑，列表只展示 `keyPrefix`（前 8 位），不返回完整 Key。

### 3.3 撤销 API Key

```bash
curl -X DELETE http://localhost:8081/api/v1/me/api-keys/{keyId} \
  -H "Authorization: Bearer {accessToken}"
```

撤销后，使用该 Key 的客户端将立即收到 `401 Unauthorized`，可用于应对 Key 泄露场景。

---

## 4. 客户端接入

### 4.1 SSE 模式（Claude Desktop / Cursor / 自定义客户端）

**Claude Desktop**（`~/Library/Application Support/Claude/claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "enterprise-kb": {
      "url": "http://localhost:8082/sse",
      "headers": {
        "X-API-Key": "ekb_your_key_here"
      }
    }
  }
}
```

修改配置后重启 Claude Desktop 生效。在对话框中点击工具图标，可看到 `enterprise-kb` 下的所有 Tools。

**Cursor**（Settings → MCP → Add Server）：

```json
{
  "enterprise-kb": {
    "url": "http://localhost:8082/sse",
    "headers": {
      "X-API-Key": "ekb_your_key_here"
    }
  }
}
```

### 4.2 stdio 模式（Claude Desktop 直连 JAR，无 HTTP 开销）

先打包 JAR：

```bash
cd kb-mcp
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn package -DskipTests
```

**Claude Desktop 配置**：

```json
{
  "mcpServers": {
    "enterprise-kb": {
      "command": "java",
      "args": ["-jar", "/path/to/kb-mcp/target/kb-mcp-1.0.0-SNAPSHOT.jar"],
      "env": {
        "SPRING_AI_MCP_SERVER_STDIO": "true",
        "KB_APP_BASE_URL": "http://localhost:8081",
        "MCP_API_KEY": "ekb_your_key_here"
      }
    }
  }
}
```

> **注意**：stdio 模式下的 API Key 认证（从环境变量启动时预换取 JWT）尚未实现，建议生产环境使用 SSE 模式。

### 4.3 MCP Inspector 快速验证

无需任何客户端，用浏览器直接测试 Tools：

```bash
npx @modelcontextprotocol/inspector
```

在浏览器（`http://localhost:5173`）中填入：

| 字段 | 值 |
|------|----|
| Transport | SSE |
| URL | `http://localhost:8082/sse` |
| Header Name | `X-API-Key` |
| Header Value | `ekb_your_key_here` |

点击 **Connect** 后可在左侧看到所有 Tools，点击任意 Tool 填参数并运行。

---

## 5. MCP Tools 使用说明

所有 Tool 均以 `spaceId` 作为必填参数来定位目标知识空间。`spaceId` 可从 kb-app 的空间列表接口获取。

### 5.1 `search_knowledge_base` — 搜索知识库

在指定知识空间中搜索相关文档片段。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `space_id` | string | 是 | — | 知识空间 ID |
| `query` | string | 是 | — | 搜索查询词 |
| `mode` | string | 否 | `hybrid` | 搜索模式：`semantic`（语义）/ `keyword`（关键词）/ `hybrid`（混合，效果最佳） |
| `top_k` | integer | 否 | `5` | 返回结果数量 |

**调用示例（在 Claude 对话中）**：

> 请在知识空间 `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` 中搜索「数据库连接池配置」

**返回示例**：

```
搜索模式：hybrid，找到 3 条结果：

1. **数据库配置指南.pdf**
   相关度：0.948
   摘录：HikariCP 最大连接数通过 maximum-pool-size 参数配置，默认值为 10...

2. **部署手册 v2.md**
   相关度：0.821
   摘录：生产环境建议将连接池上限调整为 50...
```

**模式选择建议**：

| 场景 | 推荐模式 |
|------|----------|
| 自然语言问题 / 概念查询 | `semantic` |
| 精确技术名词 / 代码符号 | `keyword` |
| 通用场景（默认） | `hybrid` |

---

### 5.2 `ask_question` — 知识库问答

基于知识库内容回答问题，返回 AI 生成的答案和引用来源。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `space_id` | string | 是 | — | 知识空间 ID |
| `question` | string | 是 | — | 要提问的问题 |
| `session_id` | string | 否 | — | 会话 ID，传入上次返回的值可延续多轮对话上下文 |

**返回示例**：

```
**答案：**
根据知识库中的配置文档，HikariCP 默认最大连接数为 20，最小空闲连接数为 5。

**引用来源：**
1. 数据库配置指南.pdf（第 3 页）
   > maximum-pool-size: 20, minimum-idle: 5, connection-timeout: 30000

*sessionId: session-uuid-abc123*
```

**多轮对话**：将返回的 `sessionId` 传入下一个问题，AI 会记住上下文：

> 第二次提问时传入 session_id: `session-uuid-abc123`

---

### 5.3 `list_documents` — 列出文档

列出知识空间中的文档，返回标题、状态、分块数等元数据。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `space_id` | string | 是 | — | 知识空间 ID |
| `page` | integer | 否 | `0` | 页码，从 0 开始 |
| `page_size` | integer | 否 | `20` | 每页条数 |

**返回示例**：

```
共 5 个文档（第 1 页）：

- **数据库配置指南.pdf**（ID: uuid-1） 状态: READY 分块数: 23
- **API 接口文档.md**（ID: uuid-2） 状态: READY 分块数: 41
- **上传中的文档.docx**（ID: uuid-3） 状态: PROCESSING 分块数: 0
```

**文档状态说明**：

| 状态 | 含义 |
|------|------|
| `PENDING` | 已上传，等待处理 |
| `PROCESSING` | 正在解析/分块/向量化 |
| `READY` | 处理完成，可搜索 |
| `FAILED` | 处理失败（可在 kb-app 触发重处理） |

---

### 5.4 `get_document_content` — 获取文档全文

获取指定文档的完整文本内容（所有分块拼接）。内容超过 10000 字时自动截断，并提示分段查询。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `space_id` | string | 是 | 知识空间 ID |
| `doc_id` | string | 是 | 文档 ID（从 `list_documents` 结果中获取） |

> **提示**：若文档尚未处理完成（状态非 `READY`），返回"文档内容为空或尚未处理完成"。

---

### 5.5 `list_tags` — 获取标签树

获取知识空间的标签层级结构，用于了解知识库的分类体系，也可将标签 ID 传给 kb-app 搜索接口做精确筛选。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `space_id` | string | 是 | 知识空间 ID |

**返回示例**：

```
标签层级结构：

- 研发（CATEGORY） ID: tag-uuid-1
  - 后端（TAG） ID: tag-uuid-2
  - 前端（TAG） ID: tag-uuid-3
- 运维（CATEGORY） ID: tag-uuid-4
  - 部署规范（TOPIC） ID: tag-uuid-5
```

---

### 5.6 `upload_document` — 上传文档

上传文档到指定知识空间，文档将在后台异步处理。**需要 EDITOR 或以上角色**。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `space_id` | string | 是 | 知识空间 ID |
| `filename` | string | 是 | 文件名，如 `report.pdf`、`guide.md` |
| `content` | string | 是 | **Base64 编码**的文件内容 |
| `mime_type` | string | 是 | MIME 类型（见下表） |

**支持的 MIME 类型**：

| 文件格式 | mime_type |
|----------|-----------|
| PDF | `application/pdf` |
| Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| Markdown | `text/markdown` |
| 纯文本 | `text/plain` |
| HTML | `text/html` |

**Base64 编码示例**（在终端准备内容）：

```bash
base64 -i report.pdf | tr -d '\n'
```

**返回示例**：

```
上传成功！文档 ID：uuid-new-doc，文件名：report.pdf，状态：PENDING。
文档将在后台异步处理，完成后可通过 get_document_content 获取全文内容。
```

> 上传成功后，文档需要几秒至几分钟完成处理（取决于文件大小），处理完成后状态变为 `READY`，才能被搜索和问答引用。

---

## 6. MCP Resources 使用说明

Resources 是只读的上下文数据，客户端可以读取后作为背景信息提供给 AI。

### 6.1 `space://{spaceId}/info` — 知识空间元信息

读取知识空间的基本信息，包括名称、slug、描述、首选 AI 模型。

**URI 示例**：`space://xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/info`

**返回内容示例**：

```
知识空间信息
名称：技术文档
slug：tech-docs
描述：研发团队技术规范与设计文档
首选模型：MINIMAX
```

### 6.2 `space://{spaceId}/documents` — 文档列表（精简版）

读取知识空间内最多 50 个文档的精简列表（标题、ID、状态、创建时间），适合在问答前让 AI 了解知识库全貌。

**URI 示例**：`space://xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/documents`

**返回内容示例**：

```
文档列表（共 12 个）：

- 数据库配置指南.pdf（ID: uuid-1） 状态: READY 创建时间: 2026-03-01T10:00:00Z
- API 接口文档.md（ID: uuid-2） 状态: READY 创建时间: 2026-03-05T14:30:00Z
- 部署手册 v2.docx（ID: uuid-3） 状态: READY 创建时间: 2026-04-10T09:00:00Z
```

---

## 7. 限流说明

`kb-mcp` 对每个 API Key 独立限流，防止 AI 客户端高频调用导致后端压力过大。

| 配置项 | 默认值 | 环境变量 |
|--------|--------|----------|
| 每分钟最大请求数 | 60 | `MCP_RATE_LIMIT_RPM` |

**触发限流时的响应**：

```
HTTP 429 Too Many Requests
{"error":"Rate limit exceeded, retry after 60s"}
```

限流是**内存令牌桶**（Bucket4j），按 API Key 独立计算，重启服务后令牌桶重置。如需调整：

```bash
# 在 docker-compose 或启动命令中修改
MCP_RATE_LIMIT_RPM=120
```

---

## 8. 运维操作

### 8.1 启动与停止

```bash
# 启动 MCP 服务
cd kb-mcp && docker compose up -d

# 查看实时日志
docker compose logs -f

# 停止 MCP 服务（不影响 kb-app 和数据库）
docker compose down

# 重启 MCP 服务
docker compose restart mcp
```

### 8.2 重新构建镜像

代码变更后需重新构建：

```bash
cd kb-mcp
docker compose build mcp && docker compose up -d
```

### 8.3 健康检查

```bash
# 服务健康状态
curl http://localhost:8082/actuator/health
# {"status":"UP"}

# 容器健康状态
docker compose ps
```

### 8.4 查看请求日志

日志中会记录限流触发事件（`WARN` 级别）：

```bash
docker compose logs mcp | grep "Rate limit"
```

### 8.5 与 enterprise-kb 的网络关系

两个项目通过外部 Docker 网络 `kb-network` 互通，需按以下顺序启动：

```bash
# 第一步：启动 enterprise-kb（同时创建 kb-network）
cd enterprise-kb && docker compose up -d

# 第二步：启动 kb-mcp（接入已有 kb-network）
cd ../kb-mcp && docker compose up -d
```

停止时各自独立，顺序不限：

```bash
cd kb-mcp && docker compose down      # 只停 MCP
cd enterprise-kb && docker compose down   # 停主应用及基础设施
```

### 8.6 查看 spaceId

`spaceId` 是 UUID 格式，可从 kb-app 接口获取：

```bash
curl http://localhost:8081/api/v1/spaces \
  -H "Authorization: Bearer {accessToken}"
```

---

## 9. 常见问题

### Q1：客户端连接后报 `401 Unauthorized`

**原因**：`X-API-Key` 缺失、错误，或 Key 已被撤销/过期。

**排查步骤**：
1. 确认请求头名称为 `X-API-Key`（区分大小写）
2. 在 kb-app 检查 Key 是否仍有效：`GET /api/v1/me/api-keys`
3. 如 Key 已过期，重新创建一个
4. 确认 `KB_APP_BASE_URL` 指向可访问的 kb-app 地址

### Q2：Tool 调用返回 `Access denied to space xxx`

**原因**：当前 API Key 对应的用户没有目标空间的访问权限。

**解决方法**：让空间的 ADMIN 成员在 kb-app 中将该用户添加为空间成员：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/members \
  -H "Authorization: Bearer {adminToken}" \
  -H "Content-Type: application/json" \
  -d '{"userId": "用户UUID", "role": "VIEWER"}'
```

### Q3：`upload_document` 返回 `Access denied: EDITOR permission required`

**原因**：该 API Key 对应用户在目标空间的角色为 `VIEWER`，无上传权限。

**解决方法**：将该用户的角色升级为 `EDITOR` 或 `ADMIN`：

```bash
curl -X PUT http://localhost:8081/api/v1/spaces/{spaceId}/members/{userId} \
  -H "Authorization: Bearer {adminToken}" \
  -H "Content-Type: application/json" \
  -d '{"role": "EDITOR"}'
```

### Q4：Tool 调用返回 `Upstream service unavailable`

**原因**：`kb-mcp` 无法连接到 `kb-app`。

**排查步骤**：
1. 确认 `enterprise-kb` 已启动：`docker compose ps`（在 enterprise-kb 目录）
2. 确认两个项目的容器都在 `kb-network` 中：`docker network inspect kb-network`
3. 检查 `KB_APP_BASE_URL` 环境变量是否正确（Docker 内网应为 `http://kb-app:8080`）

### Q5：文档上传成功但搜索不到内容

**原因**：文档仍在后台处理，尚未完成向量化。

**解决方法**：
1. 调用 `list_documents` 确认文档状态
2. 状态为 `READY` 后才能被搜索到
3. 如状态变为 `FAILED`，可在 kb-app 触发重处理：
   ```bash
   curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId}/reprocess \
     -H "Authorization: Bearer {accessToken}"
   ```

### Q6：触发限流（429）

**原因**：单个 API Key 超过每分钟 60 次请求。

**解决方法**：
- 短期：等待 60 秒后重试
- 长期：在 docker-compose 或 `.env` 中提高 `MCP_RATE_LIMIT_RPM` 并重启服务

### Q7：`get_document_content` 返回内容被截断

**原因**：文档超过 10000 字，`kb-mcp` 自动截断以避免超出 LLM 上下文窗口。

**解决方法**：使用 `search_knowledge_base` + 关键词定位具体段落，或直接调用 kb-app 原始接口获取完整内容：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId}/content \
  -H "Authorization: Bearer {accessToken}"
```





##### 测试账号密码

```
admin
Admin@123456
```



