---
name: kb-upload
argument-hint: "[文件路径或内容]"
description: |
  上传文档到知识库，支持 PDF、Markdown、TXT、Word 等格式。
  当用户想上传文件、导入文档、添加资料到知识库时使用。
---

## 执行流程

### 1. 确认上传参数

从用户输入中提取：
- `spaceId`（必填）— 不知道时先调用 `list_documents` 获取
- `filename`（必填）— 含扩展名的文件名
- `content`（必填）— 文件的 Base64 编码内容
- `mimeType`（必填）— 根据文件类型确定

**常用 MIME 类型对照：**

| 文件类型 | mimeType |
|----------|----------|
| PDF | `application/pdf` |
| Markdown | `text/markdown` |
| 纯文本 | `text/plain` |
| Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

### 2. 上传前确认

展示以下内容让用户确认后再执行：
- 目标知识空间
- 文件名
- 文件类型

### 3. 调用上传

调用 `upload_document`：

| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `spaceId` | ✅ | string | 知识空间 ID |
| `filename` | ✅ | string | 文件名，如 `report.pdf` |
| `content` | ✅ | string | Base64 编码的文件内容 |
| `mimeType` | ✅ | string | 文件 MIME 类型 |

### 4. 展示结果

- 上传成功：提示文档 ID、文件名、当前状态，说明后台异步向量化中，可通过 `list_documents` 查看处理进度
- 上传失败：说明失败原因

## 失败处理

| 场景 | 处理 |
|---|---|
| 无 EDITOR 权限 | 告知用户需要知识空间编辑权限 |
| 文件格式不支持 | 列出支持的格式，建议转换后重试 |
| Base64 编码错误 | 提示用户确认文件内容完整性 |
