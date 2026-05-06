# kb-mcp 操作手册

## 目录

1. [概述](#1-概述)
2. [快速启动](#2-快速启动)
3. [认证与登录](#3-认证与登录)
4. [客户端接入](#4-客户端接入)
5. [MCP Tools 参数说明](#5-mcp-tools-参数说明)
6. [运维操作](#6-运维操作)
7. [常见问题](#7-常见问题)

---

## 1. 概述

`kb-mcp` 是企业知识库的 MCP 接入层，让 Claude Desktop、Cursor、OpenClaw 等 AI 客户端能直接调用知识库的搜索、问答、文档管理等能力。

```
AI 客户端（Claude Desktop / OpenClaw / mcporter）
  │  MCP 协议（Streamable HTTP）
  ▼
kb-mcp :8084
  │  HTTP REST（Docker 内网）
  ▼
kb-app :8081  ──► PostgreSQL / Milvus
kb-ielts :8083
```

**已注册能力**

| 类型 | 名称 | 说明 |
|------|------|------|
| Tool | `search_knowledge_base` | 语义/关键词/混合搜索 |
| Tool | `kb_ask` | 知识库 AI 问答（含引用） |
| Tool | `kb_documents` | 列出空间内文档 |
| Tool | `kb_upload` | 上传文档（需 EDITOR 权限） |
| Tool | `ielts_words` | 雅思单词管理 |
| Tool | `ielts_study` | 雅思学习计划与统计 |
| Resource | `enterprise://spaces` | 知识空间列表 |
| Resource | `enterprise://documents` | 文档列表 |

---

## 2. 快速启动

### 前置条件

- `kb-network` Docker 网络已存在（由 enterprise-kb 创建）
- `kb-app` 和 `kb-ielts` 容器正在运行

### 2.1 启动 kb-app / kb-ielts（依赖服务）

```bash
cd enterprise-kb

# 基础设施（postgres/redis/milvus 等）未启动时：全量启动
docker compose up -d

# 基础设施已运行时：只启动应用层
docker compose up -d app ilets
```

### 2.2 启动 kb-mcp

```bash
cd kb-mcp
cp .env.example .env   # 首次启动时创建 .env

docker compose up -d
docker compose logs -f  # 首次构建需几分钟
```

### 2.3 验证

```bash
curl http://localhost:8084/actuator/health
# {"status":"UP"}
```

启动后需完成登录认证（见第 3 节）才可使用 MCP 功能。

### 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `KB_APP_BASE_URL` | `http://localhost:8081` | Docker 内网填 `http://kb-app:8081` |
| `IELTS_APP_BASE_URL` | `http://localhost:8083` | Docker 内网填 `http://kb-ielts:8083` |
| `MCP_RATE_LIMIT_RPM` | `60` | 每分钟最大请求数 |
| `KB_MCP_TOKEN_FILE` | `/data/.kb-mcp-token` | JWT token 文件路径 |

---

## 3. 认证与登录

kb-mcp 将 JWT token 持久化到文件，无需每次请求携带凭证。

### 登录

```bash
curl -X POST http://localhost:8084/mcp/login \
  -H "Content-Type: application/json" \
  -d '{"username":"用户名","password":"密码"}'
# 成功响应：{"token":"eyJhbGci..."}
```

token 自动保存到容器内 `/data/.kb-mcp-token`，容器重启后无需重新登录。

### 检查状态 / 重新登录

```bash
# 检查登录状态
curl http://localhost:8084/mcp/status
# {"loggedIn": true}

# 重新登录：先删除 token 文件，再调用登录接口
docker compose exec mcp rm /data/.kb-mcp-token
```

---

## 4. 客户端接入

### OpenClaw（推荐）

`~/.openclaw/openclaw.json`：

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

### mcporter

`~/.mcporter/mcporter.json`：

```json
{
  "mcpServers": {
    "kb-mcp": {
      "baseUrl": "http://127.0.0.1:8084/mcp"
    }
  }
}
```

### MCP Inspector（快速验证）

```bash
npx @modelcontextprotocol/inspector
# 浏览器打开 http://localhost:5173
# Transport: HTTP，URL: http://localhost:8084/mcp
```

---

## 5. MCP Tools 参数说明

### `search_knowledge_base`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `spaceId` | 是 | — | 知识空间 ID |
| `query` | 是 | — | 搜索查询词 |
| `mode` | 否 | `hybrid` | `semantic` / `keyword` / `hybrid` |
| `topK` | 否 | `5` | 返回结果数量 |

### `kb_ask`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `spaceId` | 是 | — | 知识空间 ID |
| `query` | 是 | — | 问题 |
| `sessionId` | 否 | — | 传入上次返回值可延续多轮对话 |

### `kb_documents`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `spaceId` | 是 | — | 知识空间 ID |
| `page` | 否 | `0` | 页码（从 0 开始） |
| `size` | 否 | `20` | 每页条数 |

### `kb_upload`

需要 EDITOR 或以上角色，文档异步处理。

| 参数 | 必填 | 说明 |
|------|------|------|
| `spaceId` | 是 | 知识空间 ID |
| `fileName` | 是 | 文件名，如 `report.pdf` |
| `content` | 是 | Base64 编码的文件内容 |

### `ielts_words` / `ielts_study`

`ielts_words` 支持 `action`（`list`/`create`/`review`）、`wordId`、`content` 参数。`ielts_study` 无参数，返回今日学习统计。

---

## 6. 运维操作

```bash
# 启动 / 停止 / 重启
docker compose up -d
docker compose down
docker compose restart mcp

# 查看日志
docker compose logs -f

# 代码变更后重新构建
docker compose build mcp && docker compose up -d

# 健康检查
curl http://localhost:8084/actuator/health
docker compose ps

# 验证 kb-network 网络互通
docker network inspect kb-network --format '{{range .Containers}}{{.Name}} {{end}}'
# 应包含 kb-app、kb-ielts、kb-mcp
```

**限流**：默认每分钟 60 次请求，触发时返回 `HTTP 429`。调整方式：

```bash
MCP_RATE_LIMIT_RPM=120 docker compose up -d
```

---

## 7. 常见问题

**401 Unauthorized** — token 不存在或已过期，重新登录即可。

**`-32600 Missing or invalid Mcp-Session-Id`** — 客户端 transport 类型需设为 `streamable-http`（非旧版 `sse`），重新连接让客户端重新初始化。

**`Access denied to space xxx`** — 当前用户没有该空间访问权限，让空间 ADMIN 在 kb-app 中添加成员。

**`EDITOR permission required`** — 当前角色为 VIEWER，需升级为 EDITOR 或 ADMIN。

**`Upstream service unavailable`** — kb-mcp 无法连接 kb-app，检查：
1. `docker ps | grep kb-app` 确认 kb-app 正在运行
2. `docker network inspect kb-network` 确认两个容器在同一网络
3. `KB_APP_BASE_URL` 是否设为 `http://kb-app:8081`

**429 Too Many Requests** — 等 60 秒或提高 `MCP_RATE_LIMIT_RPM`。

**获取 spaceId**：

```bash
curl http://localhost:8081/api/v1/spaces \
  -H "Authorization: Bearer {accessToken}"
```

---

##### 测试账号

```
admin / Admin@123456
```
