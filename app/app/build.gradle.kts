import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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

    defaultConfig {
        applicationId = "com.wenyan.app"
        minSdk = 26
        targetSdk = 36
        // 版本历史见根目录 CHANGELOG.md（L11 精简，不再在 build 脚本堆注释墙）
        versionCode = 40
        versionName = "1.9.3"

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

    // androidTest assets 指向 Room schema 导出目录（MigrationTestHelper 读取 1.json~6.json）
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Activity Compose (Photo Picker)
    implementation(libs.activity.compose)
    // M12: EXIF 方向读取（竖拍照片旋转修正）
    implementation(libs.exifinterface)
    // O8: Baseline Profile（启动/首帧优化，profileinstaller 读取 bundled baseline-prof.txt）
    implementation(libs.profileinstaller)

    // O4: 共享业务逻辑（llm/domain/prompt/knowledge/data 纯逻辑，KMP commonMain）
    implementation(project(":shared"))

    // Room（M1：双端统一 2.7.2）
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // OkHttp + SSE
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // Kotlin coroutines
    implementation(libs.kotlinx.coroutines.android)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // v1.7.3 T1：Room 迁移自动化测试（MigrationTestHelper，与 room 主版本一致）
    androidTestImplementation(libs.room.testing)
}

// 知识库完整性校验 + 路由表生成（AC-17 构建门禁）
tasks.register<Exec>("knowledgeCheck") {
    workingDir = rootProject.projectDir
    // L12: python 缺失/是 Windows Store stub 时回退 python3；都缺失则清晰报错
    commandLine(findPython(), "scripts/gen_routes.py")
    // gen_routes.py 校验 41 份文档齐全、生成 routes-v2.json；缺失/不匹配即非零退出
}
tasks.named("preBuild") {
    dependsOn("knowledgeCheck")
}

fun findPython(): String =
    listOf("python", "python3").firstOrNull { cmd ->
        try {
            val p = ProcessBuilder(cmd, "--version").redirectErrorStream(true).start()
            p.waitFor()
            p.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    } ?: throw GradleException("knowledgeCheck 需要 python 或 python3，请先安装（Windows 可用 winget install Python.Python.3.12）")
