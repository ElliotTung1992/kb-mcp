---
name: ielts-content
argument-hint: "[语法 / 口语话题 / 写作题目]"
description: |
  查询雅思语法要点、口语话题和写作题目。
  当用户提到语法要点、口语话题、写作题目、Task1、Task2、Part1/2/3 时使用。
---

## 意图识别

| 用户意图 | 调用工具 |
|----------|----------|
| 语法要点、语法分类 | `ielts_list_grammar` |
| 口语话题、Part1/2/3 | `ielts_list_speaking_topics` |
| 写作题目、Task1、Task2 | `ielts_list_writing_tasks` |

---

## 语法要点列表

调用 `ielts_list_grammar`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `difficulty` | ❌ | integer | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `category` | ❌ | string | 语法分类，如 `tense`、`clause`、`conditional` |
| `topicTags` | ❌ | string | 话题标签 |
| `page` | ❌ | integer | 页码，从 1 开始，默认 1 |
| `pageSize` | ❌ | integer | 每页条数，默认 10 |

---

## 口语话题列表

调用 `ielts_list_speaking_topics`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `difficulty` | ❌ | integer | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `part` | ❌ | integer | 口语 Part：`1` / `2` / `3` |
| `topicTags` | ❌ | string | 话题标签，如 `education`、`travel` |
| `page` | ❌ | integer | 页码，从 1 开始，默认 1 |
| `pageSize` | ❌ | integer | 每页条数，默认 10 |

---

## 写作题目列表

调用 `ielts_list_writing_tasks`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `difficulty` | ❌ | integer | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `taskType` | ❌ | integer | 任务类型：`1`=Task1（图表描述）/ `2`=Task2（议论文） |
| `page` | ❌ | integer | 页码，从 1 开始，默认 1 |
| `pageSize` | ❌ | integer | 每页条数，默认 10 |

---

## 失败处理

| 场景 | 处理 |
|---|---|
| 无相关内容 | 建议调整筛选条件或告知库中暂无该类内容 |
| 服务不可用 | 提示检查 kb-mcp 服务连接状态 |
