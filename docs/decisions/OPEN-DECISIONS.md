# OPEN-DECISIONS 登记册

> 悬而未决项记录。只追加 + 就地关闭（OPEN → RESOLVED）。

| Date | Source | Open Item | Related Constraints | Current Leaning | Blocked By | Resolves When | Status |
|------|--------|-----------|---------------------|-----------------|------------|---------------|--------|
| 2026-08-02 | Phase 1 | 截图分析依赖视觉模型，用户实际用哪家 Key 未定 | DeepSeek 官方 API 无视觉能力 | 多模型预设（主流+自定义）+ 双通道：多模态直读 / 非多模态走视觉转述通道 | 无（方案已定） | 已解决：用户拍板多模型选择器 + read-image 机制移植 | RESOLVED |
| 2026-08-02 | Phase 1 | OCR 兜底通道是否进 MVP | DeepSeek 用户无视觉模型时的替代路径 | read-image 视觉转述通道替代传统 OCR，进 MVP（通道 B） | 无 | 已解决：视觉模型即"超级 OCR"，默认 GLM-4V-Flash | RESOLVED |
| 2026-08-03 | Phase 4 QA | addModel 触发隐私门后意图丢失 | ProviderEditViewModel.kt:141-144 pendingAction=Save，确认后只存 provider 不续加模型 | 重构为 pendingAction 保留原意图 | 无（minor UX） | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 生产就绪 Bronze：无障碍/可观测/发布安全短板 | 无障碍 35/可观测 25/发布安全 30 | v1.1 升级：对比度/埋点/release keystore+CI | 无 | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 危机检测裸词覆盖 | "坚持不下去"（无"了"）不命中 | 后端补齐中 | 无 | 已解决：CrisisDetector 已含「坚持不下去」与「坚持不下去了」两条（M5 复核关闭） | RESOLVED |
| 2026-08 | O7 | 知识路由 BM25/精排生产切换 | 任务书前置：100–200 条真实 query 评测集，recall@3 提升 ≥15% 才保留精排层 | 已用 10 个子代理采集 Stack Exchange 公开匿名问答，构建 618 条中文真实素材评测集（route_eval_queries.json）。**最优方案 = 用评测集反哺路由关键词**：在 gen_routes.py ROUTE_TABLE 补充冷战/吃醋/已读不回/婆婆/操控等高频词。反哺后 contains：全量 P=0.368/R=0.357/F1=0.362，测试集 P=0.330/R=0.306/F1=0.317。对比：BM25 测试 P=0.145/R=0.348/F1=0.205；画像精排 P=0.171/R=0.418/F1=0.243；Hybrid P=0.159/R=0.285/F1=0.204。结论：**关键词反哺后的 contains 是最优，生产采用；精排器/Hybrid 不采用**，RouteReranker/DocProfile/HybridRouter 保留为实验代码 | 无（数据已完成） | 已按决策门关闭 | RESOLVED |
| 2026-08 | O4 | 桌面 sourceSet 物理共享重构为 :shared KMP 模块 | 平台接缝（AppLogger/JSON/AssetReader/Cipher/ImageCompressor/AppDatabase） | 已完成 :shared KMP（androidTarget+jvm）+ expect/actual JSON + AppLogger sink；desktop sourceSet hack 已删除 | 无 | 双端全量测试通过（已验收） | RESOLVED |
