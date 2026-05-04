---
name: kb-search
argument-hint: "[搜索关键词]"
description: |
  在知识库中搜索相关文档片段，支持语义、关键词、混合三种模式。
  当用户想在知识库中搜索、查找、检索内容时使用。
---

## 执行流程

### 1. 确认搜索条件

从用户输入中提取：
- `spaceId`（必填）— 不知道时先调用 `list_documents` 获取
- `query`（必填）— 搜索关键词
- `mode`（可选）— 默认 `hybrid`
- `topK`（可选）— 默认 5

### 2. 调用搜索

调用 `search_knowledge_base`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `spaceId` | ✅ | string | 知识空间 ID |
| `query` | ✅ | string | 搜索查询词 |
| `mode` | ❌ | string | `semantic` \| `keyword` \| `hybrid`，默认 `hybrid` |
| `topK` | ❌ | integer | 返回结果数量，默认 5 |

### 3. 展示结果

将搜索结果整理为列表，每条包含：
- 文档标题、相关片段摘要
- 相关度评分

提示用户可以：
- 对某条结果继续追问（使用 kb-ask）
- 查看完整文档（使用 kb-documents）

## 失败处理

| 场景 | 处理 |
|---|---|
| 无搜索结果 | 建议调整关键词或切换搜索模式（如从 hybrid 改为 semantic） |
| spaceId 不存在 | 调用 `list_documents` 重新获取可用空间 |
