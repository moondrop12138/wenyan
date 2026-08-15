# LLM 契约细化 - 狗头军师（安卓）v1.0

> **L14 版本注**：本文档描述 v1.5 旧契约，与 v1.6+ 代码不一致——当前：不再发送 `temperature`；回答统一四段结构 schema v2（原五步法 JSON 兼容映射）；历史 token 预算 24K（CJK 1 字≈1 token 口径）。以代码 `ChatRequestBuilder` / `AnalysisParser` / `HistoryCompactor` 为准。

> 依据：SPEC.md §5（外部 API 契约）+ architecture.md §4/§5
> 作者：高见远（架构师）| 日期：2026-08-02
> 状态：Phase 2 技术细化

## 1. 范围

本文件细化 App 与外部 LLM（OpenAI 兼容 Chat Completions）的完整契约：请求构造、SSE 流式解析、错误归一、重试策略、图片压缩管线。App 无自有后端，唯一出网 = 用户主动分析时请求用户配置的第三方服务。

## 2. 请求构造

### 2.1 端点与认证

- 端点：`POST {provider.baseUrl}/chat/completions`
- 认证：`Authorization: Bearer {provider.apiKey}`
- 内容类型：`Content-Type: application/json`
- 版本锚定：okhttp 4.12.0 / okhttp-sse 4.12.0

### 2.2 纯文本流式请求（完整示例）

```json
POST https://api.deepseek.com/chat/completions
Authorization: Bearer sk-xxxx
Content-Type: application/json

{
  "model": "deepseek-v4-pro",
  "stream": true,
  "temperature": 0.7,
  "messages": [
    {
      "role": "system",
      "content": "【system-核心】\n你是\"狗头军师\"...\n\n【system-档案】\n{\"me\":{\"mbti\":null,...}}\n\n【system-知识】\n【知识文档 #1】《实战话术编排器：从一句回复到后续分支.md》\n...【知识文档结束 #1】"
    },
    {
      "role": "user",
      "content": "以下是用户粘贴的聊天记录，请按五步法分析：\n【聊天记录开始】\n...【聊天记录结束】"
    }
  ]
}
```

说明：
- system 三层（核心/档案/知识）拼为**单条** system 消息，用 `【system-核心】/【system-档案】/【system-知识】` 分隔——多供应商兼容性最好（部分厂商对多条 system 消息支持不一致）。
- 分层模板原文见 prompt-architecture.md。

### 2.3 多模态请求（通道 A，content 数组）

```json
{
  "model": "gpt-5.6-terra",
  "stream": true,
  "temperature": 0.7,
  "messages": [
    {"role": "system", "content": "...同上三层..."},
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "以下是用户聊天截图，请按五步法分析。"},
        {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,/9j/4AAQ..."}}
      ]
    }
  ]
}
```

说明：
- 仅主模型 supportsVision=true 时使用（通道 A），否则走通道 B（见 ADR-005）。
- base64 无换行（Base64.NO_WRAP）；不传 detail 参数（默认 auto，供应商兼容）。

## 3. SSE 解析规范

### 3.1 事件流格式

OpenAI 兼容流式响应为 SSE：每行 `data: {json}`，事件间空行分隔；结束事件 `data: [DONE]`。okhttp-sse 的 EventSource.onEvent(event, data) 每次回调 = 一个 data 行。

### 3.2 增量 chunk 结构

```json
{"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":1780000000,"model":"deepseek-v4-pro","choices":[{"index":0,"delta":{"role":"assistant","content":"先"},"finish_reason":null}]}
```

解析规则：
- 首个 chunk 的 delta.role 用于初始化（App 固定 assistant 气泡，可忽略）。
- 累加 `choices[0].delta.content`；delta.content 为 null 或缺失时跳过（部分模型第一帧只有 role）。
- 若存在 `choices[0].delta.reasoning_content`（深度思考模型），App 单独走 Thinking 通道上报 UI，**不拼入正文**；UI 用折叠面板展示（默认收起，DeepSeek 客户端风格），用户可展开查看完整推理过程。
- 每收到 delta 立即经 MutableStateFlow 推送 UI（callbackFlow + Dispatchers.Main.immediate），禁止在 okhttp 回调线程直接改 UI（已知坑：SSE 回调在后台线程）。

### 3.3 done 判定（三条件满足其一）

1. `data: [DONE]`
2. `choices[0].finish_reason` 非 null（"stop"/"length"）
3. 读超时（60s 无任何数据）→ 按断流处理

