package com.wenyan.app.knowledge

/**
 * 分桶宏平均评测指标（测试源集脚手架；RouteEvaluator 本体在生产 shared/commonMain，不动）。
 *
 * 桶划分（优先级从高到低，一条 query 只进一个桶）：
 *  - negative: expectedDocs 为空
 *  - multi:    expectedDocs ≥ 2 份（优先于 core/longtail）
 *  - core:     主文档（expectedDocs 首个）在 routes-v2.json routes 覆盖的文档集内
 *  - longtail: 其余
 *
 * 单条指标（与 RouteEvaluator.evaluate 的 @k 宏平均口径不同，这里不截断、含空集特判）：
 *  P = |pred∩exp| / |pred|（pred 空 → P=1）
 *  R = |pred∩exp| / |exp|（exp 空时：pred 也空 → R=1，否则 0）
 *  F1 = 2PR/(P+R)（P+R=0 → 0）
 * 桶内对 query 宏平均。
 */
internal object RouteBucketMetrics {

    const val NEGATIVE = "negative"
    const val MULTI = "multi"
    const val CORE = "core"
    const val LONGTAIL = "longtail"

    /** 固定桶序，落盘 JSON 的键序与遍历顺序一致 */
    val allBuckets: List<String> = listOf(NEGATIVE, MULTI, CORE, LONGTAIL)

    /** 单条 P/R/F1 */
    data class Prf(val p: Double, val r: Double, val f1: Double)

    /** 桶级宏平均指标 + 桶内 query 数 */
    data class BucketStat(val p: Double, val r: Double, val f1: Double, val n: Int)

    /** 单条 P/R/F1（空集特判见类注释；入参先去重，路由器本身去重，防御性） */
    fun prf(pred: List<String>, exp: List<String>): Prf {
        val predSet = pred.toSet()
        val expSet = exp.toSet()
        val hit = predSet.count { it in expSet }.toDouble()
        val p = if (predSet.isEmpty()) 1.0 else hit / predSet.size
        val r = when {
            expSet.isEmpty() -> if (predSet.isEmpty()) 1.0 else 0.0
            else -> hit / expSet.size
        }
        val f1 = if (p + r <= 0.0) 0.0 else 2.0 * p * r / (p + r)
        return Prf(p, r, f1)
    }

    /**
     * 桶判定（优先级 negative > multi > core > longtail）。
     * [coreDocs] 为 routes-v2.json routes 覆盖的文档集（KnowledgeIndex.allDocs()）；
     * multi 先于 core 判定，故「主文档」只对单标注 query 生效。
     */
    fun bucketOf(expectedDocs: List<String>, coreDocs: Set<String>): String = when {
        expectedDocs.isEmpty() -> NEGATIVE
        expectedDocs.size >= 2 -> MULTI
        expectedDocs.first() in coreDocs -> CORE
        else -> LONGTAIL
    }

    /** 按桶宏平均：输入 (桶名, 单条 Prf)；空桶 n=0、指标记 0（避免 average() 的 NaN） */
    fun macroByBucket(perQuery: List<Pair<String, Prf>>): Map<String, BucketStat> =
        allBuckets.associateWith { bucket ->
            val items = perQuery.filter { it.first == bucket }
            if (items.isEmpty()) {
                BucketStat(p = 0.0, r = 0.0, f1 = 0.0, n = 0)
            } else {
                BucketStat(
                    p = items.map { it.second.p }.average(),
                    r = items.map { it.second.r }.average(),
                    f1 = items.map { it.second.f1 }.average(),
                    n = items.size,
                )
            }
        }
}
