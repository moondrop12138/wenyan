# Changelog

「温言」版本历史。版本命名：`vX.Y.Z`（功能）与 `vX.Y.Z-N`（同版本迭代构建）。

## v1.9.5（2026-10-04，消费级上线六轮迭代，versionCode 45）— 新用户激活 + 离线弱网预检 + 路由成本透明 + 路由精度 + 上线硬化 + 测试连接生命周期

- **新用户激活**：问卷末屏无模型追加配置入口 + 跳过文案分支、空状态引导卡、聊天三入口无模型拦截弹窗、设置空行加按钮与问卷画像行
- **离线弱网预检**：NO_NETWORK 错误码 + 三 flow 落库后网检短路 + UI 离线拦截、ACCESS_NETWORK_STATE 常态权限
- **路由成本透明**：开关行成本小字、RouteDiagnostics 进程内诊断透传至用量面板
- **路由精度**：29 篇 miss 相关目录摘要收窄 + SYSTEM_PROMPT 路由规则、30 字门禁与 LeakGate 合规、评测集只评不训
- **上线硬化**：release 签名精确 fail-fast、clearCrashOnly、崩溃关怀卡、下载节流进度、MemoryEdit 三态加载门
- **测试连接生命周期**：ProviderEditSessions Holder 单例（IO scope + per-key 会话 + 快照 + 行级锁 + TTL）、新建页稳定键 pe-new / 编辑页 UUID
- 验证：testDebugUnitTest 587/587 全绿、lintDebug 通过、knowledgeCheck 通过、assembleRelease 成功（44 包实测，45 仅改版本号未重打新包）、adb 冒烟 49/49 全绿

## v1.9.4（2026-09-28，09-29/09-30/10-01/10-03 迭代）— 冷启动会话恢复 + 记忆导出/导入 + 流式状态修复 + 流光背景与外观可调 + 液态玻璃质感升级 + Mica 视觉对齐/玻璃扁平化与内凹改版/色相全局跟随/输入栏与弹层对齐 web + 触摸修复 + 全量代码审查修复 + LLM 参与知识路由（决策门通过，默认 llm）+ 弹层色相跟随（17 处独立窗口弹层统一跟随流光色相）

**弹层色相跟随（10-03 迭代，versionCode 44）**：
- **根因**：色相跟随 = MainActivity 在主窗口内容根部挂的全局 hue-rotate 图层（MainActivity.kt:83-107，saveLayer + W3C 矩阵，与 web body 级 filter 同语义）只罩主窗口——Dialog/AlertDialog/DropdownMenu 是独立 Android 窗口不在层内，取到未旋转的暖色基础调色板（米色 #EFE6D8/棕褐 #2B221A/橙 #A4551C），表现为弹层与主界面色相不符（真机截图：消息长按菜单/附件菜单/用量诊断/隐私声明四处）；凡同类弹层皆同病，全仓清点共 17 处
- **修法**：新增 `ui/components/glass/HueWindow.kt`——`GtjPalette.hueRotated(hue)`（逐字段经 FluidAppearance 同一条 W3C 矩阵旋转，0 度逐字段恒等/保灰/保 alpha）+ `GtjWindowTheme{}` 包裹组件（旋转后调色板同时注入 LocalGtjColors 与 MaterialTheme colorScheme，M3 默认容器/按钮色一并跟随；透传现有 typography/shapes 防排版退回库默认；hue==0 原样透传零开销）；17 处弹层在各自定义内包裹：消息长按菜单/删除消息确认/删除会话确认（ChatScreen）、附件菜单/全屏输入页（ChatInputBar）、隐私声明/导入备份/导入记忆/清除档案（PrivacyDialogs）、记忆命名/记忆删除（MemoryDialogs）、事实编辑/冲突解决（MemoryEditScreen）、删除服务商（ProviderEditScreen）、更新提示（SettingsScreen）、用量诊断（UsageMetricsDialog）、跳过问卷（SkipDialog）；只改取色不动布局/文案/行为，ModelSheet（自套矩阵本就正确）与 web 端（hue-rotate 挂 body 天然跟随）未动；复核代理 grep 确证 14 处 AlertDialog 调用点全部包裹、唯一未包裹独立窗口仅剩黑白看图器（有意豁免）
- **包裹层 remember 约束**：GtjWindowTheme 在 hue 跨 0↔非0 时换槽位重建，包内 remember 会丢状态/换实例——FactEditDialog 的 `var text` 草稿与 FullScreenInputDialog 的 focusRequester 均留在包外（后续新增弹窗照此约束）
- 注释与验证：MainActivity.kt:82 过期注释同步（「其余 AlertDialog 仍不跟随」→ 经 GtjWindowTheme 跟随）；`:app:compileDebugKotlin`/`:app:assembleDebug` 退出码 0；色相≠0 实机视觉验收由用户执行

