# Changelog

「温言」版本历史。版本命名：`vX.Y.Z`（功能）与 `vX.Y.Z-N`（同版本迭代构建）。

## v1.9.4（2026-09-28，09-29 迭代）— 冷启动会话恢复 + 记忆导出/导入 + 流式状态修复 + 流光背景与外观可调 + 液态玻璃质感升级 + Mica 视觉对齐/玻璃扁平化与内凹改版/色相全局跟随/输入栏与弹层对齐 web + 触摸修复

**修复**：
- 历史会话点击失效与危机卡长按失效：玻璃按压动画旧实现用 `detectTapGestures(onPress)`，会消费 DOWN/UP——GlassSurface 无 onClick 时把按压修饰符放在内层，外层 `combinedClickable` 拿不到未消费的 DOWN，点击/长按静默失效（历史会话行、危机卡长按均中招）；改用 `awaitEachGesture` 纯手势观察器，全程不消费任何事件，与外层 clickable/combinedClickable 共存
- 会话抽屉搜索框对齐全 App 输入框规范（20dp 圆角 + 内凹底色 + 透明指示线），不再直角 + 底部横线与抽屉玻璃风格割裂
- 聊天页键盘（IME）弹出时整窗上移、顶栏被顶出屏幕：Manifest 未声明 `windowSoftInputMode`（默认 adjustUnspecified），系统对聚焦输入框走 adjustPan 把整个窗口内容上移出键盘高度（API 35 模拟器实测整窗 pan 738px），与 Compose 侧 `safeDrawing-bottom` 抬升输入栏叠加成双重位移——顶栏胶囊被 pan 出屏幕、空态标题顶到状态栏、输入栏悬空在键盘上方一大截。修复：MainActivity 显式 `adjustResize`（edge-to-edge 官方配方，窗口不动、IME 只以 insets 下发，输入栏单点消费贴合键盘上沿）；空态容器不再吃含 IME 的 Scaffold bottom padding（只避让状态栏/导航栏，高度恒定，键盘弹出/收起标题零跳变）；档案详情/提供商编辑/首启问卷三个输入页补 `imePadding()`，失去 pan 保护后聚焦框仍不被键盘盖住；会话抽屉搜索框因 safeDrawing 自带 IME 消费同步受益。scrollToItem(0)（仅会话切换触发）与"上滑看历史不拽底"保护均未触碰
- 新会话首条消息流式状态卡死（AI 回答完成后打字气泡不消失、发送键卡在「停止生成」）：流式状态中枢与任务注册表从 `RealChatRepository` 下沉到同文件内的 `internal class StreamStateHost`（未新增 main 源文件），核心是把「状态归属 sessionId」与 `streamJobs` 注册 key 一起交给每个流的句柄 `Handle` 迁移——retag 时把注册 key 从 `PENDING(-1)` 改签到真实 sid 并同步改写状态归属，事件应用改读动态 `handle.key`（不再用启动时捕获的常量 ownerKey），Done/Error/Delta/Analysis/Transcription 重新被归属校验接受；`cancel()` 按状态归属取 job（与注册 key 同源，兜底退回视图会话 key），`deleteSession` 走 `cancelFor` 同步复位该会话状态且不留僵尸，`invokeOnCompletion` 增加按归属的兜底复位（正常/取消/异常全覆盖，且只在 `state.streaming == true` 时动，绝不覆盖先到的 Error 文案）；`appScope` 的 `CoroutineExceptionHandler` 不再无条件复位全局状态（它拿不到归属 key，会误伤别的会话正在跑的流），异常收尾改由该流的结束回调按归属复位。retag 只处理 PENDING→真实 sid 这一步：已落定会话的流（含 H5 跨会话确认转述）保持原归属，旧会话迟到事件仍被拒收，M18/H5 语义不回归；签名层面仅私有实现加了 owner 形参，非 async 的同步入口（`sendText` / `analyzeImages` / `confirmTranscription`，本 App 内无调用方，已 grep 确认）传 owner=null，公共接口零变化、UI 层无需改动
- 桌面 Web 聊天页白屏（P0）：`d6352e8` 的改动删掉了 `renderChat()` 开头的 `const sid = S.sessionId;`，`'use strict'` 下消息拉取与迟到守卫引用未定义 sid（点侧栏会话 / 搜索跳转 / 删除刷新时聊天区已清空却抛 ReferenceError，历史消息白屏）；补回该行并注明用途，入口空态判断改用同一捕获值（同一处、其间无 await，语义等价），fetch 与 `if (sid !== S.sessionId) return;` 守卫恢复工作

