package com.wenyan.app.log

import android.util.Log

/**
 * 轻量结构化日志（可观测性埋点，tag 统一 "Wenyan"）。
 *
 * 事件格式：`event key=value key=value ...`。
 * 隐私红线：只记录事件与元数据（计数/耗时/错误码/命中关键词），
 * 禁止传入用户内容明文（聊天文本/档案正文/API Key）。
 *
 * v1.7.3：可选注入 CrashLogStore 环形缓冲（d/i/w/e 同步进缓冲，崩溃时落盘 last_crash.txt）。
 */
object AppLogger {
    const val TAG = "Wenyan"

    /** v1.7.3 崩溃日志缓冲钩子（由 WenyanApp 注入；未注入时日志仅 Logcat） */
    @Volatile
    var crashStore: CrashLogStore? = null

    fun d(event: String, vararg kv: Pair<String, Any?>) = log(Log.DEBUG, event, kv)
    fun i(event: String, vararg kv: Pair<String, Any?>) = log(Log.INFO, event, kv)
    fun w(event: String, vararg kv: Pair<String, Any?>) = log(Log.WARN, event, kv)
    fun e(event: String, vararg kv: Pair<String, Any?>) = log(Log.ERROR, event, kv)

    /** 异常日志：只记录异常类型与堆栈（标准崩溃信息，不含用户内容） */
    fun e(event: String, t: Throwable, vararg kv: Pair<String, Any?>) {
        val line = format(event, kv)
        Log.e(TAG, line, t)
        crashStore?.append("$line | ${t.javaClass.simpleName}: ${t.message}")
    }

    private fun log(level: Int, event: String, kv: Array<out Pair<String, Any?>>) {
        val line = format(event, kv)
        when (level) {
            Log.DEBUG -> Log.d(TAG, line)
            Log.INFO -> Log.i(TAG, line)
            Log.WARN -> Log.w(TAG, line)
            else -> Log.e(TAG, line)
        }
        crashStore?.append(line)
    }

    private fun format(event: String, kv: Array<out Pair<String, Any?>>): String {
        if (kv.isEmpty()) return event
        return buildString {
            append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v ?: "null") }
        }
    }
}
