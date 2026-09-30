package com.wenyan.app.knowledge

import org.junit.Test

/**
 * O7: 路由评测运行器（使用公开素材采集的真实 query 集）。
 * 对比 contains 基线与纯 BM25 的 precision@3 / recall@3。
 * F68/F69 修复：脚手架改用共享 [KnowledgeEvalCorpus]；删除两处恒真断言
 * （assertEquals(queries.size, *.queryCount)——RouteEvaluator 内部 perQuery = queries.map{...}、
 * queryCount = perQuery.size 结构恒等，任何输入都为真），保留「流水线可执行 + 指标打印」
 * 的冒烟价值与 MISS 清单输出。
 */
class RouteEvaluatorHarnessTest {

    @Test
    fun `evaluate contains vs bm25 on real query set`() {
        val index = KnowledgeEvalCorpus.loadIndex()
        val docTexts = KnowledgeEvalCorpus.loadDocTexts(index)
        val queries = KnowledgeEvalCorpus.loadQueries()

        val containsResult = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Result = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })

        println("=== O7 route eval (${queries.size} queries) ===")
        println("contains: precision@3=${containsResult.precisionAtK}, recall@3=${containsResult.recallAtK}")
        println("bm25    : precision@3=${bm25Result.precisionAtK}, recall@3=${bm25Result.recallAtK}")
        println("recall lift = ${bm25Result.recallAtK - containsResult.recallAtK}")
        println("precision delta = ${bm25Result.precisionAtK - containsResult.precisionAtK}")
        bm25Result.perQuery.filter { it.recall < 1.0 }.take(25).forEach {
            println("MISS: ${it.query} expected=${it.expected} retrieved=${it.retrieved}")
        }
    }
}