**10-01 迭代（LLM 参与知识路由）**——LLM 参与知识路由（决策门通过，默认 llm）+ 路由评测基建（去泄漏/金种子/分桶/基线导出）+ 三臂盲评：

**玻璃可调（对齐桌面 web 玻璃设置，安卓端；版本号不 bump）**：
- 设置页「外观」新增「玻璃」卡两条运行时滑条——玻璃模糊度 0-60dp（驱动全部玻璃 backdrop 模糊：悬浮顶栏/输入栏、各页玻璃卡片、模型弹层窗口模糊，= web 单一 `--wy-glass-blur` 语义，styles.css:515-518；滑条 app.js:1069）与磨砂度 0-100%（按 web 公式运行时派生玻璃填充 alpha，`GtjPalette.withGlassFrost`：glassFill=frost / strong=frost+0.17/.29 / cardFillTop/Bottom=frost×0.50/×0.35 与 ×0.50，styles.css:483-488/526-531）；默认取磨砂档 12/60（上下栏默认即磨砂、不透底），静态基础 token 仍 frost .30 = web 基准，运行时派生不改基值；链路照 bgBrightness 五件套（DataStore `glass_blur`/`glass_frost` 双向夹取 → 契约默认实现保测试 Fake 零改动 → Settings/AppViewModel 回流 → `LocalGlassBlur` 全局下发 / `GtjTheme(glassFrost)` 派生色板，拖滑条 60ms 合并落盘）；token 登记 `design-tokens.json component.glassAppearance`，钉值护栏 `GlassTransparencyReadabilityTest`（backdrop 12dp + withGlassFrost(0.60) 派生钉值）；web 端（app/desktop）未动

**玻璃模糊力度二改（10-02 迭代，版本号不 bump）**：
- 真机实测 12dp 模糊力度不足：玻璃模糊度**默认 12→20dp**（= web `.glass` 基础档 blur(20px)，styles.css:81）、**量程 0-60→0-100dp** 留加糊余量；`GlassBackdropParams` 四值 12/24/12/24→20/40/20/40（2×采样余量不变式保持）、DataStore `glass_blur` 夹取范围与默认同步 0..100/20（修复字面量夹取会把 >60 的滑条值截回的隐患）；雾化 √ 曲线默认/满档比例不变（20/100 = 旧 12/60 = 0.2），默认档雾 alpha 钉值 0.4114 不漂移；钉值重钉 `GlassFogLegacyTest`（默认档改读 `GLASS_BLUR_DEFAULT`、新增 12dp≈0.3187 与 200dp 越界夹取）与 `GlassTransparencyReadabilityTest`（backdrop 20dp+40dp）；token 登记 `design-tokens.json component.glassAppearance/glassBackdrop`；web 端未动
- **平台模糊半径像素上限发现 + 降采样放大（`blurRecordScale`）**：模拟器 API 35 实测 RenderEffect 模糊半径存在 **~64 物理像素硬上限**（52px 有效、66px 起整个效果被**静默丢弃**——半径扫描 20/25/30/40/99dp 定位边界；这同时解释真机「拖大不更糊」）——旧实现下滑条超过 ~24dp（设备相关）即落入完全无模糊死区。修复：悬浮栏 underlay（`recordBlurUnderlay`）半径像素超 `MAX_BLUR_PX=52` 时把内容按 s=52/半径 **缩小录进模糊层、画回时放大 1/s**——层空间模糊半径恒 ≤ 上限（效果不再被丢弃），视觉模糊全量程有效；上行重采样自带磨砂（力度只增不减）；≤上限走既证可靠的 1:1 路径逐位不变。已知平台约束：单次 RenderEffect 在绘制目标空间应用，视觉模糊封顶 ≈52px（滑条 20dp 起进入平台满档平台），100dp 与 30dp 观感同级但不再像旧版那样直接丢模糊；钉值 `GlassRecordScaleTest` 新增（1:1 段/缩放段/全量程层空间不超上限/放大还原视觉半径）

