import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// release 签名配置：keystore.properties 不入 git（见根 .gitignore），内容由运维在本地生成。
// 缺失/不可用时回退 debug 签名并打警告，保证 CI 与无密钥 clone 可正常构建。
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProps.isNotEmpty() &&
    file(keystoreProps.getProperty("storeFile", "keystore/release.jks")).exists()

android {
    namespace = "com.wenyan.app"
    compileSdk = 36

    // WorkBuddy safe-delete 会锁定 app/build 产物，导致重复构建时新旧混杂/manifest 冲突。
    // 每次构建把 buildDir 指到带时间戳的新目录，彻底绕开被锁旧目录；旧目录不再使用、可定期清理。
    val buildRunStamp = System.currentTimeMillis()
    buildDir = file("$buildDir.$buildRunStamp")

    defaultConfig {
        applicationId = "com.wenyan.app"
        minSdk = 26
        targetSdk = 36
        // 改名「温言」换包名（等于新 App），版本随包号升 1.2.0；v1.2.1 AI 会话标题 + UI 修复；v1.2.2 图标居中与清晰度
        // v1.3.0 图片气泡去框融合 + 预设模型名单全面更新（7 厂商）+ 默认模型可取消 + "看图"→"视觉" + 新图标（去水印去方框）
        // v1.3.1 图片预览+待发送图文同发 + 输入框圆角/全屏输入 + 50 句示例 + 主模型标签精简 + prompt 代词宽容 + freetext 话术卡融合
        // v1.3.2 freetext 话术卡段中引导词修复 + 失败重试不重复发 + 息屏/退后台回答继续（应用级流式）
        // v1.4.0 UI 重设计「墨绿×宣纸」：全量换色板（松绿主色/宣纸白底/墨黑深色）+ 标题字重 600→500 + 卡片圆角 xl 24→18 + 启动图标背景换墨绿
        // v1.5.0 布局级重构「Arc/Things 温暖质感」：顶栏加温言标题+模型状态点 / 空状态改日期+箴言+2×2示例卡+双引导卡(8%底色图标容器) /
        //  输入栏悬浮胶囊(r28+内凹输入框+松绿发送键) / AI分析卡卡片头(温言标记+时间)+投影 / 话术便签化 ThinkingPanel /
        //  抽屉 312 宽+会话预览 / 模型弹层图标容器+选中对勾 / 设置图标容器化 / 标签云选中态实底
        // v1.6.0 回答结构改造：全部输入统一四段结构（接住你→先分清事实→军师建议→现在可以做什么）schema v2 /
        //  三风格话术(稳健/会撩/强势)一次输出本地切换 / 删 ResponseMode.FREETEXT 发送路径 / 状态机全模式常开 /
        //  老五步法 JSON 兼容映射进 CoachCard / 暖色收敛到军师建议段（接住你 pill 改陶土棕系）
        // v1.6.1 配色与启动图标对齐：墨绿×宣纸 → 陶土棕×暖米白（浅）/ 暖黑×杏棕（深），accent 取自图标气泡色
        // v1.6.1+ 多图发送（最多 10 张，一次 LLM 请求全量直读）+ 启动图标整稿（去水印原稿 66% 前景/白底/主题图标）
        // v1.6.1+ 长按菜单"选择文字"：部分选取复制（SelectionContainer 可选中模式，点空白/滚动退出）
        // v1.6.2 "部分选择"完善：点菜单立即全选出选区（readOnly TextField 替代 SelectionContainer，微信式拖手柄）/
        //  模型回复（分析卡）也可部分选择（四段拼接文本，含默认风格话术）/ 菜单项去图标并改名"部分选择"
        // v1.6.3 沉浸式手势小白条：enableEdgeToEdge 双 scrim 全透明（去导航栏白条）/ 系统栏外观跟随 App 主题三态 /
        //  themes.xml 导航栏透明 + values-night 镜像防冷启动白闪 / API 26-28 navBar 用背景色顶替
        // v1.7.0 液态玻璃 UI 全量玻璃化：Glass.kt tokens + LiquidGlass 自包含玻璃绘制 + GlowBackground 光斑层 +
        //  全组件改造（顶栏/输入胶囊/气泡/CoachCard/抽屉/弹层/设置/模型管理/引导页）+ 玻璃对比度断言
        // v1.7.1 迭代打磨：根 Box 加主题背景（防浅色模式透出系统深色 windowBackground 变暗底）/
        //  抽屉面板改回实底（半透明影响阅读）/ 玻璃厚度层（上微光+下微影）与高光加宽增强质感 /
        //  光斑调柔（浅色浓度下调 + 三段衰减）/ 气泡与卡片内容内边距加大（文字不再贴框边）
        // v1.7.1 二改：空状态示例卡/引导卡/模型行内容 fillMaxSize——固定高度玻璃容器内文字垂直居中（此前贴顶）
        // v1.7.1 二改续：顶栏改悬浮胶囊（与输入栏同款 r28 strong 玻璃 + 软投影，聊天页/设置页/模型编辑页统一）
        // v1.7.1-4：顶栏/输入栏沉浸式（普通玻璃透出光斑）/ 模型弹层与侧栏液态玻璃+真高斯模糊（API31+ RenderEffect/window blur）/
        //  新建会话按钮玻璃化 / 主模型行与视觉模型行等高 / 主页面返回两次确认退出
        // v1.7.1-5：侧栏模糊提升至整个背景层（含顶栏/输入栏）+ 半径渐强动画（0→18f 250ms）/
        //  模型弹层加 Activity decorView RenderEffect 兜底模糊（窗口 blur 部分 ROM 不生效）+ 关闭清理
        // v1.7.1-6：修复弹层 decorView 模糊失效——dialog window context 是 ContextThemeWrapper，
        //  as? Activity 必为 null → 改沿 ContextWrapper 链 findActivity
        // v1.7.1-7：弹层模糊渐入动画（decorView RenderEffect 半径 0→24f 250ms）/
        //  移除窗口级 FLAG_BLUR_BEHIND（避免与 decorView 双重模糊）
        // v1.7.1-8：弹层退出渐弱动画（Animatable 双向 24→0f 200ms，currentValue 驱动）/
        //  空状态文案排版升级（箴言两行断句+字距、温柔引导语、锁图标隐私行）
        // v1.7.1-9：空状态去箴言去隐私说明，重排布局（日期→引导语→大留白→示例卡→引导卡，功能导向）
        // v1.7.1-10：弹层模糊改 targetValue 驱动——与弹层动画同步（跟手），消除迟滞感
        versionCode = 25
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 打包前校验知识库完整性 + 生成路由表（见 scripts/gen_routes.py）
        // 通过 gradle task 注册，见下方 knowledgeCheck 任务
    }

    signingConfigs {
        // 项目内 debug keystore：绕开 ~/.android（safe-delete 环境会锁 debug.keystore.lock）。
        // 仅当文件存在时覆盖 debug 签名；CI / 无密钥 clone 自动回退 AGP 默认 debug 签名（首次构建自动生成）
        if (file("debug.keystore").exists()) {
            getByName("debug") {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        // release 签名：仅本地 keystore.properties 存在时注册，避免空配置报错
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("未找到 keystore.properties/release.jks，release 构建回退 debug 签名（正式发布前必须配置 release 签名）")
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // 强制 toolchain 用 JDK17：本机 PATH 上的 JRE21（无 javac）会被误探测导致 release 编译失败
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    buildFeatures {
        compose = true
        // 版本号注入 BuildConfig，设置页动态读取（避免硬编码漂移）
        buildConfig = true
    }
    // Room schema 导出到 app/schemas/（db-schema §4：作为迁移对照基线）
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose (Photo Picker)
    implementation("androidx.activity:activity-compose:1.9.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OkHttp + SSE
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// 知识库完整性校验 + 路由表生成（AC-17 构建门禁）
tasks.register<Exec>("knowledgeCheck") {
    workingDir = rootProject.projectDir
    commandLine("python", "scripts/gen_routes.py")
    // gen_routes.py 校验 40 份文档齐全、生成 routes.json；缺失/不匹配即非零退出
}
tasks.named("preBuild") {
    dependsOn("knowledgeCheck")
}
