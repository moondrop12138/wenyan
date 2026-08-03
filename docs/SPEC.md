# Spec - 狗头军师（恋爱决策支持安卓 App）v1.0

> 生成日期：2026-08-02
> 基于：PRD v1.0（用户已确认）+ 架构文档 v1.0（用户已确认）+ UIUX 文档 v1.0（用户已确认）
> 状态：已确认
> 锁定人：大湾区靓仔（项目总监）

---

## 1. 产品定义

- **一句话描述**：以 goutoujunshi（狗头军师）开源项目为知识内核、用户自带 API Key 直连 LLM 的恋爱决策支持工具——先接住情绪，再分析关系，给出可执行的下一步。
- **目标用户**：20-35 岁处于暧昧/追求/热恋/关系焦虑/冲突/分手期的年轻人；重视隐私、不愿为虚拟陪伴氪金、深夜情绪峰值需要即时决策支持。
- **核心问题**：现有产品要么是"虚拟恋人陪伴"（模板化/付费墙/隐私上云），要么是通用 AI 套 prompt（流程不固定/无知识库/无截图分析）。市场缺少专业、克制、流程严谨、数据不出手机的恋爱决策工具。

## 2. MVP 范围（锁定——不在此列表的功能一律不做）

| 优先级 | 功能 | 验收标准摘要 | RICE |
|--------|------|-------------|------|
| P0 | F1 紧凑问卷建档（本人+对象 MBTI/评分/关系背景） | 首启 4 屏可完成/可跳过（二次确认），档案持久化 | 13.5 |
| P0 | F6 自带 Key 设置 + 纯本地存储 | Keystore AES-GCM 加密，一键清除全部档案 | 15.0 |
| P0 | F7 安全边界/危机转介 | 危机关键词触发转介而非恋爱话术 | 15.0 |
| P0 | F4 可发送话术成品 + 发送时机/后续分支 | 五步法输出含可复制话术 | 13.5 |
| P0 | F2 文本粘贴聊天记录分析（五步法） | 粘贴文本 → 结构化五步分析 | 9.0 |
| P0 | F5 按需加载知识库路由 | 场景 → 1-3 份文档注入，结果回显引用 | 8.0 |
| P0 | F3 聊天截图分析（双通道） | 多模态直读 / 非多模态视觉转述 | 4.8 |
| P0 | F8 多模型管理（Chatbox 式提供商体系） | 预设+自定义提供商、模型增删改选、视觉模型自选 | 10.0 |

## 3. 明确不做（Out-of-Scope — 锁定）

| 不做的功能 | 原因 | 何时考虑 |
|------------|------|----------|
| 虚拟恋人陪伴/角色扮演 | 与产品定位冲突（工具非娱乐） | 永不 |
| 语音视频通话 | 依赖重型服务，MVP ROI 不足 | v2+ |
| 社区广场/朋友圈互动 | 运营成本高、内容风险 | 永不 |
| 抽卡/礼物/道具变现 | 违背"克制工具"定位 | 永不 |
| 云同步账号体系 | 数据纯本地是核心卖点 | 永不 |
| 付费墙/订阅 | 用户自带 Key 零成本 | 永不 |
| 心理治疗/医疗诊断 | 超出 AI 能力边界，SKILL.md 安全边界禁止 | 永不 |
| 律师/警方功能 | 同上，仅做危机转介提示 | 永不 |
| 多人对象独立档案 | 单对象 MVP 闭环优先 | v1.1 |
| 对话演练/历史档案编辑 | 后续迭代 | v1.1 |
| 紧急情绪快速入口 | 后续迭代（问卷可跳过已覆盖部分） | v1.1 |

## 4. 技术架构（锁定 — 版本锚定）

> 版本锚定：开发前由架构师核实已安装/可获取版本，防幻觉 API。