**新增**：
- **冷启动自动恢复上次会话**：当前会话 id 持久化到 DataStore 并写穿（切换/新建/删除会话同步更新，带竞态护栏不覆盖用户较新选择）；澎湃OS 等夜间杀进程后重开自动回到上次对话，不再聊天页空态、历史抽屉无「当前会话」高亮
- **记忆导出/导入（换机迁移）**：设置页新增「导出记忆 / 导入记忆」；导出仅含用户画像 + 对象档案 + 记忆条目（不含聊天记录与 API Key），文件格式兼容桌面端导出；合并式导入——同名档案合并、重复记忆去重、事务包裹任一步失败整体回滚，绝不删除/覆盖现有数据
- **安卓流光背景**：桌面 aqua-fluid.js 流体 shader 逐行移植为 AGSL RuntimeShader（API 33+），暖色流体渐变跟随主题明暗（色值与桌面 paletteForTheme 一致）；30fps 节流只重绘不重组、缓存 Brush 每帧零对象分配，「移除动画」显示静态帧，Android 13 以下自动降级现有光斑背景；聊天/设置/问卷/提供商/记忆各页背景接入，设置页「外观」新增开关（默认开）
- **液态玻璃质感升级**：修复边缘折射 shader 因 AGSL 语法不符（vec3/vec4）从未真正渲染的问题并补 uTime 动画驱动（该 shader 连同 specular/内阴影/磨砂颗粒已在同版「玻璃扁平化」中删净，见下方视觉小节）；顶栏/输入栏接入真背景模糊（自研 backdrop blur，消息从栏底穿过时被磨砂，对齐桌面 blur(20px)+saturate(170%)，blur 值后随 Mica 对齐收窄到生效值 4px）；材质细节升级：双发丝描边，色值全部收敛进 design-tokens.json
- **卡片透光磨砂**：全 App 玻璃卡片升级为真实背景高斯模糊（延续自研 backdrop 机制，零第三方库，模糊半径对齐桌面玻璃设置「模糊度」默认 4px）——流光背景独立成层 record（修复背景画在 Scaffold 之外、卡片取样漏掉流光的缺口），经 `LocalGlassBackdrop` 分发，聊天消息气泡/危机卡/错误卡/等待气泡/转录卡/模型列表、抽屉卡片、设置页全部卡片与开关行、各二级页顶栏胶囊等约 25 个玻璃面自动垫磨砂；卡片取样纯背景层从结构上消除自反馈/自重影（内容层→卡片模糊层→背景层引用链单向无环），顶栏/输入栏既有全量磨砂（消息穿透）不回退且与卡片磨砂正确级联；API 31+ 生效，低版本与未接入页面自动回退现状半透明，不新增设置开关；模糊半径/采样余量 token 化（design-tokens.json `cardBlurRadiusDp=4`/`cardSamplePaddingDp=8`），除既有录制/重放闭包外每帧零分配，滚动不整屏失效
- **卡片通透化**：浅色 `glassFill` alpha .55→.40、`glassFillStrong` .72→.549，深色 .45→.33、.74→.549（与浅色同比例 ≈−27%），让流光真正透进卡片内部；卡片模糊半径 4dp→8dp、采样余量 8dp→16dp（维持注释里 2×blur 不变式，仍远低于悬浮栏 20dp 的性能护栏）。文字可读性用数值守而不改排版：新增 `GlassTransparencyReadabilityTest` 以**真实生产矩阵**（流光最深/最亮基色 → saturate(170%)/brightness(1.03) → 玻璃填充三层合成）复算卡片内实色并把数值以 ±0.15 钉死——浅色 fg 7.97:1 / fgSecondary 5.12:1、深色 fg 8.95:1 / fgSecondary 6.15:1（均 ≥4.5 AA）；muted 小字 3.08:1（改动前 3.61:1）属既有短板，写成记录性断言并注明正文层不用 muted
- **流光色相 / 背景亮度可调**：设置页「外观」组流光开关正下方新增「流光色相（°）」0–360 step5 与「背景亮度（%）」0–100 step1 两条滑条（`SliderField` 自带当前值显示）。色相走 **W3C hue-rotate 矩阵**（与桌面 CSS filter 同一算子，行和恒 1 保 luma；同版迭代内先按「只旋转流光/光斑基色」落地，后升级为全局跟随，见下方「流光色相全局跟随」），逐度扫描全量程卡片正文 fg ≥7.51 / fgSecondary ≥4.82（深色 fg ≥8.60 / fgSecondary ≥5.91）——**该组数字对应当时（三改）的 fill .40 配方**：同版后续 Mica 对齐把填充收到 frost .30，实测变为 Frost 组浅色 fg 6.95/6.47、fgSecondary 4.46/4.15，深色 fg 9.01/8.66、fgSecondary 6.20/5.96（浅色 fgSecondary 与 muted 低于 AA → 卡片/正文用色点一律改用 fg，见下方「测试重校准」条），以 GlassTransparencyReadabilityTest 的钉值为准；HSL 保饱和旋转版实测 190°–344° 区间 fgSecondary 跌到 3.31:1，已按评审换成矩阵并留逐度护栏；亮度与桌面 `applyGlass` 的 `--wy-brightness-white/black` 同语义（>50 叠白、<50 叠黑，浅色只叠白、深色只叠黑），默认 50 = 不叠 veil，0°/50 下与不可调版本逐位一致。链路：DataStore 新增 `fluid_hue` / `bg_brightness`（读写两侧 `coerceIn` 兜底脏数据）→ 滑条写盘 60ms 合并（拖拽不再逐像素重写 prefs，本地状态即时更新）→ `CompositionLocal`（`LocalFluidHue` / `LocalBgBrightness`）下发 → `FluidBackground` 只改每帧写入的 `uVeil` uniform（基色静态，色相由全局层承担，拖滑条不触发 AGSL 重编译也不重建 shader），降级光斑路径亮度同语义消费，reducedMotion 静态帧与动画帧共用同一段绘制代码故自动生效；纯函数内核 `ui/components/glass/FluidAppearance.kt`（`hueRotated` / `brightnessVeil` / `hueRotateColorMatrix`）零 Android 依赖，`docs/design-tokens.json` 新增 `component.fluidAppearance` 作唯一来源并同步玻璃数值（JSON 已校验）

