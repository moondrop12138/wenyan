package com.wenyan.app.llm

import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.json.Json
import java.io.File

/**
 * O6: 桌面端用量指标持久化（%APPDATA%\Wenyan\metrics.json）。
 * 与 Android 端 MetricsFileStore 同构；LlmClient 每次记录后写盘，启动时恢复。
 */
class DesktopMetricsStore : UsageMetrics.Store {

    private val file = File(AppDatabase.dbDir(), "metrics.json")

    /** L26（对齐安卓 MetricsFileStore）：load/save 共用互斥锁——UsageMetrics 多线程记录时原读写无锁竞争 */
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
                // L26 修复：writeText 非原子——写一半被杀留半截 JSON，下次启动 load 失败静默清零。
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
