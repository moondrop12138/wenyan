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

    override fun load(): UsageMetrics.Snapshot? {
        if (!file.exists()) return null
        return runCatching {
            usageMetricsSnapshotFromJson(Json.obj(file.readText()))
        }.getOrNull()
    }

    override fun save(snapshot: UsageMetrics.Snapshot) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(snapshot.toJson().toString())
        }
    }
}