| 层 | 技术 | 版本 | 锁定原因 |
|----|------|------|----------|
| 前端 UI | Jetpack Compose + Material 3 | BOM 2025.xx | 用户锁定 Kotlin 原生，DeepSeek 级体验 |
| LLM Client | OkHttp + okhttp-sse 自建 | okhttp 4.12.0 / okhttp-sse 4.12.0 | BYOK 多供应商兼容、SSE 流式、超时可配 |
| 本地存储 | Room + DataStore | room 2.6+ / datastore 1.1 | 多表关联 + 设置项 |
| 图片选择 | Photo Picker | activity-compose 1.9 | PickVisualMedia 免权限 |
| 图标 | Material Symbols（material-icons-extended） | 随 BOM | 锁定一套，R8 裁剪 |
| 加密 | Android Keystore + AES-GCM | 平台 API | API Key 本地安全 |
| 最低版本 | minSdk 26（Android 8.0+） | - | 覆盖主流设备 |
| 知识库 | assets 打包 40 份 md + 编译期路由表 JSON | - | 离线可用 |

## 5. 外部 API 契约（LLM 供应商 — OpenAI 兼容）

> App 无自有后端。唯一出网 = 用户主动分析时请求用户配置的第三方 LLM 服务。

### 5.1 Chat Completions（流式）

- 端点：`POST {provider.baseUrl}/chat/completions`（如 DeepSeek: https://api.deepseek.com/chat/completions）
- 认证：`Authorization: Bearer {provider.apiKey}`
- 请求体（OpenAI 兼容）：
```json
{
  "model": "{model.name}",
  "messages": [
    {"role": "system", "content": "{PromptBuilder 拼装的 system 消息}"},
    {"role": "user", "content": "{用户输入}"}
  ],
  "stream": true,
  "temperature": 0.7
}
```
- 多模态请求（通道 A）：user 消息 content 为数组 `[{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}]`
- 响应：SSE `data: {...chunk}` 流式增量，`[DONE]` 结束

### 5.2 错误码映射（用户可见文案）

| 状态码 | 含义 | App 文案 |
|--------|------|----------|
| 401 | Key 无效 | "API Key 无效，请到设置检查" |
| 403 | 无权限/欠费 | "服务拒绝访问，请检查账户状态" |
| 404 | 模型名不存在 | "模型不存在，请检查模型名（可能已退役）" |
| 429 | 限流/额度用尽 | "请求过于频繁或额度已用尽，稍后重试" |
| 5xx | 服务异常 | "模型服务异常，请稍后重试" |
| 超时/断流 | 网络问题 | "连接中断，可重试或停止" |

### 5.3 图片规格（锁定）

- 选图后统一缩放：最长边 ≤1568px、JPEG 质量 85%
- 超过 20MB 原图先拒绝并提示

## 6. 数据库表清单（锁定）

| 表名 | 核心字段 | 索引 | 关联 |
|------|----------|------|------|
| profile | id / mbti / score / strengths / weaknesses / createdAt | PK | - |
| target | id / codeName / mbti / score / relationStatus / timeline(JSON) / createdAt | PK | - |
| session | id / createdAt / scenarioTag / refDocs(JSON) | PK | - |
| message | id / sessionId / role / content / type[text,image,analysis] / createdAt | sessionId | FK→session |
| provider | id / name / baseUrl / apiKeyEncrypted / isPreset / sortOrder | PK | - |
| model | id / providerId / name / supportsVision / isDefault / sortOrder | providerId | FK→provider |
| DataStore | currentModelId / visionModelId / theme / onboardingCompleted / privacyAck | - | - |

## 7. 页面清单（锁定）

| 页面 | 路由 | 核心组件 | 对应数据/API | Token 主题 |
|------|------|----------|--------------|------------|
| 对话首页 | /chat | 消息流（气泡）、底部输入栏（回形针+发送）、顶部标题+模型切换器、五步法结果卡片 | LLM 流式 + session/message | 浅色/深色 |
| 首启问卷 | /onboarding（4 屏向导） | 进度条、选项卡片、"跳过直接开聊"（二次确认） | profile/target | 浅色 |
| 设置页 | /settings | 提供商列表、提供商编辑（Host/Key/模型管理/测试连接）、主模型/视觉模型选择、主题、隐私清除 | provider/model/DataStore | 浅色/深色 |
| 模型选择弹层 | 组件 | 提供商分组列表、能力徽标 | model | 浅色/深色 |
| 截图转述确认 | 对话内 | "AI 从截图中读出了这些内容"卡片 + 编辑 + 确认分析 | LLM 通道 B | 浅色/深色 |
| 空状态/错误态 | 组件 | 中心图标 + 引导文案 + 主操作 | - | 浅色/深色 |

