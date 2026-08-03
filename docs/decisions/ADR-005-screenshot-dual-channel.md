# ADR-005: 截图分析采用双通道（多模态直读 / read-image 视觉转述）

## Status
Accepted (2026-08-02)

## Background
DeepSeek 官方 API 无视觉；部分用户只有文本模型 Key，但截图是高频输入。read-image 机制已在 WorkBuddy 端验证（视觉模型即"超级 OCR"）。需同时覆盖有视觉/无视觉两类模型。

## Decision
按主模型 supportsVision 分流：
- 通道 A：多模态直读。压缩图（最长边 ≤1568px、JPEG 85%）base64 以 image_url 直接随消息发送，模型直接五步分析。
- 通道 B：视觉转述。先调用户自选视觉模型（visionModelId，默认 GLM-4V-Flash 预设）提取聊天记录文字 → 展示可编辑转述卡（"AI 从截图中读出了这些内容"）→ 用户确认后交主模型五步分析。

两种通道输出均标注三态：截图能证明 / 转述提示 / 仍未知（不脑补）。

## Consequences
正面：无视觉 Key 也可用截图；转述可见可改，符合"只把可见原文当事实"证据边界。
负面：通道 B 增加一次调用与 token 消耗；转述可能有误差，依赖用户确认环节兜底；视觉模型未配置时需引导到设置页。

## Related ADRs
ADR-001（双通道复用同一 LLM Client）
