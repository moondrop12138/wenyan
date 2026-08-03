package com.goutoujunshi.app.log

import android.util.Log

/**
 * 轻量结构化日志（可观测性埋点，tag 统一 "Goutoujunshi"）。
 *
 * 事件格式：`event key=value key=value ...`。
 * 隐私红线：只记录事件与元数据（计数/耗时/错误码/命中关键词），
 * 禁止传入用户内容明文（聊天文本/档案正文/API Key）。
 */
object AppLogger {
    const val TAG = "Goutoujunshi"

    fun d(event: String, vararg kv: Pair<String, Any?>) = Log.d(TAG, format(event, kv))
    fun i(event: String, vararg kv: Pair<String, Any?>) = Log.i(TAG, format(event, kv))
    fun w(event: String, vararg kv: Pair<String, Any?>) = Log.w(TAG, format(event, kv))
    fun e(event: String, vararg kv: Pair<String, Any?>) = Log.e(TAG, format(event, kv))

    /** 异常日志：只记录异常类型与堆栈（标准崩溃信息，不含用户内容） */
    fun e(event: String, t: Throwable, vararg kv: Pair<String, Any?>) {
        Log.e(TAG, format(event, kv), t)
    }

    private fun format(event: String, kv: Array<out Pair<String, Any?>>): String {
        if (kv.isEmpty()) return event
        return buildString {
            append(event)
            kv.forEach { (k, v) -> append(' ').append(k).append('=').append(v ?: "null") }
        }
    }
}
