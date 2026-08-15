package com.wenyan.app.log

/**
 * O4: 轻量日志门面（commonMain 无平台依赖）。
 * 隐私红线：只记录事件与元数据，禁止传入用户内容明文。
 * 平台在启动时注入 sink：Android → Log + CrashLogStore；桌面 → stdout。
 */
object AppLogger {
    const val TAG = "Wenyan"

    /** level ∈ DEBUG/INFO/WARN/ERROR；line 为结构化事件串（不含用户内容） */
    @Volatile
    var sink: ((level: String, line: String) -> Unit)? = null

    fun d(event: String, vararg kv: Pair<String, Any?>) = log("DEBUG", event, kv)
    fun i(event: String, vararg kv: Pair<String, Any?>) = log("INFO", event, kv)
    fun w(event: String, vararg kv: Pair<String, Any?>) = log("WARN", event, kv)
    fun e(event: String, vararg kv: Pair<String, Any?>) = log("ERROR", event, kv)

    fun e(event: String, t: Throwable, vararg kv: Pair<String, Any?>) {
        val line = format(event, kv) + " | " + t.javaClass.simpleName + ": " + (t.message ?: "")
        sink?.invoke("ERROR", line)
    }

    private fun log(level: String, event: String, kv: Array<out Pair<String, Any?>>) {
        sink?.invoke(level, format(event, kv))
    }

    private fun format(event: String, kv: Array<out Pair<String, Any?>>): String {
        if (kv.isEmpty()) return event
        return buildString {
            append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v ?: "null") }
        }
    }
}