**视觉对齐 web 增强 Mica + 玻璃扁平化 + 色相全局跟随（09-29 迭代）**（配方来源：web 增强段 styles.css:509-584、输入栏 styles.css:215-242、弹层 styles.css:317-340 与 app.js 生效默认值 frost=0.300/blur=4px，引擎一处改、全体玻璃面继承）：

- **玻璃填充对齐 web 两组分组**（cautions：两套填充色相不同，勿混用）：引擎新增 `GlassFill` 填充分组——默认 **Frost 组** = web frost 版 `--glass` 直色（亮 rgb(253,249,242,.30)/暗 rgb(46,36,28,.30)），消息气泡、模型胶囊、等待气泡、侧栏行/新建会话、设置卡等全部玻璃面自动归入，与 web 把这些元素归 `--glass` 组一致；**Card 组** = web `--wy-card-light/dark` 纵向渐变（亮 白 frost×0.50 → frost×0.35，默认 frost=0.300 即顶部 .150/底部 .105；暗 rgb(42,46,56) → rgb(22,25,34) 均 .150），仅顶栏/输入栏两个悬浮栏选用（web styles.css:534/538 同款）。`glassFill`/`glassFillStrong` 同步重定值为 frost 版 `--glass`（.30）与 `--glass-strong` 镜像（.47/.59）；两组各有消费方——**模型弹层**是 web `glass-strong` 组（`.sheet glass-strong edge refract`，index.html:114 → `--glass-strong` = frost+0.17/+0.29），故弹层用 `glassFillStrong`、抽屉容器与其余玻璃面保持 `--glass`
- **模糊对齐 Mica 生效值**：backdrop blur 悬浮栏 20dp→4dp、玻璃卡片 8dp→4dp（web 8 类玻璃元素共用生效默认 `--wy-glass-blur=4px`，app.js:66/130；styles.css:455 的 :root 20px 回退被 inline 覆盖、不作对齐基准），采样余量维持 2×blur 不变式（40→8、16→8）；移除 web 没有的 brightness 1.03 亮度增益，`saturate(170%)` 保留
- **阴影 / 圆角 / 聚焦环 / 边框**：顶栏与输入栏投影分别对齐 web `0 8px 28px` 与 `0 8px 32px`（两栏阴影在 web 即独立声明，不合并为一条；CSS blur 半径 ≈2σ 换算为羽化 14dp/16dp、位移 8dp，色走新 token）；本版双发丝的外圈深线（hairlineOuter）撤销——引擎外圈描边改读 `glassBorder`（= web `1px solid var(--glass-border)`，亮 rgba(255,255,255,.75)/暗 .12，Mica 下原样生效）；圆角输入栏 26、顶栏 20、会话行 14、侧栏容器 24（胶囊保持全圆）；输入栏聚焦时出现 web 同款 `0 0 0 2px` 聚焦环（亮 rgba(164,85,28,.22) / 暗 rgba(206,138,86,.20)，0.25s 淡入对应 box-shadow .25s 过渡），焦点反馈不再只靠光标
- **玻璃扁平化（删 web 没有的四层）**：web Mica 玻璃只有「渐变填充 + backdrop 模糊 + 1px 描边 + 顶部内高光 + 柔和投影」五层，安卓在其之外多叠的四层全部删净不留死代码——流光边缘 RuntimeShader（`LENS_EDGE_SHADER`，含静态降级描边、uTime 动画循环与 30fps 节流常量）、多停靠 specular 厚度层、底部内阴影、磨砂颗粒（GRAIN_* 常量与共享噪声位图 `sharedGrainBitmap`）；随 lens shader 失去消费方的 `refractionStrength` 死参数（`liquidGlass`/`GlassSurface` 签名收窄）与无消费方的 `LiquidGlassShaders.createLensEdgeShader`/`isRenderEffectSupported` 一并删除。保留：两组玻璃填充、backdrop 模糊全套、外圈 1px `glassBorder` 描边、顶边内高光、栏级/卡片阴影、果冻按压（按压 scale 语义对齐 web 各元素的 ：active scale，弹性曲线不动）与消息滚动穿透悬浮玻璃的 Mica 布局
- **玻璃卡片「外凸 → 内凹」（对齐 web 观感）**：根因是旧版沿整条 `innerPath` 描一圈内发丝——四边亮线成环读作外凸珠边；web 的 `inset 0 1px 0` 内高光只渲染顶边一条，配合向下柔和投影才读作内凹面。① 整圈内描边删除，顶边内高光与既有 2dp 顶部高光**合并为一条** `.edge` 语义渐变细线（高 1.5dp、左右各内缩 10%、两端渐隐，styles.css:93）；② 外圈 1dp `glassBorder` 描边保留（= web border）；③ 卡片投影按 web `--g-shadow` **双影**核对（styles.css:18）：远影 `0 14px 40px rgba(110,70,30,.18)` → 羽化 σ20dp/位移 14dp，近影 `0 2px 6px rgba(110,70,30,.1)` → σ3dp/位移 2dp（新 token `glassShadowNear`；暗色 web 无次条 → 全透明，引擎 alpha==0 直接跳过）；Mica 两栏 box-shadow 在 web 是整条覆盖，故关掉近影、只留各自栏级阴影 + 位移 8dp；④ web 里本就无投影的玻璃面显式关影——顶栏状态点（`.sdot` 只有描边+内芯，11dp 小件套卡片级软影会糊成一团）、侧栏行与新会话胶囊（`.sb-item`/`.sb-new` 只有 hover 底色），`GlassSurface` 相应新增 `shadow` 开关（默认 true，关掉时两条影传 Transparent，引擎不建 Paint）
- **输入栏逐项对齐 web .input-bar / .input-box**：栏外留白与内衬 padding 走 web `.input-zone{padding:10px 16px 16px}` + `.input-bar{padding:10px 12px;gap:10px;align-items:flex-end}`（左右 16dp、距底 16dp、栏内 10/12、间距 10、底部对齐；顶栏同步由左右 12 → 16，避免与输入栏宽度不齐）。内衬输入框从 M3 TextField（内含 56dp 最小高度 `TextFieldDefaults.MinHeight`、无 contentPadding 入口，达不到 web 的 38px 下界与 10/15 内边距）换 **BasicTextField**：容器由不透明 `p.surface` 改半透明白（新 token `glassInputFill` = web `--inp`，亮 .55/暗 .07）+ r20 不变，顶部内凹阴影（`glassInsetShade` = web `--inset` 亮 rgba(120,80,40,.09)/暗 rgba(0,0,0,.32)）与底部 1px 白高光（`glassInsetHighlight`，亮 .5/暗 .08）用 `drawBehind` 近似，min/max 高 38/160dp（多行上限 7 行 × 20sp + 上下内边距 = 160dp），placeholder 仍走 meta token，全屏输入入口（Android 功能件）保留为 36dp 触点叠在右端。发送键补 web `inset 0 1px 0 rgba(255,255,255,.35)` 顶部内高光与 `0 6px 14px rgba(164,85,28,.35)` 投影（羽化 7dp/位移 6dp），按压 scale .92、禁用 opacity .45（禁用保留渐变整体降透明度，旧实现换灰底与 web 不一致）；附件键改 38dp chip 圆 + accent 图标（web `.input-attach` 的 chip 底只在 hover 出现，触屏取常驻态），下拉菜单功能保留；流式停止键（web 无，Android 功能件）并入同一 40dp chip 圆配方
- **模型弹层同步现行玻璃体系**：容器从实底 `surfaceElevated` 改 frost 版 `--glass-strong`（`glassFillStrong`，web `.sheet` 即 glass-strong 组）+ 顶圆角 28 → 26（`radius.sheetTop`）；**弹窗背景真实高斯模糊**——ModalBottomSheet 是独立窗口（`ModalBottomSheetDialogLayout` 实现 `DialogWindowProvider`），页内 record 的 backdrop 层不适用，改为经该窗口设 `FLAG_BLUR_BEHIND` + `attributes.blurBehindRadius`（4dp，与页内玻璃面同一 web 值换算），API < 31 无该 API/常量 → 不设 flag、回退自绘 scrim 压暗（降级路径写在注释里，系统关闭模糊/低端机同样落这条路径）；列表行由旧版黄色 UI 换 `GlassSurface`（Frost 填充 + `glassBorder` 描边，与危机卡同款），头像字母块从不透明黄改 chip 半透明 accent 底 + accent 字，`默认/视觉` 徽章同 chip 语言（`Tag` 新增 `CHIP` 种类），选中行对齐 web `.mrow.on`（`glassChip` 底 + 1px `glassFocusRing` 描边（= web `--l2b`）+ 实心对勾），色值全部走 token、明暗两套核对
- **模型弹层逐项对齐 web `.sheet`/`.mrow` 终版**（09-29 二次迭代，模拟器装机验证）：① 容器改 `liquidGlass` 玻璃卡片——`GlassFill` 新增 **Strong 组**（frost 版 `--glass-strong` 直色，styles.css:86-91 弹层分组），引擎一次画齐 web `.glass-strong.edge` 完整层叠（Strong 填充 + `glassBorder` 1px 发丝描边 + 顶边内高光线 + `--g-shadow` 双影），`backdrop=false`（真实磨砂由窗口级 FLAG_BLUR_BEHIND 承担，不跨窗口取样页面层），API<31 降级不透明 elevated 面；② 模型行改 **web `.mrow` 无卡片扁平行**（r16 透明底、padding 11/12；上一版包 GlassSurface 玻璃卡与 web 扁平列表不符）——选中 chip 底 + 1px l2b 描边 + **20dp 勾选圈常驻**（未选中 muted 空圈、选中 accent 实底白勾），头像块 40dp r12 chip 底 accent 缩写，标题 16→13sp、副文案 11sp muted（「提供商 · 支持图片 · 默认」并入一行，去掉徽章 chip 与分组头），头部对齐 `.sheet-head`（标题 15sp/500 左 + 副文案 11sp 右，去掉 X 按钮与「当前」行），拖拽条 muted 35%；③ **弹层跟随全局色相**——独立窗口不在主窗口全局色相层子树内（曾为已知取舍），现弹层内容自套同一 W3C hue-rotate 矩阵（`LocalFluidHue` 随 composition 传播进 Dialog；填充由内容层自绘随层一起转，不双重旋转；色相 0° 零开销透传），色相≠0 时弹层与主界面同步变色，对齐 web body filter 罩住 `.sheet` 的语义
- **流光色相全局跟随**：对齐 web 把 `filter: hue-rotate(var(--wy-fluid-hue))` 挂在整个 body 上的语义——色相不再只转流光基色（UI 层固定色不跟随），而是在应用内容根部（MainActivity）挂同一 W3C 亮度保持矩阵的全局 colorFilter 图层（`drawWithContent` + `saveLayer` 合成期套矩阵——本项目的 Compose ui 1.8 graphicsLayer 作用域尚无 colorFilter 属性，故用等价实现；`FluidAppearance.hueRotateColorMatrix`），流光 + 玻璃 + 文字整树一起转；`FluidBackground`/`GlowBackground` 改画未旋转基色防二次旋转；色相 = 0（默认）直接透传绘制、零开销。已知偏差（web 无此概念）：Compose Dialog/ModalBottomSheet 等独立窗口不随全局层旋转
- **测试重校准**：`GlassTransparencyReadabilityTest` 按两组填充实测值重钉——Frost 组（承载卡片正文）浅色 fg 6.95/最坏 6.47、深色 fg 9.01/最坏 8.66、深色 fgSecondary 6.20/最坏 5.96（均 ≥4.5 AA 达标）；悬浮栏 Card 渐变组浅色 fg 5.64/最坏 4.99 达标。低于 AA 转记录性断言：浅色 fgSecondary（Frost 4.46/Bar 3.62）与 muted（浅 2.68/暗 4.07）——web 的 frost .30 与 .150/.105 渐变本就是这个透明度、正文对比由 fg 承担：**卡片与正文用色点的 fgSecondary 一律改为 fg**（ErrorCard 正文、CoachCard「接住你」与理由、空态示例问题——v1.9.4 评审修复；token 未改、填充未回退）。钉值经复刻 Compose Float16 色彩管线（通道 half 存储 + Float32 矩阵）的脚本复算，对旧配方 12 个既有钉值误差 ≤0.009 验证口径
- `docs/design-tokens.json` 同步：glass.fill/fillStrong 重定值，新增 cardFillTop/Bottom、shadowTopBar、shadowInputBar、focusRing、shadowNear、inputFill、insetShade、insetHighlight、chip，撤销 hairlineOuter（无 web 对应，外圈描边改读 border）、`hairlineInner` 改名 `insetHighlight`（语义改「1px 白内高光」，值对齐 web `.input-box` 底部 inset 亮 .50/暗 .07→.08），radius 新增 inputBar/topBar/sessionRow/sidebar 四项、sheetTop 28→26，glassBackdrop blur 20→4、cardBlur 8→4、brightness→1.0；component.inputBar 与 component.sheet 按 Mica 配方重写、新增 component.sendButton（发送键渐变/内高光/投影/按压/禁用态），删除 specular/innerShade/grainLight/grainDark 四组玻璃色与 component.glass 磨砂颗粒参数（随扁平化失去消费方）