## 8. 设计 Token（锁定）

> 设计师 Phase 2 输出正式 design-tokens.json + design-tokens.css，前端 import 引用。

- 主色：`#4D6BFE`（DeepSeek 深蓝，纯色非渐变）
- 背景：浅 `#FFFFFF` / 深 `#0F1117`；surface 浅 `#F7F8FA` / 深 `#161B22`
- 文字：浅 `#111827` / 深 `#F2F4F8`；muted 浅 `#6B7280` / 深 `#8B949E`
- 边框：浅 `#E5E7EB` / 深 `#262B36`
- 语义：success `#16A34A` / warn `#D97706` / danger `#DC2626`；warm `#E8873E`（仅"行动收束"标签，每屏 ≤1 处）
- 字体：Roboto / Noto Sans SC 系统栈；标题 Medium/Semibold，正文 400；API Key 等宽
- 图标：Material Symbols（Outlined），16/20/24px 体系，禁 emoji
- 主题：浅色/深色/跟随系统，深色首发同等体验

## 9. 验收标准（锁定 — EARS 格式，QA 唯一依据）

| 编号 | 功能 | 验收标准 | 优先级 |
|------|------|----------|--------|
| AC-01 | F1 首启问卷 | While 用户首次启动且未完成问卷，系统**必须**展示 4 屏问卷向导（本人→对象→经过→目标+情绪） | P0 |
| AC-02 | F1 问卷跳过 | While 用户点击"跳过直接开聊"，系统**必须**弹出二次确认，确认后**必须**允许直接进入对话且档案可稍后补录 | P0 |
| AC-03 | F1 档案持久化 | When 用户完成问卷提交，系统**必须**将档案写入本地库，重启后**必须**仍可读取 | P0 |
| AC-04 | F2 文本分析 | While 用户粘贴文本并发起分析，系统**必须**调用 LLM 并返回五步法结构化结果（情绪落地/事实拆分/利益判断/明确建议/行动收束） | P0 |
| AC-05 | F4 话术成品 | When 分析结果包含建议话术，系统**必须**提供一键复制；If 为"这句怎么回"场景，系统**必须**先给可复制成品再给时机/代价/后续分支 | P0 |
| AC-06 | F5 知识路由 | While 用户发起分析，系统**必须**按场景路由表命中 1-3 份知识文档注入 prompt，并在结果中回显引用文档名 | P0 |
| AC-07 | F3 通道 A | While 主模型 supportsVision=true 且用户选择截图，系统**必须**将压缩后图片（≤1568px/85%）直接随消息发送 | P0 |
| AC-08 | F3 通道 B | While 主模型 supportsVision=false 且用户选择截图，系统**必须**先调用视觉模型（visionModelId）转述文字，展示可编辑转述卡，用户确认后再交主模型 | P0 |
| AC-09 | F8 提供商管理 | While 用户在设置页操作，系统**必须**支持：新增/编辑/删除提供商（名称/Host/Key）、增删模型、标注多模态能力、测试连接 | P0 |
| AC-10 | F8 模型切换 | While 用户在对话页切换主模型，系统**必须**立即生效且不中断当前对话历史 | P0 |
| AC-11 | F6 Key 安全 | While 提供商保存 API Key，系统**必须**以 Keystore AES-GCM 加密存储，UI 仅密文显示（可显隐） | P0 |
| AC-12 | F6 隐私清除 | While 用户执行"清除全部档案"，系统**必须**删除全部本地数据（含 Key、档案、会话）并二次确认 | P0 |
| AC-13 | F7 危机转介 | If 用户输入或分析结果命中危机关键词（家暴/跟踪/胁迫/自伤等），系统**必须**触发安全转介提示（安全计划+当地紧急服务），**必须不**输出恋爱话术 | P0 |
| AC-14 | 流式输出 | While LLM 返回 SSE 流，系统**必须**实时增量渲染，支持中途停止 | P0 |
| AC-15 | 错误处理 | If LLM 返回 401/429/5xx，系统**必须**按 5.2 映射表展示明确文案并支持重试 | P0 |
| AC-16 | 深色模式 | While 主题=深色或跟随系统为深色，系统**必须**按深色 Token 渲染全部页面 | P0 |
| AC-17 | 知识库打包 | When 构建 APK，系统**必须**包含 40 份知识文档与编译期路由表（构建脚本校验完整性，缺失即失败） | P0 |
| AC-18 | 隐私声明 | While 用户首次配置 API Key 前，系统**必须**展示隐私声明（"数据将发送至用户配置的第三方模型服务"）并要求确认 | P0 |

