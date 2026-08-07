# ADR-003: 本地存储采用 Room + DataStore

## Status
Accepted (2026-08-02)

## Background
产品核心卖点是数据不出手机、无后端。需多表关联（profile/target/session/message/provider/model）+ 大量设置项（Key/BaseURL/模型选择/主题/隐私确认），且要求响应式刷新与事务一致。

## Decision
Room 2.6+（KSP）管结构化多表：外键级联（message→session、model→provider）、DAO 返回 Flow 响应式；DataStore 1.1 Preferences 管设置项。API Key 经 Android Keystore AES-GCM 加密后存 provider.apiKeyEncrypted（仅密文）。JSON 字段（timeline/refDocs）用平台 org.json 经 TypeConverter 序列化，不新增第三方依赖。

## Consequences
正面：事务/外键/响应式查询/编译期 SQL 校验，纯本地零服务。
负面：需维护 TypeConverter 与版本迁移（v1 基线见 db-schema.md）；release 禁 fallbackToDestructiveMigration。

## Related ADRs
无
