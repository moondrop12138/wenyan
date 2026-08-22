package com.wenyan.app.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志本地兜底（v1.7.3 T3）：
 * - 环形内存缓冲：最近 100 条 AppLogger 事件（synchronized；事件格式含隐私红线，无用户内容）；
 * - 崩溃回调：写 filesDir/crash/last_crash.txt（时间戳 + 线程 + 堆栈 + 缓冲全文）；
 * - clear()：删 crash 目录 + cacheDir/downloads（wipeAll 隐私联动）。
 * 线程安全：崩溃线程与 AppLogger 调用线程并发，全部 synchronized。
 */
class CrashLogStore(private val context: Context) {

    companion object {
        private const val BUFFER_CAPACITY = 100
        private const val CRASH_DIR = "crash"
        private const val LAST_CRASH_FILE = "last_crash.txt"
    }

    private val buffer = ArrayDeque<String>(BUFFER_CAPACITY)
    private val lock = Any()

    /** 环形缓冲写入（AppLogger 钩子调用） */
    fun append(line: String) {
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > BUFFER_CAPACITY) buffer.removeFirst()
        }
    }

    /** 缓冲全文快照（时间正序：旧→新；L29 修复 KDoc 与实现一致——append 为 addLast 追加，崩溃落盘按发生顺序可读） */
    fun snapshot(): String = synchronized(lock) { buffer.joinToString("\n") }

    /** 崩溃回调：写 last_crash.txt（时间戳 + 线程 + 堆栈 + 缓冲全文）；返回文件/null */
    fun writeCrash(thread: Thread, throwable: Throwable): File? = runCatching {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val file = File(dir, LAST_CRASH_FILE)
        val sb = StringBuilder()
        sb.append("=== 温言崩溃日志 ===\n")
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())).append("\n")
        sb.append("thread: ").append(thread.name).append("\n")
        sb.append("exception: ").append(throwable.javaClass.name).append(": ").append(throwable.message ?: "").append("\n")
        sb.append("stack:\n")
        throwable.stackTrace?.take(40)?.forEach { sb.append("  at ").append(it.toString()).append("\n") }
        sb.append("\n--- recent log buffer (oldest first) ---\n")   // L29: 与实际顺序一致
        sb.append(snapshot())
        file.writeText(sb.toString())
        file
    }.getOrNull()

    /** 崩溃文件（无则 null） */
    fun crashFile(): File? {
        val f = File(File(context.filesDir, CRASH_DIR), LAST_CRASH_FILE)
        return if (f.exists()) f else null
    }

    /** 清除崩溃日志目录 + 下载缓存（wipeAll 隐私联动） */
    fun clear() {
        synchronized(lock) { buffer.clear() }
        runCatching { File(context.filesDir, CRASH_DIR).deleteRecursively() }
        runCatching { File(context.cacheDir, "downloads").deleteRecursively() }
    }
}