## 10. 边界与约束

- minSdk 26（Android 8.0+），不支持更低版本
- 模型上下文需 ≥32K token（预设模型均满足）
- 知识库单文档注入预算 ≤4K token，每次 ≤3 份
- 网络：连接超时 15s / 读超时 60s，指数退避重试 3 次
- 图片：最长边 ≤1568px、质量 85%、单图 ≤20MB
- 单文件 ≤300 行；入口文件只装配零业务
- 仅 INTERNET 权限；不申请存储权限（Photo Picker 免权限）

## 11. 内嵌已知坑（新项目预置风险，开发时规避）

| 坑 | 指纹 | 根因 | 修法 |
|----|------|------|------|
| DeepSeek 无视觉 | deepseek-v4-* | 官方 API 纯文本 | 通道 B 视觉转述；设置页能力标注分流 |
| 模型名已退役 404 | 任意模型 | 厂商下架（如 deepseek-chat 已退役） | 预设表已核实；404 时提示检查模型名 |
| SSE 回调线程 | okhttp-sse | 回调在后台线程 | callbackFlow + Dispatchers.Main 更新 UI |
| Key 明文存储 | datastore | 易被提取 | Keystore AES-GCM 加密 |
| 中文长文档超 token | assets/*.md | 13KB ≈ 6-7K token | ## 分块 + 关键词命中 + 4K 预算 |
| 深色模式反色发灰 | theme | 直接 invert 色彩 | 按深色 Token 独立设计（设计师已定色值） |

## 12. 端到端验证步骤（Spec 锁定）

```bash
# 1. 构建（含知识库完整性校验 + 路由表生成）
./gradlew assembleDebug   # 断言: BUILD SUCCESSFUL，40 份文档全部打包

# 2. 单元测试（知识库路由/加密/PromptBuilder/错误映射）
./gradlew testDebugUnitTest   # 断言: 全部通过

# 3. 冒烟测试（本地 JVM，Mock LLM 端点）
#    a. 无 Key 时发送 → 提示配置 Key（AC-18 前置）
#    b. 文本粘贴 → Mock 返回五步法 JSON → 结构化卡片渲染
#    c. supportsVision=false + 截图 → 走通道 B 转述卡
#    d. 危机关键词输入 → 触发转介（AC-13）

# 4. 模拟器手工清单（交付前 QA 执行）
#    首启问卷/跳过 → 粘贴分析 → 截图双通道 → 模型切换 → 深色模式 → 隐私清除
```

## 13. 变更记录

| 日期 | 变更内容 | 原因 | 影响范围 |
|------|----------|------|----------|
| 2026-08-02 | v1.0 初版生成 | 三文档用户确认 | 全部 |
| 2026-08-02 | OpenAI 预设更新为 GPT-5.6 系（Sol/Terra/Luna） | 用户指正最新模型 | 4.1/PRD 模型表 |
