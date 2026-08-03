# 狗头军师 · 聊天主界面 UI 设计（以最新产品代码为准 · v2）

## 背景
用户改完一轮产品代码后要求重新设计 UI：去掉档案页、安全与边界页，只保留单聊天框，功能以代码为准。上一版四屏原型（暖橙配色）已作废。

## 代码调研结论（源码：app/app/src/main/java/com/goutoujunshi/app/）
纯 Jetpack Compose 单聊天 App，路由仅 Chat / Onboarding / Settings / ProviderEdit 四个。聊天链路核心：
- `ChatViewModel.sendText` 启发式路由：短句 → REPLY 模式（共情+一句成品话术）；多行/含引号 → FIVE_STEP 五步法
- `ChatScreen.kt`：TopBar（菜单/产品名/模型切换 pill/设置）+ 消息流 + ChatInputBar
- `ChatInputBar.kt`：回形针菜单（粘贴文本/选择截图），流式时发送键变停止键
- 截图双通道：主模型多模态直读；否则视觉模型转述 → `TranscriptionCard` 编辑确认
- 流式：SSE 增量 + `ThinkingPanel`（reasoning_content 折叠，DeepSeek 风格）+ `StreamingPreview` 只提取 reply 字段
- `AnalysisCard`：结论 headline + 引用行（知识透明）+ 五段折叠（01-03 默认展开，04/05 折叠）+ token 估算
- `CrisisCard`（危机关键词本地预检，克制设计）、`ErrorCard`（401/429/5xx 文案映射）
- 长按消息复制/删除（二次确认）、DeepSeek 风格会话抽屉、模型选择底部弹层
- 设计令牌：`docs/design-tokens.json`（accent #4D6BFE 蓝、warm #E8873E、16sp 正文、48dp 触控）

## 交付物
`outputs/goutoujunshi-chat-prototype.html` — 三屏高保真可交互原型：
1. **屏 1 空状态**：引导语 + 示例 chips（点击填入输入框）+ 粘贴/截图入口卡；可体验会话抽屉（新建/切换/长按删除确认）与模型选择弹层
2. **屏 2 对话中**：用户气泡 + ThinkingPanel 思考折叠 + 完整五步法分析卡（结论/引用/建议卡复制/01-05 可折叠/warm 标签/token 估算）
3. **屏 3 异常与通道 B**：截图气泡 + TranscriptionCard 转述确认 + 流式 reply 预览（打字机光标）+ 401 错误卡 + 危机转介卡；输入栏演示停止键

## 与代码的对应关系
所有色值、字号、圆角、组件结构严格引用 design-tokens.json；交互（长按菜单、二次确认、抽屉、弹层、复制 toast）与 ChatScreen.kt 状态流一致。图标风格对齐 Material Symbols Outlined，全页无 emoji。

## 后续
- 深色主题稿（tokens 已含 dark 色板，可直接套用）
- 设置页 / Onboarding 问卷 / ProviderEdit 页面如需原型可再补
