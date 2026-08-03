# ADR-002: 知识库采用规则路由而非本地 RAG

## Status
Accepted (2026-08-02)

## Background
40 份静态 md 全量约 396KB ≈ 20 万 token，不可整体注入；本地向量 RAG 需要 embedding（联网或本地模型）与向量库，与"纯本地、零成本、离线"卖点冲突。SKILL.md 原设计即"按需加载 1-3 份"。

## Decision
编译期脚本由 SKILL.md「按需加载」表 + 文档标题关键词生成场景→文档路由表 JSON；运行时按用户输入/场景命中 1-3 份，按 `##` 分块 + 关键词命中章节截断，单份 ≤4K token 注入 system；结果 citations 回显文件名。不使用 embedding 与向量库。

## Consequences
正面：零依赖、离线可用、token 可控、可解释（文件名回显）。
负面：路由表为静态规则，文档增改需重跑脚本；新场景需人工维护映射（构建脚本校验完整性，缺失即失败，AC-17）。

## Related ADRs
无
