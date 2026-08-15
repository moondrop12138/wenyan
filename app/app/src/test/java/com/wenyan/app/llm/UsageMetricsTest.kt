package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/** O6: 用量指标累计测试 */
class UsageMetricsTest {

    @Test
    fun `records requests and tokens`() {
        UsageMetrics.reset()
        UsageMetrics.recordRequestStart(100)
        UsageMetrics.recordRequestStart(200)
        UsageMetrics.recordCompletion(50)
        UsageMetrics.recordCompletion(30)
        val s = UsageMetrics.snapshot()
        assertEquals(2L, s.totalRequests)
        assertEquals(300L, s.totalInputTokens)
        assertEquals(80L, s.totalOutputTokens)
    }

    @Test
    fun `records ttft average`() {
        UsageMetrics.reset()
        UsageMetrics.recordFirstToken(100)
        UsageMetrics.recordFirstToken(300)
        assertEquals(200L, UsageMetrics.snapshot().avgTtftMs)
    }

    @Test
    fun `records failures by code`() {
        UsageMetrics.reset()
        UsageMetrics.recordFailure("RATE_LIMITED")
        UsageMetrics.recordFailure("RATE_LIMITED")
        UsageMetrics.recordFailure("SERVER_ERROR")
        val s = UsageMetrics.snapshot()
        assertEquals(2L, s.failures["RATE_LIMITED"])
        assertEquals(1L, s.failures["SERVER_ERROR"])
    }
}