**玻璃模糊量程三改（10-02 迭代，用户指定，版本号不 bump）**：
- 玻璃模糊度滑条量程改为**最低 100 / 最高 500dp、默认 100**（用户指定重度磨砂：默认即最低档）；`GlassBackdropParams` 四值 20/40/20/40→100/200/100/200、DataStore `glass_blur` 夹取同步 100..500/默认 100（旧存值 <100 读出被下限静默迁移）；量程全段超平台单次模糊上限（~52px，见二改发现），由降采样放大（blurRecordScale）保证全段渲染——视觉进入平台满档平台（结构熔化程度随半径加深、渐进增强）；雾化 √ 曲线同比例（100/500 = 0.2），默认档钉值 0.4114 不漂移；`GlassFogLegacyTest` 重钉（满档 500dp/越界 600dp、1000dp；低半径对比点改最低档 100dp）、`GlassTransparencyReadabilityTest` 重钉（backdrop 100dp+200dp）

**玻璃能力探测降级为观测 + 探测加固（10-02 迭代，versionCode 42）**：
- **探测从「行为门禁」降级为「运行时观测」**：`rememberGlassBackdrop` 删除 `if (!probeCapable) return null` 门禁——API 31+ **恒创建** backdrop 垫真实高斯模糊（冷启动「雾化过渡」窗口随之消失，真实模糊立即可用）；`liquidGlass` 的 fogFallback 收窄为**仅 API<31**（探测状态移出全部行为条件）。行为判定唯一来源 = 新增纯函数 `glassRenderMode(sdkInt)`（`GlassRenderMode.kt`，`rememberGlassBackdrop`/fogFallback 双消费方防门槛漂移），`GlassRenderModeTest` 钉 30/31 边界
- **已知取舍**：在「模糊构造真不渲染」的设备（探测当初动机）上，31+ 改为模糊静默失效 + 无雾化兜底的裸半透明玻璃（可读性兜底对该类设备整体失效）——让探测继续充当行为开关（`SDK<31 || state==FAILED` 折中）与「降级为观测」目标相悖，按计划取舍；对模糊可用的设备（含用户真机）为纯改善
- **探测加固**（防「永停 PROBING」与病态管线）：`probeAsync` 加 **2s 超时兜底**——超时也翻转出 FAILED（原因 TIMEOUT），结论写入经 `tryConclude` 单写者收敛（渲染线程结论与超时兜底竞速，后到者被拒，无双写竞争）；`grabFrame` 检查 `syncAndDraw()` 返回值（AOSP `SyncAndDrawResult` 约定 SYNC_OK=0 才是干净出帧，非 0 位标志一律判失败）+ `acquireLatestImage()` 空读回短重试（共 3 次尝试、间隔 20ms）；**每次结论打 logcat**（tag `GlassBlurCapabilityProbe`，verdict/eSharp/eBlurred/比率/失败原因：异常·读回空·超时，对齐 RealChatRepository 的 tag=类名惯例）
- **设置页「玻璃」卡运行时状态行**：31+ 显示「真实高斯模糊：已启用 · 探测已确认/未通过/超时/未见结论」（由 `GlassBlurCapabilityProbe.state`+`failure` 快照态驱动，结论翻转自动重组刷新；映射纯函数 `glassProbeStatusText` 钉四态）；<31 显示「雾化兜底（系统低于 Android 12）」，与原静态说明合并为一条
- 测试：`GlassBlurCapabilityProbeTest` 补同步返回值判定/空读回重试边界/超时预算/观测能量明细（`decideDetail`）钉值；`GlassFogLegacyTest`/`GlassRecordScaleTest`/`GlassTransparencyReadabilityTest` 既有钉值未动（雾化曲线与 CAPABLE 路径视觉未变）
- **评审修复①（卡片磨砂接入降采样放大）**：`GlassBackdropLayer.recordBlur` 与悬浮栏路径（recordBlurUnderlay）同构接入 `blurRecordScale`——运行时滑条 100-500dp（≈262-1312px@420dpi）超平台 RenderEffect 硬上限（~52px 有效/66px 起整个效果被静默丢弃，见二改发现）时缩小录层、`drawBackdropBlur` 按 `recordedScale` 画回放大 1/s，卡片透光磨砂全量程有效；旧实现半径原样入 BlurEffect——滑条三改起步 100dp 后卡片模糊整段被平台丢弃（HEAD 卡片 4dp≈10px 在阈内原本可用），构成回归，设置页/CHANGELOG「量程全段由降采样放大渲染」的宣称自此对卡片成立
- **评审修复②（栏 underlay 首帧无裁剪 + 失效链补全）**：`barSize`/`barShapePath` 由普通 var 改 Compose 快照态——形状由玻璃 cache 块在 draw 阶段写入、而玻璃节点晚于内容宿主绘制，旧实现首帧 shape=null 跳过圆角裁剪且 var 写入不触发失效，「栏+2×pad（默认 200dp）」整块模糊影像无裁剪盖在内容上、静止画面下永久驻留（栏尺寸后续变化不伴随位置变化时同样不重录）；现首帧兜底 `clipRect` 裁到栏矩形本体（几何正确、仅缺圆角），形状/尺寸快照态写入驱动内容宿主下一帧带圆角重录（draw 阶段读快照 = 既有 barPositionInRoot 同款观察模式）

