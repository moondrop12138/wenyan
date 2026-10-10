# 温言 · 恋爱决策支持 App

> 先接住情绪，再分清事实，最后给出能执行的下一步。

「温言」是一款纯本地运行的恋爱决策支持应用，支持 **Android** 与 **Windows 桌面**：以开源项目 [goutoujunshi（狗头军师）](https://github.com/powerycy/goutoujunshi) 的知识库为内核，用**四段式回答结构**（接住你 → 先分清事实 → 军师建议 → 现在可以做什么）帮你梳理关系、拆解事实与推测、给出可执行的话术与行动。

它不是虚拟恋人，也不替你读心——它帮你把"凭感觉"变成"看证据"。

> 📥 **下载**：两端独立发布、互不影响——Android 见 [v 系列 Release](https://github.com/moondrop12138/wenyan/releases) 装 `.apk`；Windows 桌面版见 [desktop-v 系列 Release](https://github.com/moondrop12138/wenyan/releases?q=desktop)，提供：
> - `*-windows.exe`：安装包，双击安装到 `AppData\Local\温言`，写卸载项并创建快捷方式。
> - `*-windows.zip`：绿色版/便携版，解压到任意位置即可运行 `温言.exe`，不写注册表，删文件夹即卸载。
>
> 两者均内嵌裁剪 JRE，无需单独装 Java。两端数据各自本地存储，互不同步。

> ⚠️ **非商业使用声明**：本仓库知识库部分衍生自 [powerycy/goutoujunshi](https://github.com/powerycy/goutoujunshi)（PolyForm Noncommercial 1.0.0），因此**本仓库整体采用非商业许可**，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

## 平台

| | Android | Windows 桌面 |
|---|---|---|
| 形态 | 原生 App（Jetpack Compose） | 本地服务 + 网页前端（exe 安装包，内嵌裁剪 JRE） |
| 界面 | Compose 液态玻璃 | 纯静态 HTML/CSS/JS 液态玻璃 |
| 数据 | Room SQLite，本机 | Room KMP SQLite，本机 `%APPDATA%\Wenyan` |
| Key 加密 | Android Keystore + AES-GCM | 机器指纹派生 AES-256-GCM |
| 业务逻辑 | 两端共享 `:shared` KMP 模块（llm / domain / prompt / knowledge / data 纯逻辑） | 同左 |

两端功能完全对齐：四段式回答、完全重答、跨会话记忆档案、记忆导出/合并导入、多模型 BYOK、测连接、截图分析、冷启动会话恢复、逐段渐显回答、用量诊断、深浅色主题、数据导出/清空、检查更新。

## 功能

- **四段式回答**：所有输入（短句提问 / 粘贴聊天记录 / 截图分析）统一输出「接住你 → 先分清事实（已知·推测·未知）→ 军师建议（含稳健/会撩/强势三风格话术，本地切换）→ 现在可以做什么」结构卡
- **完全重答**（双端，v1.9.5）：AI 气泡常驻「重新生成」纯图标按钮，原位更新回答，消息 id 与顺序不变；失败/取消不落库
- **回答逐段渐显 · 不打扰滚动**（双端，v1.9.6）：新回答按段依次淡入上浮（总时长 0.9–2.6s 钳制），历史会话/切换会话/刷新直出，任何交互立即全量呈现；发送与回答渲染不再自动滚动视口，仅进入会话时一次性定位最新消息；系统「移除动画」与危机卡直接全量
- **跨会话记忆**：自动提炼对话中关于咨询对象的关键事实并长期记住（可手动编辑、可一键关闭），后续回答基于已记住信息保持一致、不重复、不编造
- **多记忆档案**：为不同咨询对象建立独立记忆档案，设置页可新增/改名/删除（删除需二次确认），历史会话标注所属档案，多个对象互不混淆
- **记忆单条管理**：档案内事实逐条查看/修正/删除（档案详情页可编辑 MBTI、吸引力、关系状态、关键事件）
- **记忆依据溯源**：回答引用记忆时标注「记忆依据」，引用透明可核对
- **记忆导出 / 合并导入**（双端，v1.9.4）：记忆档案与事实可导出为 JSON 备份（换机迁移用），导入为合并语义、绝不删除现有数据
- **会话分组**：历史会话按记忆档案分组浏览，切换对象不乱
- **冷启动会话恢复**（双端，v1.9.4）：重开应用自动回到上次会话
- **新用户引导问卷**（Android，v1.9.5）：四屏问卷建立初始画像（本人 MBTI/评分 → 对象 → 关系 → 目标），可跳过；桌面端为一页式欢迎引导
- **离线/弱网预检**（Android，v1.9.5）：无网络时发送被拦下、输入原样保留，联网后点重试即可
- **应用内更新检查**：自动检测 GitHub Releases 新版本并下载安装（带进度条）；崩溃日志本地落盘，Android 设置页有「上次崩溃」关怀卡（查看/导出/清除）与一键导出诊断
- **截图分析**：主模型多模态直读；非多模态模型自动走"视觉转述"通道（可编辑确认后再分析）
- **多图发送**：一次最多选 10 张，单次 LLM 请求全量分析
- **知识库路由**：41 份关系科学与实用沟通文档（心理/法律/沟通/婚姻/安全）打包进 App，按场景自动注入至多 5 篇、结果回显引用来源；默认 LLM 智能路由（10 秒超时 + 文档白名单校验，失败自动回退离线 BM25 + 问法变体库混合路由，设置页可关），评测经去泄漏改写与 LeakGate 门禁把关后转正
- **危机转介**：检测到家暴/跟踪/自伤等风险时，先给安全计划与紧急服务，不给恋爱话术
- **自带 Key 直连**：无后端、数据不出本机，支持任意 OpenAI 兼容服务商；API Key 本机加密存储（Android 走 Keystore，桌面走机器指纹派生 AES-256-GCM）
- **流式输出**：SSE 增量回复，思考过程可折叠，流式期间只预览成品话术而非原始 JSON
- **用量 / 诊断**（双端，v1.9.5）：请求次数、输入/输出 token、平均首字延迟、失败分类统计；Android 另含知识路由诊断区（调用次数、来源分布、最近一次路由详情）
- **隐私设计**：本地档案可一键清除；备份（云备份/换机迁移）全部排除
- **液态玻璃 UI**：全 App 统一玻璃材质（半透明填充 + 高光 + 描边 + 柔和投影）+ 暖色光斑背景，浅色/深色双主题，支持"移除动画"无障碍设置；Android 端铺 AGSL 流体「流光背景」（色相/亮度/磨砂度可调，卡片真实高斯模糊 + 能力探测状态行，弹层独立窗口同色相跟随，低版本自动降级简化光斑）；桌面端为 WebGL2 移植的沉浸式流体背景——顶栏/输入栏为悬浮磨砂玻璃卡，消息滚动时从栏底穿过并磨砂，进入设置页背景立即模糊，支持流体配色、壁纸自定义与 Mica/兼容模式切换

## 架构

```
app/
├── shared/                   # 两端共享 KMP 模块（commonMain：llm/domain/prompt/knowledge/data 纯逻辑）
├── app/src/main/java/com/wenyan/app/      # 手机版（Android 平台接缝 + UI）
│   ├── MainActivity.kt        # 入口：edge-to-edge + 主题装配
│   ├── WenyanApp.kt           # Application：依赖容器装配
│   ├── ui/                    # Compose UI（chat / settings / onboarding / navigation）
│   ├── container/             # 仓库实现与 UI 映射
├── app/src/main/assets/knowledge/   # 41 份知识文档 + 路由表/路由目录/变体库（构建门禁生成）
│
├── desktop/                   # Windows 桌面版（纯 JVM 模块）
│   ├── src/main/kotlin/com/wenyan/desktop/
│   │   ├── Main.kt            # 入口：Ktor CIO 127.0.0.1 + 端口探测 + 自动开浏览器
│   │   ├── ApiRoutes.kt       # REST 路由 + 手动 SSE 流式聊天端点
│   │   ├── ChatEngine.kt      # 聊天引擎（复用共享链路，去 Android UI 接缝）
│   │   └── WenyanService.kt   # 数据服务层（Room KMP + 机器指纹加密）
│   ├── src/main/resources/static/   # 网页前端（原生 HTML/CSS/JS 液态玻璃）
│   └── packaging/             # jpackage 打包脚本（jdeps→jlink→app-image→exe）
│
├── scripts/gen_routes.py      # 构建门禁：知识库完整性校验 + 路由表生成
└── docs/                      # 架构 / 数据库 / LLM 契约 / 设计令牌 / ADR
```

桌面版与手机版共同依赖 `:shared` KMP 模块（commonMain：`llm / domain / prompt / knowledge / data` 纯逻辑），平台差异通过接口注入 / expect-actual 处理（加密、数据库驱动、图片压缩、知识资产读取、日志 sink），保证两端行为一致、单点维护。

设计细节见 [docs/architecture.md](docs/architecture.md)（已同步至 v1.9.6 双端架构）；[docs/llm-contract.md](docs/llm-contract.md)、[docs/db-schema.md](docs/db-schema.md) 为较早版本快照，以代码为准。

## 构建

### Android

环境要求：JDK 17、Android SDK（compileSdk 36 / minSdk 26）。

```bash
cd app

# 单元测试（构建门禁会先校验知识库完整性）
./gradlew testDebugUnitTest

# Debug 构建
./gradlew assembleDebug

# Release 构建（需要本地 keystore.properties 配置 release 签名；CI 不构建 release）
./gradlew assembleRelease
```

> 工程根在 `app/` 子目录（git 仓库根为上级目录），CI 已在 `.github/workflows/ci.yml` 中配置好构建路径。

### Windows 桌面

环境要求：JDK 21（打包用 jpackage，WiX 3.x 由脚本自动获取）。

```bash
cd app

# 本地跑（Ktor 起 127.0.0.1，自动开浏览器）
./gradlew :desktop:run

# 打包 exe 安装包 + 绿色版（产物在 desktop/dist-package/）
powershell -File desktop/packaging/package.ps1
```

## 技术栈

**共享业务层**：Kotlin / Room（KMP）/ OkHttp + SSE 流式、指数退避重试 / AES-GCM（API Key 加密）

**Android**：Jetpack Compose（Material 3）/ Kotlin 2.1 / DataStore / Android Keystore；minSdk 26 / targetSdk 36，R8 混淆

**Windows 桌面**：Ktor CIO（本地服务）/ 纯静态 HTML/CSS/JS 前端（原生 JS 无框架）/ jpackage（jdeps + jlink 裁剪 JRE + exe 安装包）

## 测试

单元测试覆盖：四段 JSON 解析（新旧双 schema）、流式预览提取、输入路由、知识库索引/检索/LLM 路由评测（含 LeakGate 去泄漏门禁）、危机检测、错误码映射、重试策略、SSE 解析、逐段渐显计划（RevealController 37 用例）、对比度断言、玻璃 token、加密、Room 仓库等，共 60+ 测试类 / 600+ 用例；构建门禁另含知识库完整性校验（文档数/路由目录一致性，违规即构建失败）。

```bash
cd app && ./gradlew testReleaseUnitTest
```

## 联系与反馈

欢迎任何 Bug 报告、功能建议或想法交流，请发送邮件至 **hyf136696647672021@126.com**。

## 知识库来源与致谢

知识库（`app/src/main/assets/knowledge/`）的 41 份文档衍生自开源项目 [goutoujunshi · 狗头军师](https://github.com/powerycy/goutoujunshi)（Copyright 2026 powerycy，[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0)），其设计原则沿用：

1. **先接住人，再解决事**——情绪没有被看见时，最正确的建议也可能无法执行
2. **行为比标签可靠**——不凭 MBTI、性别或一次聊天记录替对方读心
3. **互惠比追到更重要**——减少内耗、保留尊严与未来选择权同样是成功
4. **策略必须说明代价**——可以讨论表达包装，但同时交代适用条件与长期成本
5. **同意和退出权不可绕过**——明确拒绝不是需要破解的障碍
6. **危险情境先保安全**——暴力、胁迫、跟踪、诈骗和自伤风险不能用普通恋爱话术处理

## 免责声明

本项目提供关系教育与决策支持，**不替代**心理治疗、医疗诊断、律师意见、警方或紧急服务。遇到家暴、跟踪、自伤等紧急情况，请优先联系当地紧急服务。
