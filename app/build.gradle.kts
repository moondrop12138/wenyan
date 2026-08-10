// 根构建脚本：只声明插件版本，不写任何业务逻辑
plugins {
    id("com.android.application") version "8.9.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
    // desktop 模块：纯 JVM + Ktor（版本与 kotlin.android 对齐）
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
}
