# ADR-001: 使用 OkHttp + okhttp-sse 自建 LLM Client（弃官方 SDK）

## Status
Accepted (2026-08-02)

## Background
App 为 BYOK 直连多供应商（DeepSeek/智谱/OpenAI/通义/Kimi/Ollama），全部走 OpenAI 兼容 Chat Completions；需要 SSE 流式、可配超时、指数退避重试与停止按钮。官方 openai-java SDK 体积大、对国产模型扩展字段（reasoning_content 等）支持差、更新滞后于厂商。

## Decision
用 OkHttp 4.12.0 + okhttp-sse 4.12.0 自建 LLM Client 单例：统一连接超时 15s / 读超时 60s、指数退避重试 3 次（初始 1s、x2、±20% 抖动）、callbackFlow 封装 SSE 回调并在主线程推送 UI、支持中途停止。请求/响应解析按 OpenAI 兼容格式，错误归一映射见 llm-contract.md。

## Consequences
正面：体积小、全供应商兼容、超时/重试/停止完全可控，无第三方 SDK 锁定。
负面：需自实现 SSE 解析与错误归一（done/error/断流判定），实现必须覆盖 llm-contract.md §3-§5 全部边界。

## Related ADRs
ADR-005（截图双通道复用同一 Client）