**桌面版迭代（2026-09-29，版本号 1.9.3 → 1.9.4）**：
- **Web 端冷启动会话恢复**：当前会话 id 持久化到 `localStorage`（`wenyan.sessionId`），侧栏切换 / 搜索跳转 / 新建会话三处写穿，删除当前会话、「新会话」按钮、备份恢复、清空全部四处清 key（不留指向已删数据的悬挂键）；启动时在 `refreshSessions` 之后校验该 id 仍在会话列表内（不在则清 key 回空态），末尾 `renderChat()` 自然落到恢复的会话
- **记忆导出/导入（与安卓 v1.9.4 对齐）**：设置页「数据管理」在全量导出行之后新增「导出记忆档案」（浏览器直接下载 `wenyan-memory-YYYYMMDD.json`）与「合并导入记忆」（JS 创建隐藏 file input，二次确认明示「只新增/合并，不会删除任何现有数据」，`index.html` 未动）；后端新增 `GET /api/memory/export` —— 只含 targets/facts/profile 三段 + 文件头 `{app, version, exportedAt}`，元素字段逐字段照抄既有全量导出与安卓 `BackupRepository.exportMemoryJson`（target 8 键 / fact 6 键 / profile 4 键，无 profile 写 `JSONObject.NULL` 但保留键），`Content-Disposition: attachment; filename="wenyan-memory-<yyyyMMdd-HHmm>.json"`，GET 沿用统一拦截器只校验 Host、不校验 token（未新增任何 token 逻辑）；`POST /api/memory/import` —— Host + `X-Wenyan-Token`，事务包裹（`withTransaction(DEFERRED)`），异常显式重抛 `CancellationException`、其余兜底 `{ok:false}` 不抛 500，响应 `{ok, message, error}`，成功时 message 为中文合并摘要（如「导入 1 个档案、2 条记忆，跳过 1 条重复」）
- 合并规则与安卓逐条一致（提出为文件级 `internal` 函数 `mergeMemoryImport` / `validateMemoryExportHeader`）：文件头收 `wenyan-desktop` / `wenyan-android` 且 version≥1，非法即返回中文原因且不触碰 DAO；档案按 trim 后 codeName 精确匹配复用本地 id、匹配不上才新建、空名档案整体跳过、文件内同名只建一档；记忆经 targetId 重映射后按 text 精确去重（空白/null 跳过且不计统计）、每档案上限 `MemoryExtractor.DEFAULT_FACT_LIMIT`=50（超出跳过）、`kind` / `expiresAt` / `source` / `createdAt` 原样保留；profile 仅在本地 `getLatest() == null` 时写入；**全函数只有 insert，无任何 delete/clear**
- **测试 20 例**（新增 `app/desktop/src/test/kotlin/MemoryMergeImportTest.kt`，无既有 DB 测试基建故自建 `Room.inMemoryDatabaseBuilder` + `BundledSQLiteDriver`，与桌面生产同驱动同 `AppDatabase_Impl`，走真实 DAO/SQL/事务而非假 DAO）：导出三段与全量导出逐字段一致、导出→导入空库往返（含 kind/expiresAt/source/createdAt）、同名合并去重且本地数据零删除、trim 匹配、文件内同名只建一档、新档案全字段保留、每档案 50 上限与跨档案独立计数、profile 仅空时写入/不覆盖/null 不写、文件头三类非法与安卓导出文件可作合并源、垃圾条目与缺段容忍、桌面全量导出只取记忆段（sessions/messages 不导入）、事务中途失败整体回滚（真实 insert 后被 JSONException 打断，断言新档案已回滚而原数据完好），以及一条 HTTP 端到端（真实 CIO 仅绑 127.0.0.1 环回：GET 无 token 可下载且带 attachment 头、POST 无 token 403 且未写入、带 token 合并成功、非法 JSON 返回 200 + `ok:false`）
- 版本号 `DESKTOP_VERSION` 1.9.3 → 1.9.4，Web 端 `APP_VERSION` 兜底默认值同步为 1.9.4（仅在 `/api/health` 取版本失败时兜底显示）；新增动态文案全为静态中文串、错误信息走 toast（`textContent`），未新增 innerHTML 注入面，服务端自身不发起任何外部 URL 请求