**玻璃模糊度滑条移除、锁死 100dp（10-02 迭代，用户指定，versionCode 43）**：
- 设置页「玻璃」卡移除「玻璃模糊度」滑条（磨砂度滑条保留）：模糊半径锁死 `GLASS_BLUR_DEFAULT` 100dp——全部 backdrop 模糊（悬浮顶栏/输入栏、各页玻璃卡片、模型弹层窗口模糊）与雾化 √ 曲线统一取该值，渲染现状与滑条停在 100dp 时一致；`LocalGlassBlur` 整条下发链路移除（DataStore `glass_blur` key 停用不再读取、历史存量留库无害；契约默认实现/RealSettingsRepository/AppViewModel/MainActivity 管线随之删除），消费方（LiquidGlass/ChatScreen/ModelSheet）直读常量；`GLASS_BLUR_MAX` 保留为雾化 √ 曲线满档归一（默认档雾 alpha 钉值 0.4114 不漂移）；web 端（app/desktop）滑条不受影响；token 登记 `design-tokens.json component.glassAppearance`（blurRange 字段删除、note 记录收尾决定）

**玻璃 backdrop 模糊静默失效修复 + 构造加固（10-01 迭代，版本号不 bump）**：
- **根因（栏模糊在真机与模拟器上均完全不渲染）**：Compose ui 1.8.3 的 `rememberGraphicsLayer` 层在「录制分发之外」经 drawLayer 绘制会**静默不出图**——`GraphicsLayer.draw$ui_graphics_release` 发现 display list 失效时按存档 drawBlock 重录（`recreateDisplayListIfNeeded`→`recordInternal`，异常被吞），而含 `drawContent()` 的块在原分发外重放必然失败 → 空层；且嵌套 drawLayer（某层 record 块内再画另一 remember 层）恒缺内容，无论源层新鲜与否（模拟器探针 E4-E9 逐项实证：同分发直接绘制可见、跨分发/嵌套恒缺）。旧构造「玻璃 onDrawBehind 内 recordBlur 取样 contentLayer 再画」两条件全踩，模糊层恒为空——表现为栏后文字清晰、模糊度档位间零像素差，与真机症状一致
- **修复（design J：record+draw 同分发）**：悬浮栏全量磨砂的取样与绘制整体移到**内容层宿主节点**（`glassBackdropContent`）的同一次 draw 分发内——内容子树经 `drawContext.canvas` 换靶直接画进模糊层（无嵌套层），模糊层（renderEffect=BlurEffect）再以 `saveLayer`+栏圆角 clip 垫画到玻璃之下（内容节点先于 Scaffold 栏绘制，z 序天然正确）；玻璃节点零绘制。取样语义：源 = 内容子树（栏后消息穿透磨砂，对齐桌面 Mica）；流光背景为低频渐变不进采样（嵌套取样不可用；模糊前后视觉近似等价）。卡片透光磨砂维持 record/draw 同分发形态不变
- **构造加固（与根因无关）**：饱和度从模糊层拆出——blur（层 renderEffect）与 saturate（独立 saveLayer paint colorFilter）分属两个效果载体，作用顺序保持 `blur() saturate()` = web 配方（styles.css:517-518），单层单效果对齐「抽屉 graphicsLayer 单层单效果」已知可用形态；2×blur 采样余量不变式与「效果对象仅半径变化重建」护栏保持
- **运行时能力探测兜底**：新增 `GlassBlurCapabilityProbe`——HardwareRenderer+ImageReader 离屏渲染「高对比源层 → 生产同构模糊层」两帧读回像素、Laplacian 边缘能量比对（纯逻辑函数，JVM 可测 `GlassBlurCapabilityProbeTest`）；首次玻璃需要时懒触发，独立 HandlerThread 执行，结果进程内缓存；**探测期间与探测失败一律落雾化兜底**，不再只看 API 版本（ROM 关模糊/不渲染构造的设备自动降级）
- **雾化兜底加浓**：`glassFogAlpha` 线性改 √ 曲线、满档 0.85→0.92——默认磨砂档 12dp 由 0.17（几乎不可见）加浓到 ≈0.41（肉眼明显），0dp 恒全透明语义不变；设置页「玻璃」卡副标题同步（「低版本或设备探测失败以雾化近似」）
- **ModelSheet 窗口模糊**：API 31+ 增加 `WindowManager.isCrossWindowBlurEnabled` 检查，系统「关闭模糊」（开发者选项/无障碍）时显式跳过 FLAG_BLUR_BEHIND、走既有 scrim 降级（原先设 flag 被系统静默忽略）
- 钉值/测试：`GlassFogLegacyTest` 重钉（√ 曲线 0.4114/0.92 + 加浓护栏）、`GlassBlurCapabilityProbeTest` 新增（渲染/无视/异常/无对比度/尺寸不符五路径 fail-closed）；模拟器双路径回归截图见修复留痕（hue=0/hue=180 × blur 0/12/60、滚动中帧、强制雾化路径）

