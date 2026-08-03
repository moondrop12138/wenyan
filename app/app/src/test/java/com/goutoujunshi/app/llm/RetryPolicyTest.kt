package com.goutoujunshi.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 指数退避重试策略测试（llm-contract §5）
 */
class RetryPolicyTest {

    @Test
    fun `backoff grows exponentially with jitter bounds`() {
        val policy = RetryPolicy(random = Random(42))
        val d1 = policy.delayForRetry(1)
        val d2 = policy.delayForRetry(2)
        val d3 = policy.delayForRetry(3)

        // 1000 / 2000 / 4000 上下 ±20%
        assertTrue(d1 in 800L..1200L)
        assertTrue(d2 in 1600L..2400L)
        assertTrue(d3 in 3200L..4800L)
    }

    @Test
    fun `retry-after takes max with backoff`() {
        val policy = RetryPolicy(random = Random(42))
        // backoff(1) ≈ 1000，Retry-After 10s → 10000
        assertEquals(10000L, policy.delayWithRetryAfter(1, 10))
        // backoff(3) ≈ 4000，Retry-After 2s → max(4000,2000)=4000
        val d = policy.delayWithRetryAfter(3, 2)
        assertTrue(d in 3200L..4800L)
    }

    @Test
    fun `retry-after capped at 60s`() {
        val policy = RetryPolicy(random = Random(42))
        // Retry-After 120s → 封顶 60000
        assertEquals(60000L, policy.delayWithRetryAfter(1, 120))
    }

    @Test
    fun `no retry-after uses pure backoff`() {
        val policy = RetryPolicy(random = Random(42))
        assertTrue(policy.delayWithRetryAfter(1, null) in 800L..1200L)
    }

    @Test
    fun `total attempts is maxRetries plus one`() {
        val policy = RetryPolicy(maxRetries = 3)
        assertEquals(4, policy.totalAttempts)
    }

    @Test
    fun `out of range retry index returns zero`() {
        val policy = RetryPolicy(random = Random(42))
        assertEquals(0L, policy.delayForRetry(0))
        assertEquals(0L, policy.delayForRetry(4))
    }
}
