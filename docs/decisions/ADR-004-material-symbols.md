# ADR-004: 图标库锁定 Material Symbols（Outlined）

## Status
Accepted (2026-08-02)

## Background
P0 铁律：禁 emoji 图标、禁多套图标库混用。设计方向为 DeepSeek 极简工具风，需与 Material 3 视觉一致、语义清晰、可裁剪、零版权成本。

## Decision
全项目统一 Material Symbols（material-icons-extended，随 Compose BOM），使用 Outlined 风格，尺寸体系 16px 行内 / 20px 按钮 / 24px 独立，设计 Token 同步锁定（uiux.md §2）。R8 裁剪未用图标。

## Consequences
正面：单一来源、与 M3 一致、语义清晰、无版权成本。
负面：extended 包体积偏大，依赖 R8 裁剪控制；后续新增图标必须从库内选择，禁止引入第二套图标库。

## Related ADRs
无