### 3.4 错误事件（流中）

- 部分厂商在流中返回 `data: {"error":{"message":"...","type":"..."}}` 而非 HTTP 错误。每帧必须先检查顶层是否含 `error` 键，命中则停止累加，按错误 message 归一。
- keep-alive：SSE 注释行（`:` 开头）与空 data 忽略；未知 event 名忽略，不中断。

### 3.5 停止与取消

- 用户点"停止"→ 调 eventSource.cancel() + 取消 callbackFlow，已渲染内容保留，不计为错误。
- 会话退出/页面销毁时同步取消，防泄漏。

## 4. 错误码→App 文案映射

| 场景 | 判定 | App 文案 | 可重试 |
|------|------|----------|--------|
| 401 | HTTP 401 | "API Key 无效，请到设置检查" | 否 |
| 403 | HTTP 403 | "服务拒绝访问，请检查账户状态" | 否 |
| 404 | HTTP 404 | "模型不存在，请检查模型名（可能已退役）" | 否 |
| 429 | HTTP 429 或流中 error.type=rate_limit | "请求过于频繁或额度已用尽，稍后重试" | 是（退避） |
| 5xx | HTTP 500/502/503 | "模型服务异常，请稍后重试" | 是（退避） |
| 连接超时 | 15s 无响应 | "连接超时，请检查网络或服务地址" | 是（退避） |
| 读超时/断流 | 60s 无数据或流中终止 | "连接中断，可重试或停止" | 是（退避） |
| 流中 error | 帧含 error 键 | 取 error.message 截断展示，前缀"模型返回错误：" | 否 |
| 空内容 | done 但全文为空 | "模型未返回内容，请重试" | 是（1 次） |
| JSON 解析失败 | 流无法解析为合法 chunk | "响应格式异常，请重试或更换模型" | 否 |

错误统一走 ErrorMapper 单例，UI 只认文案 + 可重试标记 + 错误码（日志/埋点用）。

## 5. 指数退避重试策略

| 参数 | 值 | 说明 |
|------|-----|------|
| 初始延迟 | 1000 ms | 第一次重试前等待 |
| 倍数 | 2 | delay = 1000 * 2^(n-1) |
| 最大重试次数 | 3 | 总尝试 = 1 次原始 + 3 次重试 |
| 抖动 | ±20% | 随机抖动防羊群 |
| 超时 | 连接 15s / 读 60s | 分开计 |

规则：
- 仅对可重试错误重试（429/5xx/超时/断流）；401/403/404/流中错误/JSON 解析失败直接失败，不重试。
- 若 429 响应含 Retry-After 头，等待值取 max(退避延迟, Retry-After)，封顶 60s。
- 任一重试仍失败 → 按第 4 节映射表展示文案，停止。
- 用户点"停止"立即取消整个重试链。

## 6. 图片压缩管线

输入规格（锁定，SPEC §5.3）：
- 原图 > 20MB → 拒绝并提示"图片过大，请选择更小的图片"。
- 最长边 ≤ 1568px、JPEG 质量 85%。

处理步骤：
1. 解码前读边界（BitmapFactory.Options.inJustDecodeBounds）计算 inSampleSize，防 OOM。
2. 等比缩放：最长边压到 1568px（保持宽高比）。
3. 压缩：JPEG quality=85，ByteArrayOutputStream。
4. 编码：Base64.NO_WRAP 得到 data URL 的 body。
5. 组装：data:image/jpeg;base64,{body} 注入 image_url。

Token 估算公式（结果页"本次消耗估算"展示用）：
- 估算 token = ceil(宽 x 高 / 750)（OpenAI low-detail 近似，保守偏高）。
- 上限参考：1568x1568 ≈ 3278 token；常见 16:9 截图 ≈ 1.5K-2.5K token。
- 通道 B 额外增加一次视觉转述调用，按文本 token 计；结果页同时展示转述与分析的消耗估算。

## 7. 上下文预算汇总（PromptBuilder 保证）

| 层 | 预算 | 说明 |
|----|------|------|
| system-核心 | ~1.5K token | SKILL.md 精简版 |
| system-档案 | ~0.5K token | 问卷 JSON |
| system-知识 | 4-12K token | 1-3 份文档，单份 ≤4K |
| 用户消息 | 视输入 | 粘贴文本/截图转述 |
| 合计 | ≤ ~16K token | 模型上下文 ≥32K，留足余量 |
