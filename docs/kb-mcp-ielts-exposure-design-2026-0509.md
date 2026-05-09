# kb-mcp 暴露 kb-ielts 能力设计

> 生成日期：2026-05-08  
> 范围：`kb-mcp` 对外暴露 `kb-ielts` 能力。  
> 视角：MCP `Resource`、`Tool`、`Prompt`。  
> 目标：让 `open-claw` 或其他 AI 客户端可以自然使用 IELTS 学习能力。  
> 当前阶段：按 `kb-mcp` 受信任内部调用 `kb-ielts` 设计。

---

## 1. 核心结论

`kb-mcp` 的定位不是替代 `kb-ielts`，而是把 `kb-ielts` 中适合 AI 使用的能力包装成 MCP 能力。

建议边界：

```text
Resource：稳定、只读、适合作为上下文的学习数据
Tool：需要模型主动调用的查询或业务动作
Prompt：固定学习场景的对话模板和流程引导
```

本次实现后：

- `Resource` 暴露 12 个：7 个固定 Resource + 5 个 Resource Template。
- `Tool` 暴露 31 个。
- `Prompt` 暴露 7 个。

---

## 2. 当前已经暴露的能力

### 2.1 已暴露 Resource

| Resource URI | 来源接口 | 说明 |
| --- | --- | --- |
| `ielts://today/plan` | `GET /api/ielts/study/today` | 今日学习计划 |
| `ielts://study/stats` | `GET /api/ielts/study/stats` | 学习统计 |
| `ielts://profile/summary` | `GET /api/ielts/profile` | 学习档案摘要 |
| `ielts://dashboard/overview` | `GET /api/ielts/dashboard` | 学习仪表盘概览 |
| `ielts://mistakes/recent` | `GET /api/ielts/mistakes/recent` | 最近错题 |
| `ielts://mock-tests/trends` | `GET /api/ielts/mock-tests/trends` | 模考趋势 |
| `ielts://topic-tags` | `GET /api/ielts/topic-tags` | 话题标签 |

这些 Resource 适合作为 AI 会话开始时的背景上下文。

### 2.2 已暴露 Resource Template

| Resource Template | 来源接口 | 说明 |
| --- | --- | --- |
| `ielts://words/{wordId}` | `GET /api/ielts/words/{id}` | 单词详情 |
| `ielts://writing-tasks/{taskId}` | `GET /api/ielts/writing-tasks/{id}` | 写作题详情 |
| `ielts://speaking-topics/{topicId}` | `GET /api/ielts/speaking-topics/{id}` | 口语话题详情 |
| `ielts://study/records/{status}` | `GET /api/ielts/study/records?status={status}` | 按状态读取学习记录 |
| `ielts://grammar-points/{grammarPointId}` | `GET /api/ielts/grammar-points/{id}` | 语法点详情 |

这些模板适合在 AI 已经知道某个 ID 或状态时读取详情。

实现说明：当前版本升级到 Spring AI 1.1.x 后，通过官方 `SyncResourceTemplateSpecification` Bean 注册 Resource Templates，由 Spring AI 官方 Streamable HTTP transport 暴露给 MCP 客户端。

### 2.3 已暴露 Tool

| 分类 | Tool 数量 | 说明 |
| --- | --- | --- |
| 单词/短语 | 4 | 查询单词、查询短语、新增单词、批量导入单词 |
| 内容查询 | 3 | 查询语法点、口语话题、写作题目 |
| 学习闭环 | 10 | 今日计划、学习统计、学习档案、计划建议、仪表盘、学习记录、开始学习、提交复习、最近错题、错因统计 |
| 训练内容 | 9 | 听力、阅读、发音、同义替换、语法练习，以及听说读写专项训练 |
| 写作/模考 | 5 | 作文提交记录、提交作文、模考记录、新增模考、模考趋势 |

### 2.4 已暴露 Prompt

| Prompt | 说明 |
| --- | --- |
| `ielts_daily_coach` | 每日学习教练 |
| `ielts_word_drill` | 单词/短语训练 |
| `ielts_review_session` | 间隔复习会话 |
| `ielts_writing_practice` | 写作练习 |
| `ielts_speaking_practice` | 口语模拟考官 |
| `ielts_weakness_diagnosis` | 薄弱项诊断 |
| `ielts_content_import_assistant` | 内容导入助手 |

---

## 3. Resource 设计

### 3.1 Resource 适合暴露什么