**其他**：手机 versionCode 41；桌面 DESKTOP_VERSION 1.9.4（Web 端 APP_VERSION 兜底同步）。

## v1.9.3（2026-08-16）— 代码审查修复 + O7 知识路由最终落地

**桌面版迭代（2026-08-16，覆盖发布，版本号保持 1.9.3）**：
- 玻璃主题改为沉浸式：流体背景穿透设置页/主界面，玻璃面板真正浮在流体上，不再坐在纯色块上
- 玻璃选项对齐插件：玻璃模糊度(默认 4px)、磨砂度(默认 30%)、背景流体颜色(默认 0°，可改色)、背景亮度(默认 50%)
- 流体默认颜色跟随当前 UI：亮色主题用暖米白/陶土橙棕，暗色主题用深棕/暖褐
- 移除粒子鲸鱼选项及动画
- 修复“边缘渐变模糊”开关点击后不刷新状态的问题
- 流体背景 1:1 移植插件 WebGL2 fluid-shader（两段式 flow + 域扭曲噪声），修复进设置返回后流体消失
- 顶栏/输入栏改为插件同款悬浮玻璃卡：脱离文档流浮于消息流之上，滚动时消息从栏底穿过并被磨砂；卡片材质换插件配方（纵向渐变填充 + 内高光 + 柔和投影）