**路由评测基建（09-30 迭代）**：
- 评测集去泄漏改写：历史评测 query 与变体库（route_query_variants.json）重叠的条目逐条改写，改写前全集存档 `route_eval_queries.pre-v110.json`；新增 `LeakGateTest` 泄漏门禁——评测集 query × 变体库 query 做最长公共子串比对（阈值 8 字符），超阈即测试失败，杜绝「考题抄自答案」的虚高指标
- 金种子评测集：`route_eval_gold.json`（75 条，其中 60 条带非空期望文档标注、15 条为期望空召回的负例）逐条标注期望文档，作为路由评测的固定金标准
- 分桶评测脚手架：`RouteBucketMetrics`（测试源集脚手架，不动生产 RouteEvaluator）按桶宏平均 P/R/F1（空桶 n=0 记 0 防 NaN），`RoutePredictionsDumpTest` 导出基线逐条预测（`app/app/build/reports/route-eval/predictions-baseline.json`，main 1098 + gold 75 逐条 contains/variant/hybrid），供盲评与后续回归比对

**LLM 参与知识路由（决策门通过，默认开启）**：
- 新增路由目录资产 `routing-catalog.json`（41 篇知识文档的标题 + 一句话摘要清单）与 `RoutingCatalog` 解析，作为分类器的候选文档目录
- 新增 `LlmRouteClassifier`：用户输入 + 路由目录交给已配置 Provider 做意图→文档分类；降级链三级——分类器成功（含空集弃权）`route_source=llm`，分类器不可用/失败自动落回现有离线路由（HybridVariantRouter）`route_source=fallback`，开关关闭或平台未注入分类器则纯离线 `route_source=offline`（`KnowledgeEngine.kt`，双端日志字段一致）
- 双端 `knowledgeRouting` 开关（`llm`/`offline` 两档）：取值定义双端唯一来源在 shared `KnowledgeRouting`（KnowledgeEngine.kt:13-18）——决策门通过后未设置/空/非法值一律归一为 `llm`（默认开），只有显式 `offline` 才纯离线；安卓走 DataStore、桌面走 Properties 槽位，设置页「智能知识路由」开关行接入
- **三臂盲评结论：决策门通过，默认路由 `llm`**——核心桶 F1 提升达标且金种子达标，三臂盲评 B(1) > A(0)；LLM 路由转正为默认档（未设置即开），分类器不可用/失败自动落回离线路由（HybridVariantRouter）的降级链保留兜底，`offline` 档保留为用户显式关闭入口。默认切换经一致性核对后测试绿（LlmRouteClassifier 与降级链 / 双端 knowledgeRouting 开关与 route_source 埋点均全绿）。决策登记见 `docs/decisions/OPEN-DECISIONS.md`（O-路由v2，RESOLVED），错误分析见 `docs/route-error-analysis.md`（LLM 档 miss 清单 30 条：超集误召 20 / 部分命中 6 / 零召回 4，按意图类型归类与目录/摘要改进建议见该文）
- 路由参数调优（召回优先）：分类上限 3→5 篇（`MAX_ROUTES`，注入截断 `maxDocs` 同步 5——三臂盲评中"多知识注入"臂 11/12 完胜的信号，P 会降 R 上限升高）；总超时 3s→10s（`ROUTE_TIMEOUT_MS` + OkHttp callTimeout/readTimeout 同步 10s，readTimeout 不放宽会让 10s 预算形同虚设；connectTimeout 保持 3s 快速失败），最坏情况慢 provider 挂满 10s 才静默降级离线兜底；离线兜底路径仍 ≤3 篇不变