Resource 应该放“模型经常需要先知道、但不一定需要用户显式触发”的上下文。

适合：

- 今日计划
- 学习统计
- 学习档案
- 仪表盘概览
- 最近错题
- 模考趋势
- 内容标签

不适合：

- 新增、修改、删除类操作
- 需要复杂参数的搜索
- 大批量明细数据
- 敏感的跨用户数据

### 3.2 建议 Resource 清单

| 优先级 | Resource URI | 来源接口 | 说明 |
| --- | --- | --- | --- |
| P0 | `ielts://today/plan` | `GET /api/ielts/study/today` | 已有，今日计划 |
| P0 | `ielts://study/stats` | `GET /api/ielts/study/stats` | 已有，学习统计 |
| P1 | `ielts://profile/summary` | `GET /api/ielts/profile` | 学习目标、目标分数、备考阶段 |
| P1 | `ielts://dashboard/overview` | `GET /api/ielts/dashboard` | 首页综合概览 |
| P1 | `ielts://mistakes/recent` | `GET /api/ielts/mistakes/recent` | 最近错题 |
| P2 | `ielts://mock-tests/trends` | `GET /api/ielts/mock-tests/trends` | 模考趋势 |
| P2 | `ielts://topic-tags` | `GET /api/ielts/topic-tags` | 内容标签，辅助模型筛选内容 |

### 3.3 Resource 返回建议

Resource 继续返回 `text/plain` 即可，方便模型直接阅读。

但内容要保持摘要化：

- 不返回完整题库。
- 不返回太长列表。
- 当前阶段不设计跨用户查询。
- 如果后续出现多用户数据，再单独补用户隔离方案。

### 3.4 Resource Template 案例

固定 Resource 适合放“常用背景上下文”，例如今日计划、学习统计。

Resource Template 更适合放“按参数读取某个具体资源”的场景，例如根据内容 ID 读取某个单词、写作题、口语话题详情。

它和 Tool 的区别：

```text
Resource Template：只读某个资源，适合详情页/上下文读取
Tool：执行一个动作，适合搜索、筛选、写入、提交复习等操作
```

#### 案例一：单词详情

```text
Resource Template:
ielts://words/{wordId}

对应接口:
GET /api/ielts/words/{id}

用途:
AI 已经知道某个单词 ID 时，读取该单词完整信息，包括释义、音标、例句、词表、难度、标签等。
```

适用场景：

- 用户正在复习某个单词。
- Tool 查询单词列表后，AI 需要读取其中某个单词的详情。
- Prompt 里要围绕某个单词做例句、联想记忆、复习提问。

#### 案例二：写作题详情

```text
Resource Template:
ielts://writing-tasks/{taskId}

对应接口:
GET /api/ielts/writing-tasks/{id}

用途:
读取指定写作题目的完整信息，例如 Task1/Task2、题目、难度、话题标签、参考方向等。
```

适用场景：

- `ielts_writing_practice` Prompt 选中某个题目后，继续读取题目详情。
- AI 需要基于某道题生成提纲、范文结构、评分反馈。
- 用户指定“就这道题练习”时，AI 读取上下文。

#### 案例三：口语话题详情

```text
Resource Template:
ielts://speaking-topics/{topicId}

对应接口:
GET /api/ielts/speaking-topics/{id}

用途:
读取指定口语话题的完整信息，包括 Part、topic、提示点、话题标签、难度等。
```

适用场景：

- `ielts_speaking_practice` Prompt 选中某个话题后，进入模拟考官模式。
- AI 根据 Part 1/2/3 组织追问。
- 用户要求围绕某个话题做表达优化。

#### 案例四：学习状态记录

```text
Resource Template:
ielts://study/records/{status}

对应接口:
GET /api/ielts/study/records?status={status}

可选 status:
LEARNING
REVIEWING
MASTERED

用途:
读取某一类学习记录，用于复习规划或薄弱项分析。
```

适用场景：

- AI 需要知道当前有哪些内容正在学习。
- AI 需要拉取待复习或已掌握内容做复盘。
- 用户问“我现在复习中的内容有哪些”。

#### 案例五：语法点详情

```text
Resource Template:
ielts://grammar-points/{grammarPointId}

对应接口:
GET /api/ielts/grammar-points/{id}

用途:
读取指定语法点详情，包括解释、例句、分类、难度、话题标签等。
```

适用场景：

