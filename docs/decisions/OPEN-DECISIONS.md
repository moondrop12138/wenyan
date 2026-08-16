# OPEN-DECISIONS 登记册

> 悬而未决项记录。只追加 + 就地关闭（OPEN → RESOLVED）。

| Date | Source | Open Item | Related Constraints | Current Leaning | Blocked By | Resolves When | Status |
|------|--------|-----------|---------------------|-----------------|------------|---------------|--------|
| 2026-08-02 | Phase 1 | 截图分析依赖视觉模型，用户实际用哪家 Key 未定 | DeepSeek 官方 API 无视觉能力 | 多模型预设（主流+自定义）+ 双通道：多模态直读 / 非多模态走视觉转述通道 | 无（方案已定） | 已解决：用户拍板多模型选择器 + read-image 机制移植 | RESOLVED |
| 2026-08-02 | Phase 1 | OCR 兜底通道是否进 MVP | DeepSeek 用户无视觉模型时的替代路径 | read-image 视觉转述通道替代传统 OCR，进 MVP（通道 B） | 无 | 已解决：视觉模型即"超级 OCR"，默认 GLM-4V-Flash | RESOLVED |
| 2026-08-03 | Phase 4 QA | addModel 触发隐私门后意图丢失 | ProviderEditViewModel.kt:141-144 pendingAction=Save，确认后只存 provider 不续加模型 | 重构为 pendingAction 保留原意图 | 无（minor UX） | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 生产就绪 Bronze：无障碍/可观测/发布安全短板 | 无障碍 35/可观测 25/发布安全 30 | v1.1 升级：对比度/埋点/release keystore+CI | 无 | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 危机检测裸词覆盖 | "坚持不下去"（无"了"）不命中 | 后端补齐中 | 无 | 已解决：CrisisDetector 已含「坚持不下去」与「坚持不下去了」两条（M5 复核关闭） | RESOLVED |
| 2026-08 | O7 | 知识路由 BM25/精排生产切换 | 任务书前置：100–200 条真实 query 评测集，recall@3 提升 ≥15% 才保留精排层 | 已用 10 个子代理采集 Stack Exchange 公开匿名问答，构建 618 条中文真实素材评测集；随后补齐 24 份未路由文档的覆盖探针，评测集扩至 1098 条、覆盖 41/41 文档。**原 618 条关键词反哺后 contains：P=0.368/R=0.357/F1=0.362（原生产）**。扩展 41 文档后 1098 条：contains P=0.207/R=0.201/F1=0.204；纯变体 P=0.213/R=0.602/F1=0.315；**HybridVariantRouter 已接线生产（contains 优先，命中 <2 时变体补漏）**：hybridFillOne P=0.348/R=0.649/F1=0.453；对比 hybridFillEmpty P=0.307/R=0.481/F1=0.375 | 无 | 已按决策门关闭；41 文档 query 变体库（820 条）已嵌入 assets，KnowledgeEngine 已切到 HybridVariantRouter（fill-one） | RESOLVED |
| 2026-08 | O4 | 桌面 sourceSet 物理共享重构为 :shared KMP 模块 | 平台接缝（AppLogger/JSON/AssetReader/Cipher/ImageCompressor/AppDatabase） | 已完成 :shared KMP（androidTarget+jvm）+ expect/actual JSON + AppLogger sink；desktop sourceSet hack 已删除 | 无 | 双端全量测试通过（已验收） | RESOLVED |
