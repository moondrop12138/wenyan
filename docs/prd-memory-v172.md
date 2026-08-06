# PRD - 温言 v1.7.2 增量：跨会话记忆 + 多记忆档案 + 会话档案归属

> 作者：许清楚（PM）｜日期：2026-08-06
> 类型：简单版增量 PRD（基于已发版 v1.7.1，仅描述本迭代变更，不含竞品/市场分析）
> 依据：迭代计划 v2（`~/.workbuddy/plans/wenyan-v172-memory.md`，需求与 UI 设计以它为准）
> 状态：待团队确认后进入开发

---

## 1. 产品目标

1. **跨会话记忆**：让 AI 在不同会话间记住「咨询对象」的关键信息，解决现状"每开新会话即失忆"的问题，咨询体验连续、自洽。
2. **多对象档案隔离**：支持用户同时咨询多个对象（A/B/C…），各自记忆互不混乱；档案可新建、改名、编辑、删除，激活状态可切换。
3. **归属可感知**：历史会话清晰标注基于哪个人的记忆，回看/续聊上下文始终自洽，切档案只影响新会话、不污染老会话。

## 2. 用户故事

- 作为**同时咨询多个对象的用户**，我想要在设置页切换「记忆档案」，以便各对象的记忆互不混淆，切换时明确知道当前在用谁的记忆。
- 作为**长期使用者**，我想要 AI 在回复后自动提炼并记住关于对象的新事实，以便记忆自动积累、无需手动记录。
- 作为**谨慎的用户**，我想要删除记忆前二次确认，以便避免误删长期积累的记忆。
- 作为**回看历史的用户**，我想要会话列表标注所属档案，以便一眼看出这段对话是基于哪个人的记忆。
- 作为**发现记忆有误的用户**，我想要编辑记忆名称与正文，以便修正自动提炼产生的错误或不准确内容。

## 3. 需求池

### P0（本迭代必须交付）

| # | 需求 | 说明 | 验收要点 |
|---|------|------|----------|
| R1 | target 表单行→多行（多记忆档案） | `TargetEntity` +`note`（记忆正文，默认空串）；`TargetDao` 多行 CRUD（observeAll/getById/update/deleteById，保留 insert/clear，删 getLatest/observeLatest）；DB v4→v5，`MIGRATION_4_5` 两条 ALTER（target.note 带 DEFAULT ''；session.targetId 可空） | 老数据不丢；新档案可增删改查 |
| R2 | 会话绑定档案 | `SessionEntity` +`targetId`（可空）；新会话创建时写入当前激活档案 id；聊天注入 = 会话归属档案优先；老会话 targetId=null → 注入空档案=现状行为 | 历史会话归属固定、续聊自洽 |
| R3 | 设置页「记忆」分组 | 位置：模型服务之后、外观之前；复用 `SettingsSectionHeader` + `SettingsRow`（玻璃行）+ 现有弹窗风格 | 与设置其他分组视觉一致 |
| R4 | 多档案 CRUD + 激活切换 | 新建（名称，空白不允许）/ 改名 + 编辑正文 / 删除（AlertDialog 二次确认，danger 确认钮）；删除激活档案→自动激活剩余第一个，无剩余→null | 删除必须二次确认；删除后激活状态正确回退 |
| R5 | 切换激活 Toast | 点档案行主体 → 切换激活 → Toast「已切换到「X」的记忆」 | Toast 文案精确 |
| R6 | 自动记忆提炼 | 新文件 `domain/MemoryExtractor.kt`（纯逻辑）；三处回复 Done 后挂点；仅新话题（首话题或 !isSameTopic）提炼一次；`memory_auto_enabled` 开关默认开；20s 超时、失败静默、merge 去重（上限 2000 字） | 关闭开关完全禁用；重复触发不重复追加 |
| R7 | Prompt 注入记忆 | `buildProfileJson` 的 target 加 `memory` 字段（=note）；CorePrompt【system-档案】段追加记忆使用规则（基于已记住信息保持一致、不得矛盾、未提到不得编造） | 记忆生效于对话上下文 |
| R8 | 会话列表档案 Tag | 抽屉 `SessionItemContent` 标题行右侧加 Tag；显示规则 `name.trim().take(4)`（≤4 字全显，>4 字截前 4 字）；targetName=null 不显示 | 老会话无标签、正常显示 |
| R9 | 隐私清除联动 | DataStore 新增 `active_target_id`、`memory_auto_enabled` 两 key；`wipeAll()` 清全部 key + target/session 表 | 一键清除后记忆与档案全部消失 |