**安全与正确性（H1–H7）**：
- 流式重试新增 `LlmEvent.Restart`，预览不再重复拼接
- `finish_reason=length` 识别为 `OUTPUT_TRUNCATED`，不再当作成功
- 解析失败落库 `freetext` 展示原文，双端行为统一
- 更新安装权限、CSRF Token/Host 校验、构建目录与 JDK 配置清理

**健壮性与工程化（M1–M12 / L1–L15）**：
- Room 统一 2.7.2 + version catalog
- 双端编排抽到 `ChatOrchestrator` 共享
- 知识引擎 LRU 缓存、分块截断、危机词库、token 估算、密钥/指纹/EXIF/更新校验、CI lint+APK artifact 等全部落地

**优化项（O1–O10）**：
- 数据导入恢复、记忆冲突检测 + 时间线、会话搜索、`:shared` KMP 模块、LLM 调用合并、用量指标、Baseline Profile、压缩透明提示、草稿恢复
- **O7 知识路由最终落地**：BM25 评测 + 820 条 query 变体库 + `HybridVariantRouter` fill-one 生产上线（1098 条 / 41 文档：P=0.348 / R=0.649 / F1=0.453）

## v1.9.2（2026-08-13）— 等待/流式回复对齐桌面端 + styles 话术自主判断

**① 等待/流式回复对齐桌面端**：
- 等待气泡全套对齐桌面端 `.think-bubble`：玻璃容器（16dp 圆角）+ 3px 三点呼吸动画（breathe 1.2s，scale+opacity 错峰）+ 三档文案——普通「正在翻知识库，梳理你的处境…」/ 截图转述中「视觉模型正在提取截图文字…」/ 确认转述后「军师分析中…」
- 流式期间不再边流边出预览：等待气泡常驻，完整回复到达后一次性整块渲染（与桌面端 card 帧语义一致）
- 移除 reasoning 思考折叠面板（ThinkingPanel）与 StreamingPreview 流式预览逻辑，对齐桌面端
- 手机端 `transcribing` 状态补齐（此前为死字段），新增 `confirming` 状态

**② styles 话术由模型自主判断**：
- 话术区从「有条件必填」改为「场景判断制」：仅当用户明确要"这句怎么回/怎么发给对方"且关系健康适合回复时给 1-3 条
- 覆盖四类不给话术场景：向 AI 倾诉/要安慰（对话对象是 AI 自己）、要分析/判断/复盘、关系语境（已拒绝/该放下/被操控/想操纵/无发送对象）、状态类（uncertain/greeting/明确不要/已给过/安全危机）
- reply 与 styles 一致性收紧：styles 留空时 reply 必须同时留空
- 双端共享同一 PromptBuilder，桌面端自动生效

