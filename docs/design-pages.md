# 狗头军师 · 页面设计提示词（Phase 3 前端使用）

> 来源：SPEC v1.0 第 7/8/9 节 + uiux.md 设计基线 + SKILL.md 产品语气
> 唯一 Token 源：docs/design-tokens.json（色值/字号/间距/圆角/图标尺寸全部引用 Token 名）
> 图标：Material Symbols（Outlined），仅用下列锁定图标名；全项目禁 emoji 字符
> 交互基线：按压反馈 ripple/scale 0.98、转场 200ms、打字机光标 900ms、流式打字点 150ms 错峰
> 无障碍基线：正文对比度 >=4.5:1（muted 浅色 4.8:1 / 深色 6.1:1 均达标）；可点区域 >=48dp；图标按钮必有 contentDescription；focus 可见
> 对比度例外说明：accent 底 + accentOn 白字 = 4.33:1（符合大字号 AA），仅用于主按钮/用户气泡（文字一律 16sp Semibold，subtitle+600）；小于 14sp 的 accent 底文字改用 accentPressed(#2E47E0) 保证 >=4.5:1。warm 标签文字用 warmOn（浅色 #B45309 / 深色 #E8873E），勿用 warm 直接当文字色（浅底仅 2.64:1）

---

## 通用约定

- 布局栅格：内容区左右留白 `space-lg`(16)，卡片间 `space-md`(12)，分组间 `space-xxxl`(32)
- 文字层级：标题 `headline/title`，正文 `body/bodySm`，标签 `label`，元信息 `caption`
- 卡片统一 `card` token：`surfaceElevated` 底 + `border` 边 + `radius-md`，无默认阴影（深色用亮度递进代替阴影）
- 按钮：主操作 `primaryButton`（accent 底 + 白字 + 48dp 高）；次操作 `secondaryButton`（描边）；弱操作 `ghostButton`（muted 字）
- 状态全覆盖：每个可加载组件必须有 空/加载/错误/正常 四态；异步操作中按钮内联 spinner，禁止整屏阻塞

---

## 页面 1：对话首页（/chat）

### 布局结构
```
Scaffold(topBar / content / bottomBar)
├── TopBar（高 56dp，bg=bg）
│   ├── 左：产品名 "狗头军师"（title 600）
│   ├── 右：模型切换器（当前模型名 + expand_more 图标，点击弹模型选择弹层）
│   └── 右2：设置入口（settings 图标 24dp，icon-lg）
├── MessageList（LazyColumn，contentPadding 底部 96dp）
│   ├── 用户气泡：右对齐，accent 底 / accentOn 字，radius-lg，右下角小圆角，maxWidth 82%
│   ├── AI 气泡：左对齐，surface 底 / fg 字，radius-lg，左下角小圆角，maxWidth 92%，可含分析卡片/转述卡/错误卡
│   └── 时间戳 caption 居中（仅新分组显示）
├── 流式状态条（AI 回复中）：三点思考指示（typingDot 150ms 错峰）+ 光标
├── InputBar（底部，bg=surface，radius-pill，高 48dp，左右 padding-lg）
│   ├── 左：attach_file 图标（md 20dp）→ 弹出"粘贴文本 / 选择截图"入口
│   ├── 中：TextField（无边框，placeholder "说点什么，或粘贴聊天记录…"）
│   └── 右：发送按钮（圆形 44dp accent 底 + send 图标 accentOn，仅文本非空可用）
└── 流式输出时输入栏右侧替换为 stop 图标（可中途停止）
```

### 组件清单与 Token
- 气泡：`bubbleUser` / `bubbleAi`；间距：气泡间 `space-sm`(8)，同一人多条连续消息合并去间距
- 输入栏：`inputBar`；发送按钮：`primaryButton` 圆形容器
- 空状态（首次进入无会话）：
  - 顶部问候：headline "先接住情绪，再分清事实，最后给能执行的选择"
  - 说明：bodySm muted "把聊天记录粘进来，或直接说你的处境。数据只发往你配置的模型服务。"
  - 示例问题 chips（`chip` token，横向滚动）：
    - "他三天没回消息，怎么开口"
    - "这句话怎么回比较好"
    - "我们是不是该结束了"
    - "第一次约会聊什么不冷场"
  - 两个入口卡：`content_paste` 粘贴聊天记录 / `image` 选择截图分析（card token，可点区域 >=48dp）

### 状态
- 空：如上引导（绝不能是空洞欢迎语）
- 加载（流式）：三点思考 + 光标；停止后立即消失
- 错误：气泡内嵌错误卡（见页面 7），保留用户原文，重试不丢内容
- 正常：消息流 + 输入栏

### 微交互
- 发送：用户气泡 150ms 淡入 + 列表滚动到底（fast 150ms）
- 流式打字机：SSE 增量渲染，光标 900ms 闪烁；用户点击 stop 后保留已输出部分
- 按压反馈：所有可点项 scale 0.98 + ripple
- 空状态 chips：点击即把示例问题填入输入栏并聚焦（不直接发送，让用户可编辑）

### 无障碍
- 图标按钮（attach_file/settings/expand_more）必须有 contentDescription
- 气泡文本 `body`(16sp)，对比度达标；时间戳 `caption` muted
- 发送按钮 disabled 态 opacity 0.4，仍需可聚焦说明原因

---

## 页面 2：首启问卷（/onboarding，4 屏向导）

### 布局结构
```
Column
├── 顶部行（56dp）
│   ├── 左：back 图标（屏2-4 显示，返回上一屏）
│   └── 右：ghost 按钮 "跳过，直接开聊"
├── 进度条（progressTrack：4dp 高，borderSoft 底 + accent 指示，宽度 = 当前屏/4，200ms 动画）
├── 内容区（居中，maxWidth 480dp）
│   ├── 题面：title 600
│   ├── 说明：bodySm muted（标注"选填"的字段不阻塞）
│   └── 组件区（见下）
└── 底部固定：primaryButton "下一步"（全宽，高 48dp）
```

### 四屏内容（题序锁定：本人 → 对象 → 经过 → 目标+情绪）
- **屏1 本人**："先让我认识你"
  - MBTI：4 组二选一分段（E/I、S/N、T/F、J/P），组间 `space-lg`，选项用 `chip` 选中态（accentSoft 底 + accent 边 + accent 字）
  - 主观综合评分：slider 0-100（step 5，轨道 borderSoft，滑块 accent，label 实时显示分值）
  - 主要优势 / 主要短板：2 个 `textField`（选填，多行）
- **屏2 对象**："TA 呢？给 TA 起个代号"
  - 代号：textField，placeholder "如：阿岚"
  - MBTI：同屏1 四组二选一（可"不知道"跳过）
  - 主观综合评分：slider 0-100
  - 当前关系：chips 单选（暧昧 / 追求中 / 热恋 / 冷淡 / 冲突 / 已分手 / 其他）
- **屏3 经过**："你们是怎么走到现在的"
  - 认识方式：chips（朋友介绍 / 社交软件 / 同学同事 / 偶遇 / 其他）
  - 发展多久：chips（刚认识 / 1-3 个月 / 3-6 个月 / 半年以上）
  - 最近三件关键事件：多行 textField（选填）
  - 联系与投入：chips（我主动多 / 对方主动多 / 差不多）
- **屏4 目标+情绪**："你想要什么，现在最难的是什么"
  - 目标：chips 单选（推进 / 确认 / 修复 / 比较选择 / 退出）
  - 最难受的点：多行 textField
  - 情绪强度：slider 0-10（>7 提示：会先安抚再给完整分析）
  - "眼下有没有必须马上回的话"：switch + 可选 textField

### 状态
- 跳过（屏1 也显示）：点击 → `dialog` 二次确认
  - 标题："跳过问卷也能开聊"
  - 正文："建议先花两分钟建档，分析会更准。档案稍后可在任何时候补录。"
  - 按钮：主 "继续填写" / 次 "跳过，直接开聊"（确认后进对话，DataStore.onboardingCompleted=true）
- 加载：提交中按钮内联 spinner；正常：每屏"下一步"始终可点（空值允许，符合 SKILL"不知道可留空"）

### 微交互
- 进度条 200ms 平滑推进；切换屏 200ms 横向滑动
- chips 选中态 150ms；slider 拖动实时反馈

### 无障碍
- slider 支持键盘/无障碍增减；每字段有 label；可点区域 >=48dp
- 四组二选一用 segmented 语义，勿用纯色块表达选中（需图形/文字双通道）

---

## 页面 3：设置页（/settings）

### 布局结构
```
LazyColumn（分组用 dividerThick 8dp 分隔）
├── 分组1：模型服务
│   ├── Row 当前主模型：label "主模型" + 模型名 + 能力徽标（支持看图/纯文本）
│   ├── Row 视觉模型：label "视觉模型" + 模型名 + caption "用于非多模态主模型的截图分析"
│   ├── 提供商管理标题行 + add 图标按钮"添加提供商"
│   └── 提供商列表（card 内 rows）：名称 + host 域名 + 状态徽标（已配置 success / 未配置 muted / 自定义 neutral）+ chevron_right
├── 分组2：外观
│   └── 主题单选（浅色 / 深色 / 跟随系统）→ 图标 wb_sunny / dark_mode / brightness_auto + label，选中 accent
├── 分组3：隐私与安全（icon lock）
│   ├── Row 隐私声明：info 图标 + "数据将发送至你配置的第三方模型服务" → dialog 全文
│   └── Row 清除全部档案：danger 文字 + delete 图标 → 二次确认 dialog（确认后删除全部本地数据并回对话页）
└── 底部版本 caption：版本号（如 v1.0.0）
```

### 提供商编辑页（子路由 /settings/provider/:id）
- 字段：名称（textField）、Base URL/Host（textField，mono 字体）、API Key（textField mono，密文圆点显示 + visibility/visibility_off 显隐切换）
- 模型管理（card）：
  - 每行：模型名 + 多模态能力 switch（supportsVision）+ 设为默认 radio + delete 图标
  - 添加模型行：textField + add 图标
- "测试连接"按钮（secondaryButton）：结果三态：
  - 成功：success 色 check 图标 + "连接正常，模型可用"
  - 401：danger 色 error_outline + "API Key 无效，请检查"（SPEC 5.2）
  - 429：warn 色 warning + "请求过于频繁或额度已用尽，稍后重试"
  - 5xx：warn 色 warning + "模型服务异常，请稍后重试"
- 底部：danger 描边按钮"删除此提供商"（二次确认）+ primaryButton"保存"

### 状态
- 空（无提供商）：引导卡 "还没有模型服务，添加一个开始使用" + primaryButton"添加提供商"
- 加载（测试连接）：按钮内联 spinner，禁用重复点击
- 错误：见结果三态；正常：列表 + 徽标

### 微交互
- API Key 显隐切换 150ms；switch 150ms；delete 需二次确认 dialog（防误删）
- 主题切换即时生效（深色/浅色全局）

### 无障碍
- 密文 Key 的显隐按钮 contentDescription="显示/隐藏 API Key"
- 颜色徽标需文字双通道（"已配置/未配置/自定义"文字+色）

---

## 页面 4：模型选择底部弹层（组件）

### 布局结构
```
BottomSheet（sheet token：surfaceElevated 底 + 顶部圆角 xl + dragHandle 拖拽条）
├── 标题行：title "选择模型" + 左 close 图标（icon-lg）
├── 当前模型提示行：caption muted "当前：deepseek-chat"
├── LazyColumn（按提供商分组）
│   ├── 分组头：label muted "DeepSeek"
│   └── 模型行：模型名（body）+ 能力徽标（支持看图：image 图标 + "看图"标签 / 默认：neutral tag"默认"）
│       ├── 选中态：accentSoft 底 + accent 字 + check 图标（icon-md）
│       └── 未选中：surface 底 + fg 字
└── 底部：ghostButton "管理模型服务"（去设置页）
```

### 状态
- 空（无可用模型）：居中引导 "没有可用模型" + primaryButton"去设置添加"
- 加载：整层 skeleton（3 行占位，shimmer 150ms）
- 正常：分组列表

### 微交互
- 弹层展开 300ms 上滑 + scrim 淡入；行点击 150ms 选中态切换，切换后立即生效并收起（不中断会话，AC-10）
- 关闭：点 scrim / 下滑 / close 图标均可

### 无障碍
- 弹层焦点陷阱；行高 >=48dp；选中态双通道（accentSoft 底 + check 图标 + accent 字）

---

## 页面 5：截图转述确认卡（通道 B，对话内）

### 布局结构
```
AI 气泡内卡片（card token）
├── 头部行：image_search 图标（md）+ subtitle "AI 从截图中读出了这些内容" + 右 edit 图标（进入编辑态）
├── 说明行：caption muted "当前模型不支持看图，已用视觉模型提取文字。可修正后再分析。"
├── 转述内容：quoteBlock（surface 底 + borderSoft 边 + radius-sm + padding-lg，bodySm 文本）
│   └── 编辑态：多行 textField（body），行高 20
└── 底部按钮行（右对齐）
    ├── ghostButton "重新选图"
    └── primaryButton "确认分析"（send 图标）
```

### 状态
- 加载（转述中）：头部 spinner + caption "正在提取截图文字…"
- 正常（可编辑）：如上；编辑态显示 保存/取消（edit → check / close）
- 错误（转述失败）：dangerSoft 底 + "截图文字提取失败" + 重试按钮（保留原图）

### 微交互
- 进入编辑态 150ms 边框 accent；确认后卡片折叠为普通 AI 消息
- 图片小缩略图（64dp 圆角）附在卡片头部，点击可放大

### 无障碍
- 编辑 textField 有 label；确认按钮文字 "确认分析"（勿用纯图标）

---

## 页面 6：五步法分析结果卡片

### 布局结构
```
AI 气泡内（analysisCard）
├── 结论置顶：headline 结论句（如"先接住失落，再决定要不要主动问清"）
├── 引用行：caption muted + menu_book 图标 + "参考：在线约会与数字关系 · 冲突修复"（AC-06 知识透明）
├── 五段折叠区（sectionGap xxl）
│   ├── 01 情绪落地（mood 图标）→ 2-4 句感受/触发点/冲突
│   ├── 02 事实拆分（fact_check 图标）→ 已知事实 / 合理推测 / 关键未知（quoteBlock 分块）
│   ├── 03 利益判断（balance 图标）→ 互惠/可靠/吸引/可行性/可逆性/安全/机会成本
│   ├── 04 明确建议（lightbulb 图标）→ 首选 + 2-4 理由 + 至多 3 个版本（稳健/会撩/强势）
│   │   └── 建议卡：accentSoft 底 + accent 边 + content_copy 复制按钮（"已复制"toast）
│   └── 05 行动收束（flag 图标）→ warm 标签 + 一个小动作 + 观察窗口/停止条件 + 1-3 个追问
│       └── 标签：tag warm（warmSoft 底 + warmOn 字 + radius-pill），全屏 <=1 处
└── 折叠：默认 01-03 展开，04/05 折叠；头部 chevron expand_less/expand_more
```

### "这句怎么回"模式（AC-05）
- 第一屏：可复制成品卡（primaryButton 底 + accentOn 字 + content_copy 按钮）
- 第二屏：发送时机 / 主要代价 / 积极、含糊、不回应的后续（collapsible 三段）

### 状态
- 加载：骨架屏（5 个 section 占位，shimmer 150ms）
- 流式：分段增量渲染，先结论后细节
- 错误：见页面 7；正常：如上

### 微交互
- 折叠 200ms 高度过渡；复制按钮 150ms 反馈 + toast（fast）
- 行动收束标签 300ms 淡入（warm 全屏唯一，克制）

### 无障碍
- 五段标题可用 keyboard 聚焦展开；复制按钮 contentDescription="复制建议话术"
- 引用文档名与结论均需 text 通道（勿仅图标）

---

## 页面 7：错误态（401 / 429 / 5xx / 超时）

### 布局结构（AI 气泡内错误卡）
```
card（dangerSoft 仅作细节点缀，主体保持 surfaceElevated + border）
├── 头部行：error_outline 图标（danger/muted 按级别）+ subtitle 错误标题
├── 正文：bodySm 具体文案（SPEC 5.2 映射，全项目唯一文案源）
├── 重试按钮行（右对齐）
│   ├── ghostButton "取消"
│   └── primaryButton "重试"（加载中内联 spinner）
└── 401 场景附加：secondaryButton "去设置检查 API Key"
```

### 文案映射（与 SPEC 5.2 完全一致）
| 码 | 标题 | 正文 | 按钮 |
|----|------|------|------|
| 401 | API Key 无效 | 请到设置检查你的 API Key | 去设置 / 取消 |
| 403 | 服务拒绝访问 | 请检查账户状态 | 重试 |
| 404 | 模型不存在 | 请检查模型名（可能已退役） | 去设置换模型 |
| 429 | 请求过于频繁或额度已用尽 | 稍后重试 | 重试 / 取消 |
| 5xx | 模型服务异常 | 请稍后重试 | 重试 / 取消 |
| 超时/断流 | 连接中断 | 可重试或停止 | 重试 / 停止 |

### 状态
- 加载：重试按钮 spinner；错误：错误卡内联显示，原用户消息保留（不丢内容）
- 正常：错误卡消失，继续流式

### 无障碍
- 错误文案为 text 通道（勿仅图标）；重试/取消按钮 >=48dp；error_outline 用 danger 但面积小、非闪烁

---

## 页面 8：危机转介卡（安全边界触发态）

> 触发：输入或分析命中危机关键词（家暴/跟踪/胁迫/财务控制/自伤等），SPEC AC-13
> 设计原则：**克制、冷静、明确**。禁止红闪/警示色大面积、禁止"恋爱话术"、禁止建议继续推进关系。

### 布局结构
```
AI 气泡内专用卡（bg=surfaceElevated，border，radius-md，无 danger 大面积着色）
├── 头部行：shield 图标（muted/meta，24dp）+ subtitle "先处理安全，再处理关系"
├── 正文（body，行高 24）：
│   "你不需要独自面对，也不需要马上做任何决定。先把当下安全放第一。"
├── 三步清单（quoteBlock 分块，label 标题 + bodySm）：
│   1. 确保当下安全：离开可能升级的现场，去人多或熟悉的地方
│   2. 联系可信的人：家人、朋友，或当地妇女维权/援助热线
│   3. 必要时联系当地紧急服务（110 / 120），保存证据（截图、录音、就医记录）
├── 冷静提示行：caption muted "以上为一般安全指引，不替代专业帮助。"
└── 底部：ghostButton "我知道了"（收起卡片，进入对话继续非危机话题）
```

### 状态
- 触发即整卡替换分析流程（不输出五步法、不输出话术，AC-13）
- 正常：无此卡；加载：无（直接渲染）

### 微交互
- 卡片 300ms 淡入（slow），**无脉冲、无闪烁、无抖动**
- "我知道了"收起 200ms；不再主动推送

### 无障碍
- 全卡对比度达标；三步清单用有序语义列表；shield 图标 + 文字双通道
- 无紧急红色全屏遮罩（避免惊吓 + 避免色盲依赖）

---

## 附：锁定的 Material Symbols 图标清单（全项目仅此清单）

`send` `attach_file` `image` `content_paste` `settings` `expand_more` `expand_less` `chevron_right`
`close` `back`(arrow_back) `stop` `check` `edit` `delete` `add` `visibility` `visibility_off`
`content_copy` `refresh` `error_outline` `warning` `info` `menu_book` `mood` `fact_check`
`balance` `lightbulb` `flag` `image_search` `shield` `wb_sunny` `dark_mode` `brightness_auto` `lock`

> 禁 emoji 字符（P0）；禁多图标库混用；尺寸体系 icon-sm 16 / icon-md 20 / icon-lg 24
