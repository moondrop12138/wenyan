// 温言桌面版：本地 Ktor 后端 + 静态网页前端（纯 JVM 模块，与 Android :app 同仓共存）
//
// 复用策略：通过 sourceSet 直接引用 :app 模块的纯 JVM 业务源码，物理共享同一份代码，
// Android 迭代自动同步到桌面版。数据层走 Room KMP（entity/DAO/Migration 原样共享），
// 仅 Android 特有文件（Context/Keystore/assets/ImageCompressor/AppDatabase.get）被 exclude，
// 由 desktop 侧提供替代实现。

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    application
}

// WorkBuddy safe-delete 会锁定 build 产物：buildDir 时间戳化绕开（与 :app 同策略）
val buildRunStamp = System.currentTimeMillis()
layout.buildDirectory.set(file("build.$buildRunStamp"))

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.wenyan.desktop.MainKt")
}

// 共享 Android 业务源码根
val appSharedSrc = rootProject.file("app/src/main/java/com/wenyan/app")

sourceSets {
    main {
        kotlin {
            // 业务逻辑层（llm/domain/prompt 零 android import）
            srcDir("$appSharedSrc/llm")
            srcDir("$appSharedSrc/domain")
            srcDir("$appSharedSrc/prompt")
            srcDir("$appSharedSrc/knowledge")
            // 数据层：Room KMP 共享 entity/DAO/Converters（AppDatabase/PresetSeed 由 desktop 重写）
            srcDir("$appSharedSrc/data/db")
            // 安全层：AesGcmCipher（纯 JCE）共享；KeystoreAesGcmCipher 由 desktop 同名重写
            srcDir("$appSharedSrc/data/security")
            // 图片规格契约（纯数据类）；ImageCompressor 由 desktop ImageIO 重写
            srcDir("$appSharedSrc/data/image")

            // ---- 排除 Android 特有文件（desktop 侧提供同名/替代实现）----
            exclude("**/AndroidKnowledgeAssetReader.kt")   // android.content.Context 读 assets
            exclude("**/KeystoreAesGcmCipher.kt")          // AndroidKeyStore → desktop 机器指纹 provider
            exclude("**/ImageCompressor.kt")               // android.graphics.Bitmap → ImageIO
            exclude("**/AppDatabase.kt")                   // Context builder → desktop BundledSQLiteDriver
            exclude("**/PresetSeed.kt")                    // 依赖 AppDatabase（desktop 同名重写，seed 逻辑复用）
            // 注：data/repository 与 ui/contract 不共享——那是 Android UI 层接缝（耦合
            // Keystore 具体类 / UpdateChecker / android.net.Uri），desktop 后端直接面向
            // DAO + LlmClient 暴露 HTTP API，不经过 UI 契约层。
        }
    }
}

// 知识库资源：打包时从 :app 的 assets 复制进 jar（routes.json + 40 份文档）
tasks.processResources {
    from(rootProject.file("app/src/main/assets/knowledge")) {
        into("knowledge")
    }
}

// Room schema 导出（desktop 侧独立导出，与 Android schemas 交叉验证）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Ktor Server（CIO 引擎：纯 Kotlin，无 native 依赖，适合 jpackage）
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-cio-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")
    implementation("io.ktor:ktor-server-host-common-jvm:2.3.12")  // staticResources

    // Room KMP（2.7.x 起官方支持 JVM desktop；entity/DAO/Migration 代码共享）
    // 注：room-ktx 是 Android-only，KMP 版 Flow/协程支持已并入 room-runtime，不再单独引
    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    // Room KMP desktop 需要 SQLite 驱动（bundled 模式：自带 native sqlite，无需系统安装）
    implementation("androidx.sqlite:sqlite-bundled:2.5.2")

    // LLM 调用（与 Android 同版本）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // JSON（与 ChatRequestBuilder/AnalysisParser 一致，手写 org.json）
    implementation("org.json:json:20240303")

    // 图片解码增强（TwelveMonkeys ImageIO 插件，SPI 自动注册，DesktopImageCompressor 零改动）：
    // - imageio-jpeg：更健壮的 JPEG 读写（覆盖 CMYK/渐进式等原生 reader 啃不动的变体）
    // - imageio-webp + webp-imageio(gotson, 自带 native libwebp)：补 WebP（截图/微信存图常见格式）
    // 注：AVIF/HEIC 无可靠纯 Java 解码方案，维持不支持（上传报「无法识别的图片格式」）
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.12.0")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.github.gotson:webp-imageio:0.2.2")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // 日志
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // 测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnit()
}
