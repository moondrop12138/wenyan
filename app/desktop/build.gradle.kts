// 温言桌面版：本地 Ktor 后端 + 静态网页前端（纯 JVM 模块，与 Android :app 同仓共存）
//
// 复用策略：通过 sourceSet 直接引用 :app 模块的纯 JVM 业务源码，物理共享同一份代码，
// Android 迭代自动同步到桌面版。数据层走 Room KMP（entity/DAO/Migration 原样共享），
// 仅 Android 特有文件（Context/Keystore/assets/ImageCompressor/AppDatabase.get）被 exclude，
// 由 desktop 侧提供替代实现。

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    application
}

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

// O4: 共享业务逻辑改由 :shared KMP 模块提供（commonMain）。
// 桌面端只保留平台接缝实现：DesktopAppDatabase/DesktopPresetSeed/DesktopKnowledgeAssetReader/
// DesktopImageCompressor/KeystoreAesGcmCipher（PBKDF2 派生）与 AppLogger 的 sink 接线。

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
    // O4: 共享业务逻辑（llm/domain/prompt/knowledge/data 纯逻辑）
    implementation(project(":shared"))

    // Ktor Server（CIO 引擎：纯 Kotlin，无 native 依赖，适合 jpackage）
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.host.common)  // staticResources
    implementation(libs.ktor.server.status.pages)  // L18: 客户端错误统一 400/404（原一律 500）

    // Room KMP（2.7.x 起官方支持 JVM desktop；entity/DAO/Migration 代码共享）
    // 注：room-ktx 是 Android-only，KMP 版 Flow/协程支持已并入 room-runtime，不再单独引
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    // Room KMP desktop 需要 SQLite 驱动（bundled 模式：自带 native sqlite，无需系统安装）
    implementation(libs.sqlite.bundled)

    // LLM 调用（与 Android 同版本）
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // JSON（与 ChatRequestBuilder/AnalysisParser 一致，手写 org.json）
    implementation(libs.json)

    // 图片解码增强（TwelveMonkeys ImageIO 插件，SPI 自动注册，DesktopImageCompressor 零改动）：
    // - imageio-jpeg：更健壮的 JPEG 读写（覆盖 CMYK/渐进式等原生 reader 啃不动的变体）
    // - imageio-webp + webp-imageio(gotson, 自带 native libwebp)：补 WebP（截图/微信存图常见格式）
    // 注：AVIF/HEIC 无可靠纯 Java 解码方案，维持不支持（上传报「无法识别的图片格式」）
    implementation(libs.twelvemonkeys.jpeg)
    implementation(libs.twelvemonkeys.webp)
    implementation(libs.webp.imageio)

    // 协程
    implementation(libs.kotlinx.coroutines.core)

    // 日志
    implementation(libs.slf4j.simple)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}

tasks.test {
    useJUnit()
}