- 用户问某个语法点如何使用。
- AI 在语法练习后解释错题。
- 写作批改时需要引用某个语法点做针对性讲解。

#### 已实现的 Resource Template

第一批只做只读详情类，不做写入和删除：

| 优先级 | Resource Template | 说明 |
| --- | --- | --- |
| P1 | `ielts://words/{wordId}` | 单词详情 |
| P1 | `ielts://writing-tasks/{taskId}` | 写作题详情 |
| P1 | `ielts://speaking-topics/{topicId}` | 口语话题详情 |
| P2 | `ielts://grammar-points/{grammarPointId}` | 语法点详情 |
| P2 | `ielts://study/records/{status}` | 按状态读取学习记录 |

当前代码已经实现上述固定 Resource 和 Resource Template。

---

## 4. Tool 设计

### 4.1 Tool 适合暴露什么

Tool 是 AI 可以主动调用的“动作”。

适合：

- 条件查询
- 获取详情
- 开始学习
- 提交复习结果
- 生成计划建议
- 提交作文
- 查询训练材料

需要谨慎：

- 新增内容
- 批量导入
- 更新学习档案
- 创建模考记录

第一阶段不建议暴露：

- 删除类接口
- 管理端全量 CRUD
- 跨用户查询

### 4.2 基础 Tool

基础 Tool 继续保留：

| Tool | 建议 |
| --- | --- |
| `ielts_list_words` | 保留 |
| `ielts_list_phrases` | 保留 |
| `ielts_create_word` | 保留 |
| `ielts_batch_import_words` | 保留 |
| `ielts_list_grammar` | 保留 |
| `ielts_list_speaking_topics` | 保留 |
| `ielts_list_writing_tasks` | 保留 |
| `ielts_get_today_plan` | 保留 |
| `ielts_get_study_stats` | 保留 |

### 4.3 本次新增 Tool

#### P1：补齐学习闭环

| Tool | 来源接口 | 说明 |
| --- | --- | --- |
| `ielts_get_profile` | `GET /api/ielts/profile` | 获取学习档案 |
| `ielts_get_plan_suggestion` | `GET /api/ielts/profile/plan-suggestion` | 获取计划建议 |
| `ielts_get_dashboard` | `GET /api/ielts/dashboard` | 获取综合概览 |
| `ielts_get_study_records` | `GET /api/ielts/study/records` | 查询学习记录 |
| `ielts_start_study` | `POST /api/ielts/study/start` | 开始学习某个内容 |
| `ielts_submit_review` | `POST /api/ielts/study/review` | 提交复习结果 |
| `ielts_get_recent_mistakes` | `GET /api/ielts/mistakes/recent` | 查询最近错题 |
| `ielts_get_mistake_stats` | `GET /api/ielts/mistakes/stats` | 查询错题统计 |

这一组最重要，因为它让 MCP 不只是“查内容”，而是能完成学习动作。

#### P2：扩展训练内容

| Tool | 来源接口 | 说明 |
| --- | --- | --- |
| `ielts_list_listening_items` | `GET /api/ielts/listening-items` | 查询听力素材 |
| `ielts_list_reading_items` | `GET /api/ielts/reading-items` | 查询阅读素材 |
| `ielts_list_pronunciation_points` | `GET /api/ielts/pronunciation-points` | 查询发音点 |
| `ielts_list_paraphrase_groups` | `GET /api/ielts/paraphrase-groups` | 查询同义替换 |
| `ielts_list_grammar_exercises` | `GET /api/ielts/grammar-exercises` | 查询语法练习 |
| `ielts_get_training_listening` | `GET /api/ielts/training/listening` | 获取听力训练 |
| `ielts_get_training_reading` | `GET /api/ielts/training/reading` | 获取阅读训练 |
| `ielts_get_training_writing` | `GET /api/ielts/training/writing` | 获取写作训练 |
| `ielts_get_training_speaking` | `GET /api/ielts/training/speaking` | 获取口语训练 |

#### P2：写作与模考

| Tool | 来源接口 | 说明 |
| --- | --- | --- |
| `ielts_list_writing_submissions` | `GET /api/ielts/writing-submissions` | 查询作文提交记录 |
| `ielts_submit_writing` | `POST /api/ielts/writing-submissions` | 提交作文 |
| `ielts_list_mock_tests` | `GET /api/ielts/mock-tests` | 查询模考记录 |
| `ielts_create_mock_test` | `POST /api/ielts/mock-tests` | 新增模考记录 |
| `ielts_get_mock_test_trends` | `GET /api/ielts/mock-tests/trends` | 查询模考趋势 |