## v1.9.1（2026-08-12）— 记忆增强三件套（上下文压缩 / 时效记忆 / 来源标注）

**② 上下文预算选择式压缩**：
- 长会话历史超限时不再「整轮丢弃」，改为共享 `HistoryCompactor` 两段式压缩：早期消息逐条裁剪保头（每条保留 200 字 + 「…[已省略]」标记），末尾 6 轮工作集保持完整；仍超预算才成对丢弃
- 双端共用同一纯函数实现（手机 `RealChatRepository` + 桌面 `ChatEngine`），行为强一致，JVM 单测覆盖

**③ 时效记忆自动过期（DB v7→v8）**：
- `memory_fact` 加 `expiresAt`（可空，毫秒）：提炼时模型标注 `expires_in=today|week`，本地换算到期时间戳（today→次日 0 点 / week→下周一 0 点，系统时区）
- 到期事实不再注入回答（惰性过滤，不物理删）；设置页档案详情可看到「临时」徽标，一键「转永久」
- 老数据迁移默认永久（expiresAt=null），不猜测

**④ 素材来源渠道标注**：
- `memory_fact` 加 `source`：paste（粘贴记录）/ transcription（截图转述）/ chat（口述）/ manual（手工，老数据默认）
- 注入时对「截图转述」来源自动标注「（来自截图转述）」——转述可能有误差，提示模型不可全信；设置页条目带来源徽标
- 桌面版：记忆 API 透传新字段 + `/api/facts/{id}/permanent` 转永久端点 + 前端徽标/转永久按钮

**其他**：手机 versionCode 38；桌面 DESKTOP_VERSION 1.9.1；老数据全部兼容（v1→v8 全链路迁移测试通过）

## v1.9.0（2026-08-11）— 知识库同步 + 记忆增强

**知识库（同步上游 goutoujunshi 8-11 版）**：
- 40 份知识文档全量更新（依恋/冲突/法律安全等全部主题实质修订）+ 新增《公开表达案例的伦理转译》（北疯/李洋洋公开案例的合规转译，配关键词路由）；知识库总数 40 → 41 份
- 说话人映射锁定：长聊天记录先建立并锁定「用户/对象」映射，不按左右气泡、语气或性别猜
- 拒绝细化：一次拒绝具体时间或方式 ≠ 整段关系拒绝（明确不想发展/反复不欢迎才停止推进）
- 混合素材分级：截图能证明 / 转述提示 / 仍未知，线下通话区分观察-感受-解释
- 关系趋势边界：K线/评分曲线等技术指标不是关系测量，不得判断爱意忠诚分手概率

**记忆增强（B2–B6）**：
- **自动记忆开关（B2）**：设置页开关（默认开），关闭后不再自动提炼
- **事实/推断分层（B3）**：DB v6→v7，memory_fact 加 kind 列；模型推断类事实标「推测（待验证）」注入提示，设置页记忆列表带「推测」角标
- **写入回执（B4）**：自动提炼成功后回复侧 Toast 提示「已记住 N 条事实，可在设置中查看或撤销」
- **撤销最近一次（B5）**：设置页一键撤销最近一轮自动写入的事实（日志存 DataStore/Properties，保留最近 5 次）
- 双端同步：手机版 + 桌面版（设置页 UI / 提炼链路 / 撤销 API 对齐）

**v1.9.0 迭代（2026-08-12，覆盖发布，versionCode 36）**：
- **空状态排版协调**：内容收进 296dp 版心并整体居中（内部保持左对齐），不再贴屏幕左缘；垂直由正中改为偏上（顶部留白 = 可用高度 12%，刊头感），整体视觉更协调

## v1.8.1（2026-08-08）— 液态玻璃 2.0 + 修复包

**液态玻璃 2.0（v1.8.0 合入）**：iOS 26 风格边缘伪折射（API 33+ AGSL RuntimeShader，色散条纹+动态波纹）；按压效果移除；设置页方框玻璃统一为圆角卡片。

**修复包（B1–B5）**：
- **B1 SSE 连接泄漏**：重试时递归创建新 EventSource，取消逻辑仍 cancel 首次旧引用 → 重试中的 OkHttp 长连接不释放。改 `currentEventSource` 持有当前实例 + `awaitClose` 释放
- **B2 RuntimeShader 崩溃 + 每帧重建**：个别 ROM AGSL 编译失败在绘制路径直接崩溃；改 `runCatching` 回退静态亮边，且 shader 移入 `drawWithCache` 缓存块一次创建（不再每帧重建）
- **B3 弹层模糊冲突**：`LaunchedEffect(blurRadius.value)` 每帧取消重建协程 + 每帧全屏模糊；且 onDispose 误清主界面抽屉模糊（两套模糊并存叠加）。按「两套模糊不可并存」原则整体移除弹层 decorView 模糊，保留 ModalBottomSheet scrim 遮罩
- **B4 光斑 dead path**：`glowPositions/glowIntensities` 接收后从未使用，却经 `GlowState` 每帧写 state 引发 60fps 全屏重组。删 dead path（参数/回调/GlowState/未用 shader 全清）
- **B5 深色模式启发式**：`bg.red < 0.5f` 在陶土棕/中性灰下误判 → 改读 `LocalGtjIsDark` 显式 token（Theme 层解析后下发）

