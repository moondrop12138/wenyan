# 数据库 Schema - 温言（安卓）v1.7.2

> 依据：SPEC.md §6 + architecture.md §6
> 作者：高见远（架构师）| 日期：2026-08-02（v1.7.2 更新 2026-08-06）
> 状态：Phase 2 技术细化

## 1. 总览

- ORM：Room 2.6+（KSP 编译期校验）；设置项用 DataStore 1.1。
- 全部主键为自增 Long；时间戳统一 epoch millis（Long）。
- JSON 字段（timeline/refDocs）用平台 org.json 序列化（TypeConverter），非新增第三方依赖。
- 唯一外键关系：message→session、model→provider，均 ON DELETE CASCADE。
- **v1.7.2：target 表多行化（多记忆档案）**，`note` 存记忆正文；session 加 `targetId` 归属档案。DB version 4 → 5（MIGRATION_4_5 仅加列，老数据不丢）。

## 2. 表定义

### 2.1 profile（用户档案，MVP 单行）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| mbti | String? | - | null | 四字母 |
| score | Int? | - | null | 0-100 主观评分 |
| strengths | String? | - | null | 主要优势 |
| weaknesses | String? | - | null | 主要短板 |
| createdAt | Long | - | 必填 | 建档时间 |

索引：无（单行读取，ORDER BY id LIMIT 1）。

### 2.2 target（对象档案，v1.7.2 起多行 = 多记忆档案）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| codeName | String | NOT NULL | - | 档案名称（用户可编辑 = 重命名） |
| mbti | String? | - | null | - |
| score | Int? | - | null | 0-100 主观评分 |
| relationStatus | String? | - | null | 当前关系 |
| timeline | String | JSON 字符串 | "[]" | 最近关键事件（org.json 数组） |
| note | String | NOT NULL | "" | **v1.7.2 记忆正文**（跨会话记忆，mergeNote 上限 2000 字；DB v5 新增） |
| createdAt | Long | - | 必填 | - |

索引：无。timeline 示例：`[{"time":"2026-07","event":"..."}]`。
多档案读取：`observeAll()` 按 id DESC（最新在前）；删激活项回退取第一条。

### 2.3 session（会话）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| createdAt | Long | - | 必填 | - |
| scenarioTag | String? | - | null | 场景标签（如 "reply"） |
| refDocs | String | JSON 字符串 | "[]" | 本次引用文档文件名数组 |
| targetId | Long? | - | null | **v1.7.2 所属记忆档案 id**（可空；老会话 null = 未关联 = 注入空档案；DB v5 新增） |

索引：无。refDocs 示例：`["实战话术编排器：从一句回复到后续分支.md"]`。

### 2.4 message（消息）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| sessionId | Long | FK→session.id, CASCADE, INDEX | - | 所属会话 |
| role | String | NOT NULL | - | USER / ASSISTANT |
| type | String | NOT NULL | - | text / image / analysis |
| content | String | NOT NULL | - | 见下 |
| createdAt | Long | - | 必填 | - |

索引：Index(value=["sessionId"])。

content 语义：
- type=text：用户文本或 AI 纯文本回复。
- type=image：压缩后图片 base64（≤1568px/85%，约 200-500KB；历史仅保留压缩图，隐私清除一并删除）。
- type=analysis：AI 五步法 JSON 原文（前端结构化渲染）。

### 2.5 provider（提供商）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| name | String | NOT NULL | - | 显示名 |
| baseUrl | String | NOT NULL | - | API Host，如 https://api.deepseek.com |
| apiKeyEncrypted | String? | - | null | Keystore AES-GCM 密文，可空（自定义可后补） |
| isPreset | Boolean | - | false | 内置预设 |
| sortOrder | Int | - | 0 | 排序 |

索引：无。预设种子见 architecture.md §4.1（DeepSeek/智谱/OpenAI/通义/Kimi，模型名单已核实）。

### 2.6 model（模型）

| 字段 | Kotlin 类型 | 约束 | 默认 | 说明 |
|------|-------------|------|------|------|
| id | Long | PK autoGenerate | 0 | - |
| providerId | Long | FK→provider.id, CASCADE, INDEX | - | 所属提供商 |
| name | String | NOT NULL | - | 模型名（请求 model 字段） |
| supportsVision | Boolean | - | false | 多模态能力（设置页可改） |
| isDefault | Boolean | - | false | 该提供商默认模型 |
| sortOrder | Int | - | 0 | 排序 |

索引：Index(value=["providerId"])。

## 3. DataStore keys（设置项）

| key | 类型 | 默认 | 说明 |
|-----|------|------|------|
| current_model_id | Long? | null | 主模型 id（未选时引导） |
| vision_model_id | Long? | null | 视觉模型 id（通道 B 用，仅列多模态） |
| theme | String | "system" | light / dark / system |
| onboarding_completed | Boolean | false | 首启问卷完成 |
| privacy_ack | Boolean | false | 隐私声明确认（配 Key 前置，AC-18） |
| active_target_id | Long? | null | **v1.7.2 激活记忆档案 id**（新会话默认归属；删空档案回退 null） |
| memory_auto_enabled | Boolean | true | **v1.7.2 自动记忆开关**（回复后自动提炼写入档案；关闭完全禁用） |

实现：Preferences DataStore 单例 `val Context.settingsDataStore by preferencesDataStore(name = "settings")`。

## 4. 迁移策略

- v1 基线：上述 6 表 + 索引 + 外键，Room version = 1。
- exportSchema = true：schema JSON 提交入库（app/schemas/），作为后续迁移对照基线。
- 后续版本一律用显式 Migration 对象（如 MIGRATION_1_2）+ 迁移测试；release 禁止 fallbackToDestructiveMigration（仅 debug 开发期可用）。
- TypeConverter 变更需同步更新历史数据迁移（如 timeline 结构变化）。
- **v4→v5（MIGRATION_4_5，两条 ALTER，老数据不丢）**：
  ```sql
  ALTER TABLE target ADD COLUMN note TEXT NOT NULL DEFAULT ''
  ALTER TABLE session ADD COLUMN targetId INTEGER   -- 可空，无 DEFAULT
  ```

## 5. 与数据源分层

- Repository 层注入 DAO + DataStore，UI/ViewModel 不直接触碰 SQL。
- DAO 读操作返回 Flow（响应式刷新：消息列表/模型列表），写操作用 suspend。
