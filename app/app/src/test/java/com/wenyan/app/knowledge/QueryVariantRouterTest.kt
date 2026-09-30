package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O7 重方案评测：LLM 生成的 query 变体库路由 vs contains / BM25 / 画像精排。
 * F68/F28 修复：脚手架改用共享 [KnowledgeEvalCorpus]（原为第四份逐字拷贝；且本测试原先读的
 * routes.json 是与 routes-v2.json 字节级相同的冻结副本——副本已删除，统一读生产使用的 v2，
 * 消除「改 v2 忘了 v1」的双份静默分叉）。
 */
class QueryVariantRouterTest {

    @Test
    fun `evaluate query variant routing`() {
        val index = KnowledgeEvalCorpus.loadIndex()
        val docTexts = KnowledgeEvalCorpus.loadDocTexts(index)
        val profiles = DocProfile.build(index, docTexts)
        val variants = KnowledgeEvalCorpus.loadVariants()
        val queries = KnowledgeEvalCorpus.loadQueries()

        val allFileKeys = KnowledgeEvalCorpus.loadRouteFileKeys()
        assertEquals("query 变体库应覆盖全部 41 份文档", allFileKeys.size, variants.size)
        assertTrue("query 变体库缺少文档", allFileKeys.all { variants.containsKey(it) })
        assertTrue("每份文档应至少有 20 条变体", variants.values.all { it.size >= 20 })

        val variantRouter = QueryVariantRouter(variants)
        val hybridRouter = HybridVariantRouter(index, variants)
        val reranker = RouteReranker(index, docTexts, profiles)

        val containsRes = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Res = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerRes = RouteEvaluator.evaluate(queries, router = { q -> reranker.rank(q) })
        val variantRes = RouteEvaluator.evaluate(queries, router = { q -> variantRouter.route(q) })
        val hybridRouterRes = RouteEvaluator.evaluate(queries, router = { q -> hybridRouter.route(q) })
        val hybridFillEmptyRes = RouteEvaluator.evaluate(queries, router = { q ->
            val base = index.route(q).toMutableList()
            if (base.isEmpty()) {
                variantRouter.route(q, topK = 3).forEach { d ->
                    if (d !in base && base.size < 3) base.add(d)
                }
            }
            base
        })
        val hybridFillOneRes = RouteEvaluator.evaluate(queries, router = { q ->
            val base = index.route(q).toMutableList()
            if (base.size < 2) {
                variantRouter.route(q, topK = 3).forEach { d ->
                    if (d !in base && base.size < 2) base.add(d)
                }
            }
            base
        })

        println("=== O7 重方案：query 变体路由 ===")
        println("queries=${queries.size} variants=${variants.values.sumOf { it.size }}")
        println("contains : P=${containsRes.precisionAtK} R=${containsRes.recallAtK} F1=${f1(containsRes)}")
        println("bm25    : P=${bm25Res.precisionAtK} R=${bm25Res.recallAtK} F1=${f1(bm25Res)}")
        println("reranker: P=${rerankerRes.precisionAtK} R=${rerankerRes.recallAtK} F1=${f1(rerankerRes)}")
        println("variant : P=${variantRes.precisionAtK} R=${variantRes.recallAtK} F1=${f1(variantRes)}")
        println("variant vs contains F1 diff=${f1(variantRes) - f1(containsRes)}")
        println("variant vs reranker F1 diff=${f1(variantRes) - f1(rerankerRes)}")
        println("hybridRouter(fillOne): P=${hybridRouterRes.precisionAtK} R=${hybridRouterRes.recallAtK} F1=${f1(hybridRouterRes)}")
        println("hybrid vs contains F1 diff=${f1(hybridRouterRes) - f1(containsRes)}")
        println("hybrid vs variant F1 diff=${f1(hybridRouterRes) - f1(variantRes)}")
        println("hybridFillEmpty: P=${hybridFillEmptyRes.precisionAtK} R=${hybridFillEmptyRes.recallAtK} F1=${f1(hybridFillEmptyRes)}")
        println("hybridFillOne  : P=${hybridFillOneRes.precisionAtK} R=${hybridFillOneRes.recallAtK} F1=${f1(hybridFillOneRes)}")
        assertTrue("hybridRouter(fillOne) should beat contains on F1", f1(hybridRouterRes) > f1(containsRes))
    }

    private fun f1(r: RouteEvaluator.EvalResult): Double = KnowledgeEvalCorpus.f1(r)
}
