package com.wenyan.app.log

/**
 * AppLogger 的桌面（JVM）实现：与 Android 版同名同包同 API，编译期替换 android.util.Log 版本。
 *
 * desktop 模块不引用 Android 侧 log/ 目录，改引用本实现（输出到 stdout/slf4j）。
 * 隐私红线与 Android 版一致：只记录事件与元数据，禁止用户内容明文。
 */
object AppLogger {
    const val TAG = "Wenyan"

    /** 桌面端暂不接崩溃日志缓冲（CrashLogStore 为 Android 实现），保留字段兼容共享代码 */
    @Volatile
    var crashStore: Any? = null

    fun d(event: String, vararg kv: Pair<String, Any?>) = log("DEBUG", event, kv)
    fun i(event: String, vararg kv: Pair<String, Any?>) = log("INFO", event, kv)
    fun w(event: String, vararg kv: Pair<String, Any?>) = log("WARN", event, kv)
    fun e(event: String, vararg kv: Pair<String, Any?>) = log("ERROR", event, kv)

    fun e(event: String, t: Throwable, vararg kv: Pair<String, Any?>) {
        System.err.println("[$TAG/ERROR] ${format(event, kv)} | ${t.javaClass.simpleName}: ${t.message}")
    }

    private fun log(level: String, event: String, kv: Array<out Pair<String, Any?>>) {
        println("[$TAG/$level] ${format(event, kv)}")
    }

    private fun format(event: String, kv: Array<out Pair<String, Any?>>): String {
        if (kv.isEmpty()) return event
        return buildString {
            append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v ?: "null") }
        }
    }
}