### P1（后续迭代，本版本不做）

- 档案详情编辑页：结构化字段（mbti / score / relationStatus / timeline）可编辑 + 已记忆内容专门查看页
- 同名档案重名校验
- 记忆导出 / 备份

## 4. UI 设计稿（文字版）

### 4.1 设置页「记忆」分组（对齐计划 §3.5）

```
────────── 记忆 ──────────      ← SettingsSectionHeader（复用）
  记忆档案                      ← 玻璃行（点行主体 = 切换激活 + Toast）
  ├─ ✓ 小A（caption: 使用中 / 已记住 N 字）
  │    [编辑图标] [删除图标]
  ├─ ○ 小B（caption: 未使用）
  │    [编辑图标] [删除图标]
  ➕ 添加记忆                     ← Add 图标 + accent（参照「提供商 +」行样式）
  自动记忆  [开关]               ← Switch 行（memory_auto_enabled，默认开）
  记忆说明 caption               ← "选择本次咨询对象的记忆，不同对象互不干扰"
────────── 外观 ──────────      ← ThickDivider 分隔
```

- **档案行**：GlassSurface 形态；左侧激活标识 = accent 实心对勾（参照 ModelSheet 选中态），未激活 = 空心圆；右侧 20dp 图标按钮 `Icons.Outlined.Edit`（编辑）/ `Icons.Outlined.Delete`（删除）。
- **空状态**：无档案时显示 muted 提示「还没有记忆档案，添加一个开始使用」。

### 4.2 三个弹窗（新文件 `ui/settings/MemoryDialogs.kt`，风格对齐 PrivacyDialogs.kt）

| 弹窗 | 触发 | 内容 |
|------|------|------|
| MemoryNameDialog | 「添加记忆」行 | AlertDialog + GtjShape.lg，TextField 输入名称，确认按钮 accent，空白不允许；创建并激活（列表为空时） |
| MemoryEditDialog | 档案行编辑图标 | 名称 TextField + 记忆正文多行 TextField（2-4 行），保存按钮 → `updateTarget(id, name, note)` |
| MemoryDeleteDialog | 档案行删除图标 | 标题「删除记忆」、正文「删除后「X」的记忆将无法恢复，确定删除？」、确认按钮 danger 色（复用 WipeDialog 模板） |

### 4.3 抽屉会话档案 Tag（对齐计划 §3.8）

- `SessionDrawer.SessionItemContent` 标题行右侧加档案 `Tag`（复用现有 Tag 组件，TagKind.NEUTRAL 或 accent 系，按视觉一致性定）。
- 显示文字规则：`name.trim().take(4)`（≤4 字全显，>4 字截前 4 字，不做省略号，Tag 自适应宽度；空名称不显示）。
- `targetName=null`（老会话）不显示 Tag，正常布局。

## 5. 数据与隐私说明

- **存储位置**：记忆正文（note）与档案列表仅存本机 Room（target 表），激活档案 id 与自动记忆开关存本机 DataStore；无自有后端，不云同步（延续产品"数据不出手机"一级卖点）。
- **出网范围**：记忆提炼复用主模型，出网仅发生在用户主动分析/回复时，请求发往用户自行配置的第三方 LLM 服务；提炼失败静默，不影响主流程。
- **清除联动**：设置页「清除全部档案」将一并删除记忆正文、档案列表、激活 id、自动记忆开关及全部会话——随隐私清除，无残留。
- **长度与质量**：单档案记忆正文上限 2000 字（mergeNote 截断 + 弹窗所见即所得）；提炼质量依赖模型，防御性解析 + 去重兜底，最坏情况是不追加，不会写坏现有记忆。

## 6. 待确认问题

无（迭代计划 v2 已确认全部决策点：自动提炼并入、分组位置、Toast 文案、会话档案归属）。
