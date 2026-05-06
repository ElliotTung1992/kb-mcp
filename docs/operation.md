# kb-mcp 操作手册

## 目录

1. [概述](#1-概述)
2. [快速启动](#2-快速启动)
3. [客户端接入](#3-客户端接入)
4. [MCP Tools 参数说明](#4-mcp-tools-参数说明)
5. [运维操作](#5-运维操作)
6. [常见问题](#6-常见问题)

---

## 1. 概述

`kb-mcp` 是雅思学习系统的 MCP 接入层，让 Claude Desktop、Cursor、OpenClaw 等 AI 客户端能直接调用雅思单词、语法、口语、写作内容管理以及学习计划查询等能力。

```
AI 客户端（Claude Desktop / OpenClaw / mcporter）
  │  MCP 协议（Streamable HTTP）
  ▼
kb-mcp :8084
  │  HTTP REST（Docker 内网）
  ▼
kb-ielts :8083  ──► PostgreSQL
```

**已注册能力**

| 类型 | 名称 | 说明 |
|------|------|------|
| Tool | `ielts_list_words` | 查询雅思单词列表，支持难度/词表/话题筛选 |
| Tool | `ielts_list_phrases` | 查询雅思短语列表 |
| Tool | `ielts_create_word` | 新增雅思单词 |
| Tool | `ielts_batch_import_words` | 批量导入雅思单词 |
| Tool | `ielts_list_grammar` | 查询语法要点列表 |
| Tool | `ielts_list_speaking_topics` | 查询口语话题列表 |
| Tool | `ielts_list_writing_tasks` | 查询写作题目列表 |
| Tool | `ielts_get_today_plan` | 获取今日学习计划 |
| Tool | `ielts_get_study_stats` | 获取学习统计数据 |
| Resource | `ielts://today/plan` | 今日学习计划（背景上下文） |
| Resource | `ielts://study/stats` | 学习统计（背景上下文） |

---

## 2. 快速启动

### 前置条件

- `ielts-network` Docker 网络已存在（由 kb-ielts 创建）
- `kb-ielts` 容器正在运行

### 2.1 启动 kb-ielts（依赖服务）

```bash
cd kb-ielts
cp .env.example .env   # 首次启动时创建 .env，填写 PG_PASSWORD
docker compose up -d
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

### 环境变量说明

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `IELTS_APP_BASE_URL` | `http://localhost:8083` | Docker 内网填 `http://kb-ielts:8083` |
| `MCP_RATE_LIMIT_RPM` | `60` | 每分钟最大请求数 |

---

## 3. 客户端接入

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

## 4. MCP Tools 参数说明

### `ielts_list_words`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `difficulty` | 否 | — | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `wordList` | 否 | — | 词表：`AWL` / `GSL` / `IELTS` |
| `topicTags` | 否 | — | 话题标签，如 `environment` |
| `page` | 否 | `1` | 页码（从 1 开始） |
| `pageSize` | 否 | `20` | 每页条数 |

### `ielts_create_word`

| 参数 | 必填 | 说明 |
|------|------|------|
| `word` | 是 | 单词原形 |
| `definitionZh` | 是 | 中文释义 |
| `definitionEn` | 否 | 英文释义 |
| `phoneticUk` | 否 | 英式音标 |
| `phoneticUs` | 否 | 美式音标 |
| `partOfSpeech` | 否 | 词性，如 `adj.` |
| `wordList` | 否 | `AWL` / `GSL` / `IELTS` |
| `difficulty` | 否 | `1`=基础 / `2`=中级 / `3`=高级 |
| `topicTags` | 否 | 话题标签，逗号分隔 |
| `skillTags` | 否 | 适用技能，逗号分隔 |
| `relatedWords` | 否 | 关联词，逗号分隔 |
| `examples` | 否 | 例句列表，每项含 `sentence` 和 `translation` |

### `ielts_batch_import_words`

| 参数 | 必填 | 说明 |
|------|------|------|
| `words` | 是 | 单词对象数组，每项至少含 `word` 和 `definitionZh` |

### `ielts_list_grammar`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `difficulty` | 否 | — | 难度 1-3 |
| `category` | 否 | — | 分类，如 `tense`、`clause` |
| `topicTags` | 否 | — | 话题标签 |
| `page` / `pageSize` | 否 | `1` / `10` | 分页 |

### `ielts_list_speaking_topics`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `difficulty` | 否 | — | 难度 1-3 |
| `part` | 否 | — | Part 1 / 2 / 3 |
| `topicTags` | 否 | — | 话题标签 |
| `page` / `pageSize` | 否 | `1` / `10` | 分页 |

### `ielts_list_writing_tasks`

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `difficulty` | 否 | — | 难度 1-3 |
| `taskType` | 否 | — | `1`=Task1 / `2`=Task2 |
| `page` / `pageSize` | 否 | `1` / `10` | 分页 |

### `ielts_get_today_plan` / `ielts_get_study_stats`

无参数。

---

## 5. 运维操作

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
```

**限流**：默认每分钟 60 次请求，触发时返回 `HTTP 429`。调整方式：

```bash
MCP_RATE_LIMIT_RPM=120 docker compose up -d
```

---

## 6. 常见问题

**`-32600 Missing or invalid Mcp-Session-Id`** — 客户端 transport 类型需设为 `streamable-http`（非旧版 `sse`），重新连接让客户端重新初始化。

**`Upstream service unavailable`** — kb-mcp 无法连接 kb-ielts，检查：
1. `docker ps | grep kb-ielts` 确认 kb-ielts 正在运行
2. `IELTS_APP_BASE_URL` 是否设为 `http://kb-ielts:8083`

**429 Too Many Requests** — 等 60 秒或提高 `MCP_RATE_LIMIT_RPM`。