#### P3：内容管理能力

这类能力主要偏内容管理，不建议第一阶段全部开放。

| 能力 | 建议 |
| --- | --- |
| 新增/批量导入短语、语法、听力、阅读、口语、写作题 | 后续再开放 |
| 更新内容 | 后续再开放 |
| 删除内容 | 暂不通过 MCP 暴露 |
| 内容链接维护 | 暂不通过 MCP 暴露，后续给管理端使用 |
| 话题标签维护 | 可以先只暴露查询，写入后置 |

---

## 5. Prompt 设计

### 5.1 Prompt 适合暴露什么

Prompt 不是接口调用本身，而是“固定学习场景的对话模板”。

它适合把多个 Resource 和 Tool 组合成一套稳定体验。

比如：

```text
读取今日计划
读取学习统计
结合用户目标
安排 30 分钟学习
学习后引导用户复习打卡
```

这些逻辑不应该每次都让模型临场发挥，应该沉淀成 Prompt。

### 5.2 建议 Prompt 清单

| Prompt | 使用能力 | 说明 |
| --- | --- | --- |
| `ielts_daily_coach` | `today/plan`、`study/stats`、`get_plan_suggestion` | 每日学习教练，生成当天学习安排 |
| `ielts_word_drill` | `list_words`、`list_phrases`、`submit_review` | 单词/短语训练 |
| `ielts_review_session` | `today/plan`、`get_study_records`、`submit_review` | 间隔复习会话 |
| `ielts_writing_practice` | `list_writing_tasks`、`submit_writing` | 写作练习 |
| `ielts_speaking_practice` | `list_speaking_topics` | 口语模拟考官 |
| `ielts_weakness_diagnosis` | `study/stats`、`mistakes/recent`、`mock-tests/trends` | 薄弱项诊断 |
| `ielts_content_import_assistant` | `create_word`、`batch_import_words` | 内容导入助手 |

### 5.3 Prompt 参数建议

| Prompt | 参数 |
| --- | --- |
| `ielts_daily_coach` | `availableMinutes`、`targetBand`、`focusSkill` |
| `ielts_word_drill` | `difficulty`、`topicTags`、`count` |
| `ielts_review_session` | `durationMinutes`、`contentType` |
| `ielts_writing_practice` | `taskType`、`targetBand` |
| `ielts_speaking_practice` | `part`、`topicTags`、`targetBand` |
| `ielts_weakness_diagnosis` | `days` |
| `ielts_content_import_assistant` | `contentType`、`sourceText` |

### 5.4 Prompt 输出约束

Prompt 里建议统一约束：

- 不编造学习记录。
- 需要真实数据时先读 Resource 或调用 Tool。
- 复习结果只能基于用户反馈提交。
- 当前阶段不设计跨用户数据访问。

---

## 6. 审计建议

当前阶段 `kb-mcp` 直接调用 `kb-ielts`。

因此这版先只做基础边界控制：

- 查询类 Tool 可以直接调用。
- 写入类 Tool 按现有实现直接调用。
- 删除类 Tool 第一阶段不暴露。
- 跨用户查询第一阶段不设计。

需要记录审计：

- Tool 名称
- 调用来源，比如客户端名、IP、会话 ID，能拿到什么先记什么
- 调用时间
- 参数摘要
- 是否写操作
- 执行结果
- 错误信息

---

## 7. 当前落地状态

### 已完成

- 暴露 7 个固定 Resource。
- 暴露 5 个 Resource Template。
- 暴露 31 个 Tool。
- 暴露 7 个 Prompt。
- 继续由 `kb-mcp` 直接调用 `kb-ielts`。
- 不暴露删除类 Tool。

### 后续可继续优化

- 根据使用频率决定是否继续开放内容管理类 Tool。
- 后续如出现多用户场景，再补用户隔离方案。
- 后续如需要生产化，再补更完整的工具调用审计和指标。

---

## 8. 需要补充的问题

1. `kb-mcp` 是否只给 `open-claw` 使用，还是也给其他 AI 客户端使用。
2. 当前阶段是否允许通过 MCP 新增单词，还是只保留查询能力。
3. MCP Prompt 是否作为二期正式能力实现，还是先继续使用现有 `kb-mcp-skills` 过渡。
