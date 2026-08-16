package com.wenyan.app.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O7 重方案评测：LLM 生成的 query 变体库路由 vs contains / BM25 / 画像精排。
 */
class QueryVariantRouterTest {

    private val userDir = System.getProperty("user.dir") ?: "."
    private val roots = listOf(File(userDir), File(userDir, "app"))

    @Test
    fun `evaluate query variant routing`() {
        val routesFile = roots.map { File(it, "src/main/assets/knowledge/routes.json") }.firstOrNull { it.exists() }
            ?: error("routes.json not found")
        val queriesFile = roots.map { File(it, "src/test/resources/route_eval_queries.json") }.firstOrNull { it.exists() }
            ?: error("route_eval_queries.json not found")
        val variantsFile = roots.map { File(it, "src/test/resources/route_query_variants.json") }.firstOrNull { it.exists() }
            ?: error("route_query_variants.json not found")

        val index = KnowledgeIndex(routesFile.readText(Charsets.UTF_8))
        val docTexts = mutableMapOf<String, String>()
        for (doc in index.allDocs()) {
            val f = File(routesFile.parentFile, doc)
            if (f.exists()) docTexts[doc] = f.readText(Charsets.UTF_8)
        }
        val profiles = DocProfile.build(index, docTexts)

        val variantsRoot = JSONObject(variantsFile.readText(Charsets.UTF_8))
        val variants = variantsRoot.keys().asSequence().associateWith { key ->
            val arr = variantsRoot.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }

        val root = JSONArray(queriesFile.readText(Charsets.UTF_8))
        val queries = (0 until root.length()).map { i ->
            val obj = root.getJSONObject(i)
            val expected = (0 until obj.optJSONArray("expectedDocs").length()).map { j ->
                obj.optJSONArray("expectedDocs").getString(j)
            }
            RouteEvaluator.EvalQuery(obj.getString("query"), expected)
        }

        val variantRouter = QueryVariantRouter(variants)
        val hybridRouter = HybridVariantRouter(index, variants)
        val reranker = RouteReranker(index, docTexts, profiles)

        val containsRes = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Res = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerRes = RouteEvaluator.evaluate(queries, router = { q -> reranker.rank(q) })
        val variantRes = RouteEvaluator.evaluate(queries, router = { q -> variantRouter.route(q) })
        val hybridRes = RouteEvaluator.evaluate(queries, router = { q ->
            val base = index.route(q).toMutableList()
            if (base.size < 3) {
                variantRouter.route(q, topK = 5).forEach { d ->
                    if (d !in base && base.size < 3) base.add(d)
                }
            }
            base
        })
        val hybridFillEmptyRes = RouteEvaluator.evaluate(queries, router = { q -> hybridRouter.route(q) })
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
        println("hybrid : P=${hybridRes.precisionAtK} R=${hybridRes.recallAtK} F1=${f1(hybridRes)}")
        println("hybrid vs contains F1 diff=${f1(hybridRes) - f1(containsRes)}")
        println("hybrid vs variant F1 diff=${f1(hybridRes) - f1(variantRes)}")
        println("hybridFillEmpty: P=${hybridFillEmptyRes.precisionAtK} R=${hybridFillEmptyRes.recallAtK} F1=${f1(hybridFillEmptyRes)}")
        println("hybridFillOne  : P=${hybridFillOneRes.precisionAtK} R=${hybridFillOneRes.recallAtK} F1=${f1(hybridFillOneRes)}")
        assertTrue("hybridFillEmpty should beat contains on F1", f1(hybridFillEmptyRes) > f1(containsRes))
    }

    private fun f1(r: RouteEvaluator.EvalResult): Double {
        val sum = r.precisionAtK + r.recallAtK
        return if (sum <= 0.0) 0.0 else 2.0 * r.precisionAtK * r.recallAtK / sum
    }
}
