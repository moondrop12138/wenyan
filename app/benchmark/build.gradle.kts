// O8: Baseline Profile 生成模块（com.android.test，macro benchmark）。
// 本地不跑真机/模拟器；CI 的 baseline-profile job 用 Gradle Managed Device 生成
// 并产出 src/main/baseline-prof.txt（或 managed device 任务输出），随 APK 发布。
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    // 生成 <GMD 设备名>BaselineProfile 任务（com.android.test 只出 BenchmarkAndroidTest 任务，
    // 没有它 workflow 里的 :benchmark:pixel6Api34BaselineProfile 会 TaskSelectionException）
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.wenyan.app.benchmark"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34").apply {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.benchmark.macro)
    implementation(libs.androidx.test.junit)
    implementation(libs.uiautomator)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// baselineprofile 插件默认只认 connected device，GMD 必须显式登记
baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}
