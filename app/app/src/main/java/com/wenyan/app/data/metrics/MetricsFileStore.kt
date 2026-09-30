package com.wenyan.app.data.metrics

import com.wenyan.app.json.Json
import com.wenyan.app.llm.UsageMetrics
import com.wenyan.app.llm.toJson
import com.wenyan.app.llm.usageMetricsSnapshotFromJson
import java.io.File

/**
 * O6: Android 端用量指标持久化（filesDir/metrics.json）。
 * 每次 LlmClient 记录指标后同步写盘；文件极小，不用 DataStore 以保持同步/简单。
 */
class MetricsFileStore(private val file: File) : UsageMetrics.Store {

    /** L26: load/save 共用互斥锁（UsageMetrics 单例在多协程线程记录，原读写无锁竞争） */
    private val lock = Any()

    override fun load(): UsageMetrics.Snapshot? {
        synchronized(lock) {
            if (!file.exists()) return null
            return runCatching {
                usageMetricsSnapshotFromJson(Json.obj(file.readText()))
            }.getOrNull()
        }
    }

    override fun save(snapshot: UsageMetrics.Snapshot) {
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                // L26 修复：writeText 非原子——写一半被杀留半截 JSON，下次启动 load 永远失败。
                // 改「写 .tmp → rename」原子替换。
                // F19 修复：删掉 rename 前的 file.delete()——Android(Linux) 上 File.renameTo
                // 走 rename(2)，目标存在时也原子替换；多出来的 delete 反而制造「旧文件已删、
                // 新文件未落」的窗口，进程在该窗口被杀会丢失整个指标文件（历史计数归零）
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(snapshot.toJson().toString())
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                }
            }
        }
    }
}
