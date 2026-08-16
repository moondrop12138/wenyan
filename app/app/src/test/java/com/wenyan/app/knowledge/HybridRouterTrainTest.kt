package com.wenyan.app.knowledge

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * O7 新方案二：Hybrid 路由（contains 优先 + 画像精排补漏 + 否定过滤 + 阈值）。
 * 使用 618 条真实素材评测集，70/30 划分；固定使用画像精排器已有权重，仅搜索补漏阈值。
 */
class HybridRouterTrainTest {

    private val userDir = System.getProperty("user.dir") ?: "."
    private val roots = listOf(File(userDir), File(userDir, "app"))
    private val rerankerWeights = doubleArrayOf(1.5, 1.0, 1.0, 2.0, 0.5, -1.0)

    @Test
    fun `train hybrid threshold and compare`() {
        val routesFile = roots.map { File(it, "src/main/assets/knowledge/routes-v2.json") }.firstOrNull { it.exists() }
            ?: error("routes-v2.json not found from $userDir")
        val queriesFile = roots.map { File(it, "src/test/resources/route_eval_queries.json") }.firstOrNull { it.exists() }
            ?: error("route_eval_queries.json not found from $userDir")

        val index = KnowledgeIndex(routesFile.readText(Charsets.UTF_8))
        val docTexts = mutableMapOf<String, String>()
        for (doc in index.allDocs()) {
            val f = File(routesFile.parentFile, doc)
            if (f.exists()) docTexts[doc] = f.readText(Charsets.UTF_8)
        }
        val profiles = DocProfile.build(index, docTexts)

        val root = JSONArray(queriesFile.readText(Charsets.UTF_8))
        val queries = (0 until root.length()).map { i ->
            val obj = root.getJSONObject(i)
            val expected = (0 until obj.optJSONArray("expectedDocs").length()).map { j ->
                obj.optJSONArray("expectedDocs").getString(j)
            }
            RouteEvaluator.EvalQuery(obj.getString("query"), expected)
        }

        val reranker = RouteReranker(index, docTexts, profiles, rerankerWeights)
        val indices = queries.indices.toMutableList()
        indices.shuffle(Random(42))
        val train = indices.take((indices.size * 0.7).toInt())
        val test = indices.drop(train.size)

        // 在训练集上搜索补漏阈值
        val thresholds = doubleArrayOf(0.0, 0.05, 0.1, 0.15, 0.2, 0.3, 0.4, 0.5)
        var bestThreshold = 0.0
        var bestF1 = Double.NEGATIVE_INFINITY
        for (t in thresholds) {
            val router = HybridRouter(index, reranker, t)
            val r = RouteEvaluator.evaluate(train.map { queries[it] }, router = { q -> router.route(q) })
            val f1 = f1Of(r)
            if (f1 > bestF1) {
                bestF1 = f1
                bestThreshold = t
            }
        }

        val hybrid = HybridRouter(index, reranker, bestThreshold)

        val containsRes = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Res = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerRes = RouteEvaluator.evaluate(queries, router = { q -> reranker.rank(q) })
        val hybridRes = RouteEvaluator.evaluate(queries, router = { q -> hybrid.route(q) })

        val containsTest = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> index.route(q) })
        val bm25Test = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerTest = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> reranker.rank(q) })
        val hybridTest = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> hybrid.route(q) })

        println("=== O7 Hybrid 路由 ===")
        println("queries=${queries.size} train=${train.size} test=${test.size}")
        println("bestThreshold=$bestThreshold (trainF1=$bestF1)")
        println("ALL:")
        println("  contains: P=${containsRes.precisionAtK} R=${containsRes.recallAtK} F1=${f1Of(containsRes)}")
        println("  bm25    : P=${bm25Res.precisionAtK} R=${bm25Res.recallAtK} F1=${f1Of(bm25Res)}")
        println("  reranker: P=${rerankerRes.precisionAtK} R=${rerankerRes.recallAtK} F1=${f1Of(rerankerRes)}")
        println("  hybrid  : P=${hybridRes.precisionAtK} R=${hybridRes.recallAtK} F1=${f1Of(hybridRes)}")
        println("TEST ONLY:")
        println("  contains: P=${containsTest.precisionAtK} R=${containsTest.recallAtK} F1=${f1Of(containsTest)}")
        println("  bm25    : P=${bm25Test.precisionAtK} R=${bm25Test.recallAtK} F1=${f1Of(bm25Test)}")
        println("  reranker: P=${rerankerTest.precisionAtK} R=${rerankerTest.recallAtK} F1=${f1Of(rerankerTest)}")
        println("  hybrid  : P=${hybridTest.precisionAtK} R=${hybridTest.recallAtK} F1=${f1Of(hybridTest)}")

        val improvement = f1Of(hybridTest) - f1Of(containsTest)
        println("test F1 improvement vs contains = $improvement")
        // 结论：关键词反哺后 contains 已显著强于 BM25/精排/Hybrid，因此 Hybrid 不切生产。
        assertTrue("backfilled contains should remain best on test F1",
            f1Of(containsTest) >= f1Of(hybridTest))
        assertTrue("backfilled contains should remain best vs reranker on test F1",
            f1Of(containsTest) >= f1Of(rerankerTest))
    }

    private fun f1Of(r: RouteEvaluator.EvalResult): Double {
        val sum = r.precisionAtK + r.recallAtK
        return if (sum <= 0.0) 0.0 else 2.0 * r.precisionAtK * r.recallAtK / sum
    }
}