**09-30 迭代（全量代码审查与修复）**：206 个源码文件（安卓 + 共享层 + 桌面与内嵌 Web）逐目录审查，全部发现经独立复核确认后修复，修复改动再经独立 diff 验收与 CI 同款门禁（testDebugUnitTest + :desktop:test + assembleDebug + lintDebug）全绿。以下按主题列要点，完整清单（含证据与逐条处置状态：已解决 106 / 部分解决 4 / 留作建议 20）见审查报告。

**数据安全与状态一致性（高危）**：
- 清空数据/导入备份后内存 `sessionId` 悬空：`wipeAll`/`importBackup` 清库不清 `RealChatRepository.sessionId`，之后发消息复用已删会话 id，用户消息静默丢失或写进恢复出来的无关会话；清库联动复位并走 `ensureSession` 重开
- 图片预落库类失败（READ_FAILED/TOO_LARGE/COMPRESS_FAILED）后点「重试」仍按「用户消息已落库」发 `persistUser=false`，图片与配文永不写库，AI 回复成孤儿记录；retry 按错误类别区分是否需要补落库
- 新建提供商「测试连接/添加模型/保存」各插一条 provider 行（最多 3 条重复）：统一走 `ensurePersisted` 首存记忆 id、后续一律 update；新建页模型列表同步补收集器（原先 `models` 收集整体在 `if (!isNew)` 内，加了模型列表也不显示）
- 20MB 图片大小守卫后置：调用方先 `readBytes()` 全量进内存才检查上限，超大文件在守卫前就可能 OOM；守卫前置到读取路径

