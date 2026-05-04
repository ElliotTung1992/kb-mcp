# kb-mcp 操作手册

## 目录

1. [概述](#1-概述)
2. [快速启动](#2-快速启动)
3. [认证与登录](#3-认证与登录)
4. [客户端接入](#4-客户端接入)
5. [MCP Tools 使用说明](#5-mcp-tools-使用说明)
6. [MCP Resources 使用说明](#6-mcp-resources-使用说明)
7. [限流说明](#7-限流说明)
8. [运维操作](#8-运维操作)
9. [常见问题](#9-常见问题)

---

## 1. 概述

`kb-mcp` 是企业知识库的 MCP（Model Context Protocol）接入层，让 Claude Desktop、Cursor、OpenClaw 等 AI 客户端能够直接调用知识库的搜索、问答、文档管理等能力。

### 架构位置

```
AI 客户端（Claude Desktop / OpenClaw / mcporter）
  │  MCP 协议（Streamable HTTP）
  ▼
kb-mcp :8084          ← 本项目（认证 + 限流 + Tool/Resource 注册）
  │  HTTP REST（Docker 内网）
  ▼
kb-app :8081          ← 企业知识库主应用（鉴权、业务逻辑均由此处理）
  │
  ├─► PostgreSQL       — 用户/空间/文档元数据
  └─► Milvus           — 向量索引
```

### 传输协议

**Streamable HTTP**（MCP 2024-11-05 规范）：
- `POST /mcp` — 所有 MCP 消息入口（initialize、tools/call 等）
- `DELETE /mcp` — 关闭 MCP 会话

> 实现说明：Spring AI 1.0.0 仅内置旧 HTTP+SSE transport，本项目通过自定义
> `WebMvcStreamableHttpServerTransportProvider` 实现 Streamable HTTP，
> 并通过 `@ConditionalOnMissingBean` 替换 Spring AI 默认传输层。

### 已注册能力

| 类型 | 名称 | 说明 |
|------|------|------|
| Tool | `search_knowledge_base` | 语义/关键词/混合搜索 |
| Tool | `kb_ask` | 基于知识库的 AI 问答（含引用） |
| Tool | `kb_documents` | 列出空间内文档 |
| Tool | `kb_upload` | 上传文档（需 EDITOR 权限） |
| Tool | `ielts_words` | 雅思单词管理 |
| Tool | `ielts_study` | 雅思学习计划与统计 |
| Resource | `enterprise://spaces` | 知识空间列表 |
| Resource | `enterprise://documents` | 文档列表 |

---

## 2. 快速启动

### 前置条件

- `enterprise-kb` 已启动，并已创建 `kb-network` Docker 网络
- kb-app 中已有用户账号（用于登录）

### 2.1 Docker 方式（推荐）

```bash
# 在 kb-mcp 目录
cd kb-mcp

# 首次启动前先登录（见第 3 节）
# 之后启动不需要额外操作
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

# 启动
java -jar target/kb-mcp-*.jar \
  --kb.app.base-url=http://localhost:8081
```

### 2.3 验证服务健康

```bash
curl http://localhost:8084/actuator/health
# {"status":"UP"}
```

### 2.4 服务端口

| 容器/服务 | 对外端口 | 说明 |
|-----------|----------|------|
| kb-mcp | 8084 | MCP Streamable HTTP 端点、健康检查 |
| kb-app | 8081 | 知识库主应用 REST API |

### 2.5 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `KB_APP_BASE_URL` | `http://localhost:8081` | kb-app 地址（Docker 内网填 `http://kb-app:8081`） |
| `IELTS_APP_BASE_URL` | `http://localhost:8083` | kb-ielts 地址 |
| `SPRING_AI_MCP_SERVER_NAME` | `enterprise-kb` | MCP 服务器名称（客户端可见） |
| `SPRING_AI_MCP_SERVER_VERSION` | `1.0.0` | MCP 服务器版本 |
| `KB_MCP_TOKEN_FILE` | `/data/.kb-mcp-token` | JWT token 文件路径 |
| `MCP_RATE_LIMIT_RPM` | `60` | 每分钟最大请求数（全局） |

---

## 3. 认证与登录

kb-mcp 使用 **JWT token 文件** 存储认证状态，无需每次请求携带凭证。

### 3.1 登录

```bash
curl -X POST http://localhost:8084/mcp/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}'
```

**成功响应**：
```json
{"token":"eyJhbGci..."}
```

JWT 自动保存到容器内 `/data/.kb-mcp-token`（对应宿主机 Docker volume）。

### 3.2 检查登录状态

```bash
curl http://localhost:8084/mcp/status
# {"loggedIn": true}
```

### 3.3 登出（删除 token）

登录成功后，删除 token 文件即可重新登录：

```bash
docker compose exec mcp rm /data/.kb-mcp-token
```

### 认证流程说明

1. 客户端调用 `POST /mcp/login` → kb-mcp 向 kb-app 验证用户名密码
2. kb-mcp 将拿到的 JWT 保存到 token 文件
3. 后续所有 `/mcp` 请求，kb-mcp 从文件读取 JWT 传给 kb-app
4. JWT 过期后需重新登录

---

## 4. 客户端接入

### 4.1 OpenClaw（推荐）

`~/.openclaw/openclaw.json` 中添加或更新：

```json
{
  "mcp": {
    "servers": {
      "kb-mcp": {
        "url": "http://localhost:8084/mcp",
        "transport": "streamable-http"
      }
    }
  }
}
```

首次使用前需确保已登录（token 文件存在）。

### 4.2 mcporter

在 `~/.mcporter/mcporter.json` 中添加：

```json
{
  "mcpServers": {
    "kb-mcp": {
      "baseUrl": "http://127.0.0.1:8084/mcp"
    }
  }
}
```

mcporter 默认使用 HTTP transport，kb-mcp 的 Streamable HTTP 端点兼容此模式。

### 4.3 MCP Inspector 快速验证

```bash
npx @modelcontextprotocol/inspector
```

在浏览器（`http://localhost:5173`）中填入：

| 字段 | 值 |
|------|----|
| Transport | HTTP |
| URL | `http://localhost:8084/mcp` |

Inspector 会自动尝试初始化连接（确保已登录）。

### 4.4 手动 cURL 测试

Streamable HTTP 使用 `Mcp-Session-Id` 维持会话，需按顺序执行：

```bash
# 1. 登录（写入 token 文件）
curl -X POST http://localhost:8084/mcp/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}'

# 2. initialize（返回 Mcp-Session-Id 响应头）
SESSION_ID=$(curl -sD - -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  | grep -i "^mcp-session-id" | awk '{print $2}' | tr -d '\r')

# 3. 发送 initialized 通知（激活会话）
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 4. 查看 tools 列表
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 5. 调用 tool（以搜索为例）
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_knowledge_base","arguments":{"spaceId":"你的spaceId","query":"测试"}}}'

# 6. 关闭会话
curl -X DELETE http://localhost:8084/mcp \
  -H "Mcp-Session-Id: $SESSION_ID"
```

---

## 5. MCP Tools 使用说明

所有 Tool 均以 `spaceId` 作为必填参数来定位目标知识空间。

### 5.1 `search_knowledge_base` — 搜索知识库

在指定知识空间中搜索相关文档片段。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `spaceId` | string | 是 | — | 知识空间 ID |
| `query` | string | 是 | — | 搜索查询词 |
| `mode` | string | 否 | `hybrid` | 搜索模式：`semantic` / `keyword` / `hybrid` |
| `topK` | integer | 否 | `5` | 返回结果数量 |

### 5.2 `kb_ask` — 知识库问答

基于知识库内容回答问题，返回 AI 生成的答案和引用来源。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `spaceId` | string | 是 | — | 知识空间 ID |
| `query` | string | 是 | — | 要提问的问题 |
| `sessionId` | string | 否 | — | 会话 ID，传入上次返回的值可延续多轮对话上下文 |

**多轮对话**：将返回的 `sessionId` 传入下一个问题，AI 会记住上下文。

### 5.3 `kb_documents` — 列出文档

列出知识空间中的文档，返回标题、状态、分块数等元数据。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `spaceId` | string | 是 | — | 知识空间 ID |
| `page` | integer | 否 | `0` | 页码，从 0 开始 |
| `size` | integer | 否 | `20` | 每页条数 |

### 5.4 `kb_upload` — 上传文档

上传文档到指定知识空间，文档将在后台异步处理。**需要 EDITOR 或以上角色**。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `spaceId` | string | 是 | 知识空间 ID |
| `fileName` | string | 是 | 文件名，如 `report.pdf`、`guide.md` |
| `content` | string | 是 | **Base64 编码**的文件内容 |

### 5.5 `ielts_words` — 雅思单词管理

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `action` | 否 | string | 操作类型：`list`、`create`、`review` |
| `wordId` | 否 | string | 单词 ID（删除/复习时传入） |
| `content` | 否 | object | 单词内容（创建时传入） |

### 5.6 `ielts_study` — 雅思学习计划

无参数。返回今日计划数、已完成数和待复习内容。

---

## 6. MCP Resources 使用说明

Resources 是只读的上下文数据，客户端可以读取后作为背景信息提供给 AI。

### 6.1 `enterprise://spaces` — 知识空间列表

### 6.2 `enterprise://documents` — 文档列表

---

## 7. 限流说明

`kb-mcp` 对所有 `/mcp` 请求统一限流，防止高频调用导致后端压力过大。

| 配置项 | 默认值 | 环境变量 |
|--------|--------|----------|
| 每分钟最大请求数 | 60 | `MCP_RATE_LIMIT_RPM` |

**触发限流时的响应**：

```
HTTP 429 Too Many Requests
{"error":"Rate limit exceeded, retry after 60s"}
```

限流是**全局内存令牌桶**（Bucket4j），重启后令牌桶重置。如需调整：

```bash
MCP_RATE_LIMIT_RPM=120 docker compose up -d
```

---

## 8. 运维操作

### 8.1 启动与停止

```bash
# 启动 MCP 服务
cd kb-mcp && docker compose up -d

# 查看实时日志
docker compose logs -f

# 停止 MCP 服务
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
curl http://localhost:8084/actuator/health

# 容器健康状态
docker compose ps
```

### 8.4 与 enterprise-kb 的网络关系

两个项目通过外部 Docker 网络 `kb-network` 互通，需按以下顺序启动：

```bash
# 第一步：启动 enterprise-kb（同时创建 kb-network）
cd enterprise-kb && docker compose up -d

# 第二步：启动 kb-mcp（接入已有 kb-network）
cd ../kb-mcp && docker compose up -d
```

### 8.5 查看 spaceId

`spaceId` 是 UUID 格式，可从 kb-app 接口获取：

```bash
curl http://localhost:8081/api/v1/spaces \
  -H "Authorization: Bearer {accessToken}"
```

---

## 9. 常见问题

### Q1：调用接口返回 `401 Unauthorized`

**原因**：未登录，token 文件不存在、内容为空，或 JWT 已过期。

**解决方法**：

```bash
# 重新登录（自动刷新 token 文件）
curl -X POST http://localhost:8084/mcp/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}'

# 确认登录状态
curl http://localhost:8084/mcp/status
```

### Q1.1：MCP 客户端报 `-32600 Missing or invalid Mcp-Session-Id`

**原因**：Streamable HTTP 需先完成 `initialize` 握手拿到 `Mcp-Session-Id`，后续请求必须携带该 header。

**解决方法**：确认客户端配置的 transport 类型为 `streamable-http`（而非旧版 `sse`），并重新连接让客户端重新初始化。

### Q2：Tool 调用返回 `Access denied to space xxx`

**原因**：当前用户没有目标空间的访问权限。

**解决方法**：让空间的 ADMIN 成员在 kb-app 中将该用户添加为空间成员。

### Q3：`kb_upload` 返回 `Access denied: EDITOR permission required`

**原因**：当前用户在目标空间的角色为 `VIEWER`，无上传权限。

**解决方法**：将该用户的角色升级为 `EDITOR` 或 `ADMIN`。

### Q4：Tool 调用返回 `Upstream service unavailable`

**原因**：`kb-mcp` 无法连接到 `kb-app`。

**排查步骤**：
1. 确认 `enterprise-kb` 已启动：`docker compose ps`（在 enterprise-kb 目录）
2. 确认两个项目的容器都在 `kb-network` 中：`docker network inspect kb-network`
3. 检查 `KB_APP_BASE_URL` 环境变量是否正确（Docker 内网应为 `http://kb-app:8081`）

### Q5：触发限流（429）

**原因**：超过每分钟 60 次请求。

**解决方法**：
- 短期：等待 60 秒后重试
- 长期：提高 `MCP_RATE_LIMIT_RPM` 并重启服务

### Q6：JWT token 过期

kb-app 颁发的 JWT 有有效期，过期后需要重新登录。删除 token 文件后重新调用 `/mcp/login` 即可。

---

##### 测试账号密码

```
admin
Admin@123456
```
