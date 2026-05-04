---
name: ielts-words
argument-hint: "[查询条件 或 单词内容]"
description: |
  管理雅思单词和短语库：查询、新增、批量导入。
  当用户提到雅思单词、查单词、添加单词、导入词汇、查短语时使用。
---

## 意图识别

| 用户意图 | 调用工具 |
|----------|----------|
| 查询单词列表 | `ielts_list_words` |
| 查询短语列表 | `ielts_list_phrases` |
| 新增单个单词 | `ielts_create_word` |
| 批量导入单词 | `ielts_batch_import_words` |

---

## 查询单词列表

调用 `ielts_list_words`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `difficulty` | ❌ | integer | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `wordList` | ❌ | string | 词表：`AWL` / `GSL` / `IELTS` |
| `topicTags` | ❌ | string | 话题标签，如 `environment`、`technology` |
| `page` | ❌ | integer | 页码，从 1 开始，默认 1 |
| `pageSize` | ❌ | integer | 每页条数，默认 20 |

---

## 查询短语列表

调用 `ielts_list_phrases`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `difficulty` | ❌ | integer | 难度：`1`=简单 / `2`=中等 / `3`=困难 |
| `topicTags` | ❌ | string | 话题标签 |
| `page` | ❌ | integer | 页码，从 1 开始，默认 1 |
| `pageSize` | ❌ | integer | 每页条数，默认 20 |

---

## 新增单词

**执行前展示内容让用户确认。**

调用 `ielts_create_word`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `word` | ✅ | string | 单词原形，如 `ambiguous` |
| `definitionZh` | ✅ | string | 中文释义，多义词用换行分隔 |
| `definitionEn` | ❌ | string | 英文释义 |
| `phoneticUk` | ❌ | string | 英式音标，如 `/æmˈbɪɡjuəs/` |
| `phoneticUs` | ❌ | string | 美式音标 |
| `partOfSpeech` | ❌ | string | 词性，如 `adj.`、`n.`、`v.` |
| `wordList` | ❌ | string | 词表来源：`AWL` / `GSL` / `IELTS` |
| `difficulty` | ❌ | integer | 难度：`1`=基础 / `2`=中级 / `3`=高级 |
| `topicTags` | ❌ | string | 话题标签，逗号分隔，如 `environment,technology` |
| `skillTags` | ❌ | string | 适用技能，逗号分隔，如 `reading,writing` |
| `examples` | ❌ | array | 例句列表，每项包含 `sentence`（英文例句）和 `translation`（中文翻译） |

---

## 批量导入单词

**执行前展示单词列表让用户确认。**

调用 `ielts_batch_import_words`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `words` | ✅ | array | 单词对象数组，每项字段同新增单词（`word` + `definitionZh` 必填） |

## 失败处理

| 场景 | 处理 |
|---|---|
| 单词已存在 | 提示用户该词已在词库中 |
| 必填字段缺失 | 提示用户补充 `word` 和 `definitionZh` |
