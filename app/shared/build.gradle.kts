// O4: :shared KMP 模块（androidTarget + jvm）。
// 承载 llm/domain/prompt/knowledge/data(dB/security/image 纯逻辑) 共享代码；
// 平台接缝（AppLogger/JSON/AssetReader/Cipher/ImageCompressor/AppDatabase）通过
// 接口注入或 expect/actual 处理，逐步替换桌面 sourceSet 物理引用。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.room.runtime)
            implementation(libs.okhttp)
            implementation(libs.okhttp.sse)
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)

        jvmMain.dependencies {
            implementation(libs.json)
        }
    }
}

// Room KMP：KSP 生成双端 DAO 实现（app/desktop 的 AppDatabase 依赖共享 entity/DAO）
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

android {
    namespace = "com.wenyan.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
