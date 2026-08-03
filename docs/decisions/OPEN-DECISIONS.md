# OPEN-DECISIONS 登记册

> 悬而未决项记录。只追加 + 就地关闭（OPEN → RESOLVED）。

| Date | Source | Open Item | Related Constraints | Current Leaning | Blocked By | Resolves When | Status |
|------|--------|-----------|---------------------|-----------------|------------|---------------|--------|
| 2026-08-02 | Phase 1 | 截图分析依赖视觉模型，用户实际用哪家 Key 未定 | DeepSeek 官方 API 无视觉能力 | 多模型预设（主流+自定义）+ 双通道：多模态直读 / 非多模态走视觉转述通道 | 无（方案已定） | 已解决：用户拍板多模型选择器 + read-image 机制移植 | RESOLVED |
| 2026-08-02 | Phase 1 | OCR 兜底通道是否进 MVP | DeepSeek 用户无视觉模型时的替代路径 | read-image 视觉转述通道替代传统 OCR，进 MVP（通道 B） | 无 | 已解决：视觉模型即"超级 OCR"，默认 GLM-4V-Flash | RESOLVED |
| 2026-08-03 | Phase 4 QA | addModel 触发隐私门后意图丢失 | ProviderEditViewModel.kt:141-144 pendingAction=Save，确认后只存 provider 不续加模型 | 重构为 pendingAction 保留原意图 | 无（minor UX） | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 生产就绪 Bronze：无障碍/可观测/发布安全短板 | 无障碍 35/可观测 25/发布安全 30 | v1.1 升级：对比度/埋点/release keystore+CI | 无 | v1.1 | OPEN |
| 2026-08-03 | Phase 4 QA | 危机检测裸词覆盖 | "坚持不下去"（无"了"）不命中 | 后端补齐中 | 无 | 本轮修复 | OPEN |
