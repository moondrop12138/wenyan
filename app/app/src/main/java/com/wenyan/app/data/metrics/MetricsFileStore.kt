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
