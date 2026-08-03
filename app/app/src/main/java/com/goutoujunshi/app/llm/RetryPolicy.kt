package com.goutoujunshi.app.llm

import kotlin.random.Random

/**
 * 指数退避重试策略（llm-contract §5）
 * 纯 JVM 可测。
 */
class RetryPolicy(
    val maxRetries: Int = 3,
    private val initialDelayMs: Long = 1000L,
    private val multiplier: Int = 2,
    private val jitterRatio: Double = 0.2,
    private val maxDelayMs: Long = 60_000L,
    private val random: Random = Random.Default,
) {
    val totalAttempts: Int = maxRetries + 1

    /** 第 n 次重试（1-based）前的等待毫秒数，含 ±20% 抖动 */
    fun delayForRetry(retryIndex: Int): Long {
        if (retryIndex < 1 || retryIndex > maxRetries) return 0
        val base = initialDelayMs * pow(multiplier, retryIndex - 1)
        val jitter = base * jitterRatio
        val delay = base + random.nextDouble(-jitter, jitter)
        return delay.coerceIn(0.0, maxDelayMs.toDouble()).toLong()
    }

    /**
     * 若 429 响应含 Retry-After 头，取 max(退避延迟, Retry-After)，封顶 60s
     */
    fun delayWithRetryAfter(retryIndex: Int, retryAfterSeconds: Int?): Long {
        val backoff = delayForRetry(retryIndex)
        if (retryAfterSeconds == null || retryAfterSeconds <= 0) return backoff
        val retryAfterMs = retryAfterSeconds * 1000L
        return maxOf(backoff, retryAfterMs).coerceAtMost(maxDelayMs)
    }

    private fun pow(base: Int, exp: Int): Int {
        var result = 1
        repeat(exp) { result *= base }
        return result
    }
}
