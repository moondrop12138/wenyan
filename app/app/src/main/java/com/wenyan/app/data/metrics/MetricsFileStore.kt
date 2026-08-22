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
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(snapshot.toJson().toString())
                if (file.exists()) file.delete()
                if (!tmp.renameTo(file)) {
                    tmp.delete()
                }
            }
        }
    }
}
