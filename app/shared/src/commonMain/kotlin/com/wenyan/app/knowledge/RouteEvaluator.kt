package com.wenyan.app.knowledge

/**
 * O7: 知识路由离线评测工具（纯 JVM）。
 * 任务书前置：先建 100–200 条真实 query 评测集，对比 contains / BM25 / BM25+精排
 * 的 precision@3 与 recall@3；recall 提升 ≥15% 才允许精排层与生产路由切换。
 * 本工具只负责按标注集计算指标；生产路由切换必须在真实评测集通过决策门后进行。
 */
object RouteEvaluator {

    data class EvalQuery(
        val query: String,
        /** 标注应命中的文档（相对路径，1-3 份） */
        val expectedDocs: List<String>,
    )

    data class EvalResult(
        val precisionAtK: Double,
        val recallAtK: Double,
        val queryCount: Int,
        val perQuery: List<QueryScore>,
    )

    data class QueryScore(
        val query: String,
        val retrieved: List<String>,
        val expected: List<String>,
        val correct: Int,
        val precision: Double,
        val recall: Double,
    )

    /** 宏平均 precision@k / recall@k（k 通常取 3） */
    fun evaluate(
        queries: List<EvalQuery>,
        router: (String) -> List<String>,
        k: Int = 3,
    ): EvalResult {
        if (queries.isEmpty()) return EvalResult(0.0, 0.0, 0, emptyList())
        val perQuery = queries.map { q ->
            val retrieved = router(q.query).take(k).distinct()
            val correct = retrieved.count { it in q.expectedDocs }
            QueryScore(
                query = q.query,
                retrieved = retrieved,
                expected = q.expectedDocs,
                correct = correct,
                precision = if (retrieved.isEmpty()) 0.0 else correct.toDouble() / retrieved.size,
                recall = if (q.expectedDocs.isEmpty()) 0.0 else correct.toDouble() / q.expectedDocs.size,
            )
        }
        return EvalResult(
            precisionAtK = perQuery.map { it.precision }.average(),
            recallAtK = perQuery.map { it.recall }.average(),
            queryCount = perQuery.size,
            perQuery = perQuery,
        )
    }

    /** 与基线对比：recall 提升是否达到决策门（默认 15%，即 +0.15） */
    fun recallLiftPasses(baseline: EvalResult, candidate: EvalResult, minLift: Double = 0.15): Boolean =
        candidate.recallAtK - baseline.recallAtK >= minLift
}