**安全**：
- 桌面端 `http://` 公网地址静默明文发送 API Key：`UNSUPPORTED_URL` 终检依赖 Android 网络策略抛的 cleartext 异常，桌面端无此策略恒放行；双端在请求前显式校验 scheme
- ProviderRepository 加密写路径对 `cipher.encrypt` 零兜底，Keystore 不可用时保存/测试连接直接崩溃（对称的解密路径有完整兜底）；`KeyUnavailableException` 现达 UI 提示「密钥不可用，请重新输入」
- Base URL 校验收紧（F22/F23）：空串、含逗号/空格、带 `?`/`#`（query/fragment 会吞掉硬拼的 `/chat/completions` 恒 404）一律拒绝并提示，save/addModel/testConnection 三入口统一预检
- 桌面端密钥机器指纹：非 Windows 回退读了 Windows 专有的 `COMPUTERNAME`，主机名绑定在 Linux/macOS 恒为常量 `unknown-host`，换同名用户机器即可解密；改按平台取主机名

**健壮性/正确性**：
- `UpdateChecker.check()` 的 `catch (Exception)` 与桌面 `importAllJson` 的裸 `runCatching` 吞 `CancellationException`，协程取消被转成正常失败返回；两处显式 rethrow
- 桌面 `DesktopPresetSeed` 补齐安卓 L23 修复（「存在任意 provider 即整体跳过」→ 逐预设按名判重 + 事务，首启写一半被杀不再永久丢预设）；桌面 `DesktopMetricsStore` 补齐 L26 修复（save 共用锁 + tmp+rename 原子写，崩溃不再丢整份指标）
- `recordMemoryWrite` 在 DataStore `edit{}` 事务内对同一 DataStore 再入读（违反契约的潜伏死锁），改读事务快照 `prefs`
- `mergeFacts` 同批次内不去重（模型单次输出重复事实双双入库），已保留的新事实并入后续比对；桌面 `persistFacts` 用未清洗数量 `drop` 导致每有一条空白事实就错位丢一条新记忆，双端对齐清洗后 drop
- EXIF 方向修正补全：安卓 5/7（转置）与 2/4（镜像）不修正；桌面端此前完全不处理 EXIF（竖拍图横置发给模型且重编码丢 EXIF）；桌面压缩管线顺带去掉一次等价的全量 ARGB→RGB 重绘
- 聊天 UI：切到已有长会话列表停在 index 0、isAtBottom 恒 false 导致后续消息不自动滚底（改 `scrollToItem(count-1)`）；危机卡「我知道了」传空 lambda 点了没反应（接收后收起并持久化）；预落库失败恢复逻辑随 combine 重发射重放、覆盖用户输入找回已删图片（错误态消费一次即清）；会话搜索去抖窗口内新旧结果混显（标题匹配与全文检索按同一关键词口径过滤）
- 知识库/记忆：`isSameTopic` 对无标点中文整句 token 化失效导致同题长输入误判换题；`BreatheDot` 把 delay 写进 `infiniteRepeatable` 子 tween（周期漂移错峰失效）；`GlowBackground` 用开机绝对帧时间驱动相位（长开机 float32 精度劣化光斑跳格，对齐 FluidBackground 相对化）；Mica 磨砂层与栏级投影绘制顺序颠倒（投影压暗栏内玻璃）；`KnowledgeChunker` 字符预算漏计每块 4 字符渲染头；Bm25 标点清洗正则 4 处漂移收敛为单份预编译；`PromptBuilder.buildUserReply` 末尾硬编码 `safety_override=false` 与系统层危机契约矛盾（改为按场景条件输出）；崩溃兜底 handler 内 `AppLogger.e` 加异常防护（OOM 崩溃时不再阻断 crash 落盘）；`CrashLogStore.clear` 删错目录（cacheDir→filesDir，wipeAll 联动真正删掉已下载 APK）
- 桌面 Web（app.js）：流式中点击侧栏当前会话使流令牌作废、本轮回复静默消失（同会话点击不再 bump streamSeq）；保存/新增厂商与新建档案不判 API 错误（失败仍弹成功提示、500 无反馈、新建失败 TypeError）——统一走错误分支提示
- 路由生成门禁 `gen_routes.py` 的「无文档未被覆盖」检查是永假死断言（route_docs 由同一张表构造），改为对 ROUTE_TABLE 与真实文档集合做差集