## v1.7.6（2026-08-07）— BYOK 兼容性加固

- **移除 temperature 参数**：Kimi Code / OpenAI 等推理模型（thinking-only）只允许 temperature=1，此前固定发送 0.7/0.3 会被 400 拒绝 → 请求体不再发送该字段（对所有 OpenAI 兼容服务通用，服务端采用默认值）
- **Base URL 规范化与校验**：保存时自动 trim、去尾斜杠、剥掉误填的完整端点（/chat/completions）；含逗号/空格/中文等非法字符时阻止保存并明确提示
- **错误提示修复**：错误卡片与测试连接红绿灯改按错误码枚举名匹配（此前按 "401"/"404" 数字匹配全部落空，真实原因被掩盖成「模型返回错误」）；测试连接透传服务端真实错误正文

## v1.7.2（2026-08-06）— 跨会话记忆 + 多记忆档案

- **跨会话记忆**：AI 在不同会话间记住「咨询对象」的关键信息，咨询体验连续、自洽
- **多记忆档案**：target 表多行化（DB v4→v5，MIGRATION_4_5 仅加列，老数据不丢）；设置页新增「记忆」分组（模型服务之后、外观之前），支持新建 / 改名 + 编辑正文 / 删除（二次确认 danger）/ 切换激活（Toast「已切换到「X」的记忆」）
- **会话档案归属**：新会话绑定当前激活档案（切档案只影响新会话）；聊天注入 = 会话归属档案优先；老会话（targetId=null）注入空档案 = 现状行为，不报错
- **自动记忆提炼**：回复完成后（新话题/首话题）由主模型自动提炼新事实写入档案（`domain/MemoryExtractor.kt` 纯逻辑，merge 去重幂等 ≤2000 字，20s 超时失败静默）；设置页「自动记忆」开关默认开
- **Prompt 生效**：`buildProfileJson` target 加 `memory` 字段（=note）；档案确有记忆时追加 `CorePrompt.memoryRule` 使用规则
- **会话列表档案 Tag**：抽屉会话标题行右侧显示归属档案 Tag（`name.trim().take(4)`，老会话不显示）

## v1.7.1（2026-08-06）— 液态玻璃迭代打磨 + 终检修复

- **沉浸式玻璃**：顶栏/输入栏普通玻璃透出光斑；模型弹层与侧栏真高斯模糊（API 31+ RenderEffect，targetValue 驱动跟手动画）；抽屉模糊覆盖全背景
- **返回二次确认**：主页两次返回才退出（防边缘滑退误触）
- **玻璃质感**：厚度层（上微光+下微影）、高光加宽、投影不被裁剪、内容内边距加大
- **空状态重排**：功能导向布局（日期→引导语→示例卡→引导卡）
- **终检修复（versionCode 26）**：
  - 修复弹层关闭时模糊清理缺少 API 31+ 守卫导致的低版本崩溃（Android 8–11）
  - 网络安全配置：公网强制 https，localhost/127.0.0.1 允许明文（本地模型服务可连）
  - 新增 UNSUPPORTED_URL 错误码，明文地址给出明确提示而非伪装成超时
  - dataExtractionRules + fullBackupContent：云备份与换机迁移均排除聊天数据

## v1.7.0（2026-08-05）— 液态玻璃 UI

- 全 App 统一玻璃材质（半透明填充 + 顶部高光 + 细描边 + 柔和投影，自包含绘制）
- 暖色光斑背景缓慢漂移（22/26/30s 周期），"移除动画"设置下自动降级
- 模型 pill 内嵌状态点（已连接/思考中/失败）、用户气泡深棕 tint 与 AI 白色玻璃对比
- 18 组玻璃 token 对比度断言全绿

## v1.6.x（2026-08-05）— 回答结构改造与文本选择

- **v1.6.0** 全部输入统一四段结构 JSON（接住你→事实三分→军师建议[三风格话术本地切换]→行动）；老数据兼容映射，无 DB 迁移
- **v1.6.1** 多图发送（≤10 张一次分析）；启动图标整稿；长按菜单"选择文字"
- **v1.6.2** 部分选择完善：进入即全选拖手柄（readOnly TextField 方案）；模型回复也可选
- **v1.6.3** 沉浸式手势小白条（edge-to-edge 双 scrim 透明）；模型管理厂商红绿灯

## v1.5.0（2026-08-05）— 布局级重构

Arc/Things 温暖质感：顶栏标题+模型状态点、空状态引导卡、悬浮胶囊输入栏、AI 分析卡卡片头、便签化思考面板、312dp 会话抽屉、标签云选中态实底。

## v1.4.0（2026-08-04）— UI 重设计

墨绿×宣纸色板、标题字重 500、卡片圆角 18dp。

## v1.3.x（2026-08-04）— 图片与流式

- 图片气泡去框融合、点击全屏预览、待发送图文同发
- SSE 流式输出、思考折叠、失败重试不重复发、息屏/退后台回答继续

## v1.2.x（2026-08-03/04）— 改名与基础完善

改名「温言」（原狗头军师）、AI 会话标题、图标居中与清晰度。

## v1.1.3（2026-08-03）— 首个可用版本

单聊天 App 骨架：启发式输入路由（聊天记录/转述/短句/问候）、知识库路由、会话抽屉、模型管理。
