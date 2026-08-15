package com.wenyan.app.llm

/**
 * O6: 轻量用量指标（进程内累计；仅计数/耗时，绝不含用户消息原文）。
 * LlmClient 在关键节点记录；桌面 /api/metrics 与手机设置页读快照展示。
 */
object UsageMetrics {

    private val lock = Any()
    private var totalRequests = 0L
    private var totalInputTokens = 0L
    private var totalOutputTokens = 0L
    private var ttftSumMs = 0L
    private var ttftCount = 0L
    private val failuresByCode = LinkedHashMap<String, Long>()

    fun recordRequestStart(inputTokens: Int) = synchronized(lock) {
        totalRequests++
        totalInputTokens += inputTokens
    }

    fun recordFirstToken(ttftMs: Long) = synchronized(lock) {
        ttftSumMs += ttftMs
        ttftCount++
    }

    fun recordCompletion(outputTokens: Int) = synchronized(lock) {
        totalOutputTokens += outputTokens
    }

    fun recordFailure(code: String) = synchronized(lock) {
        failuresByCode[code] = (failuresByCode[code] ?: 0L) + 1L
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            totalRequests = totalRequests,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            avgTtftMs = if (ttftCount == 0L) 0L else ttftSumMs / ttftCount,
            failures = failuresByCode.toMap(),
        )
    }

    /** 供测试/重启复位 */
    fun reset() = synchronized(lock) {
        totalRequests = 0L
        totalInputTokens = 0L
        totalOutputTokens = 0L
        ttftSumMs = 0L
        ttftCount = 0L
        failuresByCode.clear()
    }

    fun toJson(): org.json.JSONObject = snapshot().let { s ->
        org.json.JSONObject()
            .put("totalRequests", s.totalRequests)
            .put("totalInputTokens", s.totalInputTokens)
            .put("totalOutputTokens", s.totalOutputTokens)
            .put("avgTtftMs", s.avgTtftMs)
            .put("failures", org.json.JSONObject(s.failures))
    }

    data class Snapshot(
        val totalRequests: Long,
        val totalInputTokens: Long,
        val totalOutputTokens: Long,
        val avgTtftMs: Long,
        val failures: Map<String, Long>,
    )
}