**假测试清理（名不副实/恒真断言，改为有效覆盖或删除）**：
- `RouteByInputShapeTest` 从未调用被测函数、断言的是手工复制副本 → 改为直接调用 `routeByInputShape`；`SessionDrawerGroupTest` 未断言组顺序 → 补顺序断言；`MemoryDialogsTest` 删除确认路径从未点击确认键 → 补确认路径
- 恒真/空断言修正：`Bm25ScorerTest`（IDF 降权断言里 common 恒 0）、`CrisisDetectorTest`（isNotEmpty 与词表纯文本无关）、`SseParserTest`（assertNull 无法区分「返回 null」与「返回致命块」）、`KnowledgeIndexTest`（fixture 仅 3 文档 cap-3 恒真）、`RouteEvaluatorHarnessTest`（queryCount 恒等）、`LlmClientTest` 重启顺序补后半不变量、`PromptBuilderTest` emoji 码点区间补全、`ContrastTest` 记录值 4.29→实测 5.39 并据此收紧阈值、`FluidAppearanceSettingsFlowTest` 死回流操作改为真回流、`SharedSourceSmokeTest` "round trip" 名实相符化
- 删除严格被 `ErrorMapperFullTest` 覆盖的 `ErrorMapperTest`；`MigrationTest` 中零新增覆盖的逐字重复用例删除；`migrateNoteToFactsOnce` 上限/截断用例改用互不包含数据（适配 `mergeFacts` 同批次去重）并把长段挪进截断窗口使断言真正生效

**死代码与同构精简（行为不变）**：
- 删除零引用死代码：`ChipSpacer`、`rememberCoachInnerCard` 系列常量、`SettingsRepository` 7 个一次性 suspend 读取、`AppViewModel.setFluidBackground`、`SettingsViewModel.editTarget` 状态机、`SessionDao.recent`、`Converters` List<String> 转换器、`SessionFirstMessage.lastMessageAt` 死投影、`ModelDao.observeByProvider`/`ProfileDao.observeLatest` 死链、Json 抽象的 `getJSONObject`/`getString`/`optScalarString`、`SseFormatException` 不可达 catch、ErrorCard v1.1 死分支、`LlmErrorCode.code` 死属性、`ChatRepository` 非异步流式三成员、aqua-fluid.js 死 uniform 管线、`gen_routes.py` 不可达 makedirs、`routes.json` 重复资产（与 routes-v2.json 字节级相同，11.5KB 随包白拿）
- 复制粘贴收敛为单份实现：`ChatViewModel.routeByInputShape` 抽 `InputShapeRouter`（同文件测试直测真函数）；三份输入框配色抽 `EditFieldColors`；测试侧四个同包 `FakeSettingsRepository` 合并共享、假 DAO 抽 `FakeDaos`、三份评测脚手架抽 `KnowledgeEvalCorpus`；`MemoryExtractor` 内私有 `takeCodePoints` 复用 `SessionTitle` 同名实现；`UpdateClient`/`UpdateChecker` 死错误码约定注释改为如实描述

**留作建议（20 条未动，需跨模块上提或产品决策）**：记忆导入导出约 130 行双端逐字复制、版本比较函数、Json android/jvm 包装类下沉 `jvmCommonMain`、PresetSeed 数据表下沉、引导页第 4 屏 6 个采集字段提交后被静默丢弃（需产品决策接入或删屏）等，逐条见审查报告。

**09-29 迭代**：

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
