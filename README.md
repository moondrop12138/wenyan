# 温言 · 恋爱决策支持 App

> 先接住情绪，再分清事实，最后给出能执行的下一步。

「温言」是一款纯本地运行的恋爱决策支持 Android 应用：以开源项目 [goutoujunshi（狗头军师）](https://github.com/powerycy/goutoujunshi) 的知识库为内核，用**四段式回答结构**（接住你 → 先分清事实 → 军师建议 → 现在可以做什么）帮你梳理关系、拆解事实与推测、给出可执行的话术与行动。

它不是虚拟恋人，也不替你读心——它帮你把"凭感觉"变成"看证据"。

> ⚠️ **非商业使用声明**：本仓库知识库部分衍生自 [powerycy/goutoujunshi](https://github.com/powerycy/goutoujunshi)（PolyForm Noncommercial 1.0.0），因此**本仓库整体采用非商业许可**，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

## 功能

- **四段式回答**：所有输入（短句提问 / 粘贴聊天记录 / 截图分析）统一输出「接住你 → 先分清事实（已知·推测·未知）→ 军师建议（含稳健/会撩/强势三风格话术，本地切换）→ 现在可以做什么」结构卡
- **跨会话记忆**：自动提炼对话中关于咨询对象的关键事实并长期记住（可手动编辑、可一键关闭），后续回答基于已记住信息保持一致、不重复、不编造
- **多记忆档案**：为不同咨询对象建立独立记忆档案，设置页可新增/改名/删除（删除需二次确认），历史会话标注所属档案，多个对象互不混淆
- **记忆单条管理**：档案内事实逐条查看/修正/删除（档案详情页可编辑 MBTI、吸引力、关系状态、关键事件）
- **记忆依据溯源**：回答引用记忆时标注「记忆依据」，引用透明可核对
- **会话分组**：历史会话按记忆档案分组浏览，切换对象不乱
- **应用内更新检查**：自动检测 GitHub Releases 新版本并下载安装；崩溃日志本地落盘，设置页可一键导出诊断
- **截图分析**：主模型多模态直读；非多模态模型自动走"视觉转述"通道（可编辑确认后再分析）
- **多图发送**：一次最多选 10 张，单次 LLM 请求全量分析
- **知识库路由**：40 份关系科学与实用沟通文档（心理/法律/沟通/婚姻/安全）打包进 App，按场景自动加载 1–3 份，结果回显引用来源
- **危机转介**：检测到家暴/跟踪/自伤等风险时，先给安全计划与紧急服务，不给恋爱话术
- **自带 Key 直连**：无后端、数据不出手机，支持任意 OpenAI 兼容服务商；API Key 经 Android Keystore + AES-GCM 加密存储
- **流式输出**：SSE 增量回复，思考过程可折叠，流式期间只预览成品话术而非原始 JSON
- **隐私设计**：本地档案可一键清除；备份（云备份/换机迁移）全部排除
- **液态玻璃 UI**：全 App 统一玻璃材质（半透明填充 + 高光 + 描边 + 柔和投影）+ 暖色光斑背景，浅色/深色双主题，支持"移除动画"无障碍设置

## 架构

```
app/
├── app/src/main/java/com/wenyan/app/
│   ├── MainActivity.kt        # 入口：edge-to-edge + 主题装配
│   ├── WenyanApp.kt           # Application：依赖容器装配
│   ├── ui/                    # Compose UI（chat / settings / onboarding / navigation）
│   ├── llm/                   # LLM 客户端（OkHttp + SSE）、解析器、错误归一、重试策略
│   ├── domain/                # 会话状态机、输入路由
│   ├── knowledge/             # 知识检索与危机检测
│   ├── data/                  # Room / DataStore / 图片压缩 / Keystore 加密
│   ├── prompt/                # Prompt 构建
│   └── container/             # 仓库实现与 UI 映射
├── app/src/main/assets/knowledge/   # 40 份知识文档 + 路由表（构建门禁生成）
├── scripts/gen_routes.py      # 构建门禁：知识库完整性校验 + 路由表生成
└── docs/                      # 架构 / 数据库 / LLM 契约 / 设计令牌 / ADR
```

设计细节见 [docs/architecture.md](docs/architecture.md)、[docs/llm-contract.md](docs/llm-contract.md)、[docs/db-schema.md](docs/db-schema.md)。

## 构建

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

## 技术栈

- Jetpack Compose（Material 3）/ Kotlin 2.1 / Room / DataStore
- OkHttp + SSE 流式、指数退避重试
- Android Keystore + AES-GCM（API Key 加密）
- minSdk 26 / targetSdk 36，R8 混淆

## 测试

单元测试覆盖：四段 JSON 解析（新旧双 schema）、流式预览提取、输入路由、知识库索引/检索/危机检测、错误码映射、重试策略、SSE 解析、对比度断言、玻璃 token、加密、Room 仓库等。

```bash
cd app && ./gradlew testReleaseUnitTest
```

## 联系与反馈

欢迎任何 Bug 报告、功能建议或想法交流，请发送邮件至 **2508266762@qq.com**。

## 知识库来源与致谢

知识库（`app/src/main/assets/knowledge/`）的 40 份文档衍生自开源项目 [goutoujunshi · 狗头军师](https://github.com/powerycy/goutoujunshi)（Copyright 2026 powerycy，[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0)），其设计原则沿用：

1. **先接住人，再解决事**——情绪没有被看见时，最正确的建议也可能无法执行
2. **行为比标签可靠**——不凭 MBTI、性别或一次聊天记录替对方读心
3. **互惠比追到更重要**——减少内耗、保留尊严与未来选择权同样是成功
4. **策略必须说明代价**——可以讨论表达包装，但同时交代适用条件与长期成本
5. **同意和退出权不可绕过**——明确拒绝不是需要破解的障碍
6. **危险情境先保安全**——暴力、胁迫、跟踪、诈骗和自伤风险不能用普通恋爱话术处理

## 免责声明

本项目提供关系教育与决策支持，**不替代**心理治疗、医疗诊断、律师意见、警方或紧急服务。遇到家暴、跟踪、自伤等紧急情况，请优先联系当地紧急服务。
