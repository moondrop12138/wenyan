package com.wenyan.app.llm
import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject

/**
 * O6: 轻量用量指标（进程内累计；仅计数/耗时，绝不含用户消息原文）。
 * LlmClient 在关键节点记录；桌面 /api/metrics 与手机设置页读快照展示。
 * 持久化通过 attachStore 注入平台存储（Android filesDir / 桌面 %APPDATA%\Wenyan），
 * 每次记录后同步落盘，重启时 load 恢复。
 */
object UsageMetrics {

    /** 平台侧持久化接缝（Android / 桌面各自实现；测试不 attach 即纯内存） */
    interface Store {
        fun load(): Snapshot?
        fun save(snapshot: Snapshot)
    }

    private val lock = Any()
    @Volatile
    private var store: Store? = null

    private var totalRequests = 0L
    private var totalInputTokens = 0L
    private var totalOutputTokens = 0L
    private var ttftSumMs = 0L
    private var ttftCount = 0L
    private val failuresByCode = LinkedHashMap<String, Long>()

    /** 启动时注入平台存储：加载既有快照并接管后续写盘 */
    fun attachStore(newStore: Store) {
        store = newStore
        newStore.load()?.let { restore(it) }
    }

    fun detachStore() {
        store = null
    }

    fun recordRequestStart(inputTokens: Int) {
        synchronized(lock) {
            totalRequests++
            totalInputTokens += inputTokens
        }
        notifyChanged()
    }

    fun recordFirstToken(ttftMs: Long) {
        synchronized(lock) {
            ttftSumMs += ttftMs
            ttftCount++
        }
        notifyChanged()
    }

    fun recordCompletion(outputTokens: Int) {
        synchronized(lock) {
            totalOutputTokens += outputTokens
        }
        notifyChanged()
    }

    fun recordFailure(code: String) {
        synchronized(lock) {
            failuresByCode[code] = (failuresByCode[code] ?: 0L) + 1L
        }
        notifyChanged()
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            totalRequests = totalRequests,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            avgTtftMs = if (ttftCount == 0L) 0L else ttftSumMs / ttftCount,
            ttftSumMs = ttftSumMs,
            ttftCount = ttftCount,
            failures = failuresByCode.toMap(),
        )
    }

    /** 供测试/重启复位（不改变 store） */
    fun reset() = synchronized(lock) {
        totalRequests = 0L
        totalInputTokens = 0L
        totalOutputTokens = 0L
        ttftSumMs = 0L
        ttftCount = 0L
        failuresByCode.clear()
    }

    fun toJson(): JsonObject = snapshot().toJson()

    private fun restore(s: Snapshot) = synchronized(lock) {
        totalRequests = s.totalRequests
        totalInputTokens = s.totalInputTokens
        totalOutputTokens = s.totalOutputTokens
        ttftSumMs = s.ttftSumMs
        ttftCount = s.ttftCount
        failuresByCode.clear()
        failuresByCode.putAll(s.failures)
    }

    private fun notifyChanged() {
        store?.save(snapshot())
    }

    data class Snapshot(
        val totalRequests: Long,
        val totalInputTokens: Long,
        val totalOutputTokens: Long,
        val avgTtftMs: Long,
        val ttftSumMs: Long = 0L,
        val ttftCount: Long = 0L,
        val failures: Map<String, Long>,
    )
}

/** O6: 快照 → JSON（失败分类作为嵌套对象） */
fun UsageMetrics.Snapshot.toJson(): JsonObject {
    val failuresObj = Json.obj()
    failures.forEach { (code, count) -> failuresObj.put(code, count) }
    return Json.obj()
        .put("totalRequests", totalRequests)
        .put("totalInputTokens", totalInputTokens)
        .put("totalOutputTokens", totalOutputTokens)
        .put("avgTtftMs", avgTtftMs)
        .put("ttftSumMs", ttftSumMs)
        .put("ttftCount", ttftCount)
        .put("failures", failuresObj)
}

/** O6: JSON → 快照（容忍缺字段/类型错误） */
fun usageMetricsSnapshotFromJson(json: JsonObject): UsageMetrics.Snapshot? = runCatching {
    val failuresObj = json.optJSONObject("failures") ?: Json.obj()
    val failures = failuresObj.keys().asSequence().associateWith { failuresObj.optLong(it, 0L) }
    UsageMetrics.Snapshot(
        totalRequests = json.optLong("totalRequests", 0L),
        totalInputTokens = json.optLong("totalInputTokens", 0L),
        totalOutputTokens = json.optLong("totalOutputTokens", 0L),
        avgTtftMs = json.optLong("avgTtftMs", 0L),
        ttftSumMs = json.optLong("ttftSumMs", 0L),
        ttftCount = json.optLong("ttftCount", 0L),
        failures = failures,
    )
}.getOrNull()
