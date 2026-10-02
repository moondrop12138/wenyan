package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

/** 分桶宏平均指标的决策门数学正确性（口径见 [RouteBucketMetrics] 类注释） */
class RouteBucketMetricsTest {

    private val coreDocs = setOf("knowledge/07-沟通冲突与修复.md")

    @Test
    fun `bucket precedence is negative multi core longtail`() {
        assertEquals(RouteBucketMetrics.NEGATIVE, RouteBucketMetrics.bucketOf(emptyList(), coreDocs))
        // multi 优先于 core：即使主文档在 routes 覆盖集内
        assertEquals(
            RouteBucketMetrics.MULTI,
            RouteBucketMetrics.bucketOf(listOf("knowledge/07-沟通冲突与修复.md", "knowledge/15.md"), coreDocs),
        )
        assertEquals(
            RouteBucketMetrics.CORE,
            RouteBucketMetrics.bucketOf(listOf("knowledge/07-沟通冲突与修复.md"), coreDocs),
        )
        assertEquals(
            RouteBucketMetrics.LONGTAIL,
            RouteBucketMetrics.bucketOf(listOf("practical/实战话术.md"), coreDocs),
        )
    }

    @Test
    fun `prf empty-set special cases`() {
        // pred 空 → P=1；交集为空 → R=0（exp 非空时）
        assertEquals(RouteBucketMetrics.Prf(1.0, 0.0, 0.0), RouteBucketMetrics.prf(emptyList(), listOf("a", "b")))
        // exp 空 + pred 空 → P=1, R=1, F1=1
        assertEquals(RouteBucketMetrics.Prf(1.0, 1.0, 1.0), RouteBucketMetrics.prf(emptyList(), emptyList()))
        // exp 空 + pred 非空 → R=0, F1=0
        assertEquals(RouteBucketMetrics.Prf(0.0, 0.0, 0.0), RouteBucketMetrics.prf(listOf("x"), emptyList()))
        // 全错且漏检：P=0, R=0 → F1=0（P+R=0 分支）
        assertEquals(RouteBucketMetrics.Prf(0.0, 0.0, 0.0), RouteBucketMetrics.prf(listOf("x", "y"), listOf("a")))
        // 常规：P=1/2, R=1, F1=2/3
        assertEquals(RouteBucketMetrics.Prf(0.5, 1.0, 2.0 / 3.0), RouteBucketMetrics.prf(listOf("a", "x"), listOf("a")))
    }

    @Test
    fun `macro average groups by bucket and counts n`() {
        val perQuery = listOf(
            RouteBucketMetrics.NEGATIVE to RouteBucketMetrics.Prf(1.0, 1.0, 1.0),
            RouteBucketMetrics.NEGATIVE to RouteBucketMetrics.Prf(0.0, 0.0, 0.0),
            RouteBucketMetrics.CORE to RouteBucketMetrics.Prf(1.0, 1.0, 1.0),
        )
        val byBucket = RouteBucketMetrics.macroByBucket(perQuery)
        assertEquals(0.5, byBucket.getValue(RouteBucketMetrics.NEGATIVE).p, 1e-9)
        assertEquals(2, byBucket.getValue(RouteBucketMetrics.NEGATIVE).n)
        assertEquals(1.0, byBucket.getValue(RouteBucketMetrics.CORE).f1, 1e-9)
        assertEquals(1, byBucket.getValue(RouteBucketMetrics.CORE).n)
        // 空桶：n=0、指标记 0（不得 NaN）
        val empty = byBucket.getValue(RouteBucketMetrics.MULTI)
        assertEquals(0, empty.n)
        assertEquals(0.0, empty.p, 0.0)
        assertEquals(0.0, empty.f1, 0.0)
        // 桶序固定，落盘键序稳定
        assertEquals(
            listOf(RouteBucketMetrics.NEGATIVE, RouteBucketMetrics.MULTI, RouteBucketMetrics.CORE, RouteBucketMetrics.LONGTAIL),
            byBucket.keys.toList(),
        )
    }
}
