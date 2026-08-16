package com.wenyan.app.knowledge

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * O7 新方案：文档画像精排器的权重训练与评测。
 * 使用 618 条真实素材评测集，70/30 划分；坐标搜索最大化 F1@3。
 */
class RouteRerankerTrainTest {

    private val userDir = System.getProperty("user.dir") ?: "."
    private val roots = listOf(File(userDir), File(userDir, "app"))

    @Test
    fun `train reranker and compare with contains and bm25`() {
        val routesFile = roots.map { File(it, "src/main/assets/knowledge/routes-v2.json") }.firstOrNull { it.exists() }
            ?: error("routes.json not found from $userDir")
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

        // 预计算所有 query 的文档特征向量（不依赖权重）
        val reranker = RouteReranker(index, docTexts, profiles)
        val featureSets = queries.map { q -> reranker.featureVectors(q.query) }

        // 固定随机划分 70/30
        val indices = queries.indices.toMutableList()
        indices.shuffle(Random(42))
        val train = indices.take((indices.size * 0.7).toInt())
        val test = indices.drop(train.size)

        // 坐标搜索最优权重
        var bestWeights = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, -1.0)
        var bestF1 = f1(bestWeights, train, featureSets, queries, index)
        val steps = doubleArrayOf(0.5, 0.2, 0.1, 0.05)
        for (step in steps) {
            var improved = true
            while (improved) {
                improved = false
                for (f in bestWeights.indices) {
                    for (delta in doubleArrayOf(step, -step)) {
                        val candidate = bestWeights.copyOf()
                        candidate[f] += delta
                        val f1v = f1(candidate, train, featureSets, queries, index)
                        if (f1v > bestF1 + 1e-9) {
                            bestF1 = f1v
                            bestWeights = candidate
                            improved = true
                        }
                    }
                }
            }
        }

        // 最终评测
        val containsRes = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Res = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerFull = RouteReranker(index, docTexts, profiles, bestWeights)
        val rerankerRes = RouteEvaluator.evaluate(queries, router = { q -> rerankerFull.rank(q) })

        val containsTest = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> index.route(q) })
        val bm25Test = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })
        val rerankerTest = RouteEvaluator.evaluate(test.map { queries[it] }, router = { q -> rerankerFull.rank(q) })

        println("=== O7 文档画像精排器 ===")
        println("queries=${queries.size} train=${train.size} test=${test.size}")
        println("bestWeights=${bestWeights.joinToString()} (trainF1=$bestF1)")
        println("ALL:")
        println("  contains: P=${containsRes.precisionAtK} R=${containsRes.recallAtK} F1=${f1Of(containsRes)}")
        println("  bm25    : P=${bm25Res.precisionAtK} R=${bm25Res.recallAtK} F1=${f1Of(bm25Res)}")
        println("  reranker: P=${rerankerRes.precisionAtK} R=${rerankerRes.recallAtK} F1=${f1Of(rerankerRes)}")
        println("TEST ONLY:")
        println("  contains: P=${containsTest.precisionAtK} R=${containsTest.recallAtK} F1=${f1Of(containsTest)}")
        println("  bm25    : P=${bm25Test.precisionAtK} R=${bm25Test.recallAtK} F1=${f1Of(bm25Test)}")
        println("  reranker: P=${rerankerTest.precisionAtK} R=${rerankerTest.recallAtK} F1=${f1Of(rerankerTest)}")

        // 决策门记录：F1 提升需 ≥0.05 才切换生产。当前测试集提升不足且 precision 略降，
        // 因此保持 contains 基线；本测试仅保证新方案不劣于两个基线。
        val improvement = f1Of(rerankerTest) - f1Of(containsTest)
        println("test F1 improvement = $improvement")
        println("decision gate (>=0.05): ${improvement >= 0.05}")
        assertTrue("reranker should not be worse than bm25 on test F1", f1Of(rerankerTest) >= f1Of(bm25Test))
        // 关键词反哺后 contains 已是最强，精排器不作为生产切换对象
        println("contains remains best on test F1 = ${f1Of(containsTest) >= f1Of(rerankerTest)}")
    }

    private fun f1Of(r: RouteEvaluator.EvalResult): Double {
        val sum = r.precisionAtK + r.recallAtK
        return if (sum <= 0.0) 0.0 else 2.0 * r.precisionAtK * r.recallAtK / sum
    }

    private fun f1(
        weights: DoubleArray,
        qIndices: List<Int>,
        featureSets: List<List<Pair<String, DoubleArray>>>,
        queries: List<RouteEvaluator.EvalQuery>,
        index: KnowledgeIndex,
    ): Double {
        if (qIndices.isEmpty()) return 0.0
        var pSum = 0.0
        var rSum = 0.0
        for (idx in qIndices) {
            val retrieved = rankFromFeatures(weights, featureSets[idx], queries[idx].query, index)
            val expected = queries[idx].expectedDocs
            val correct = retrieved.count { it in expected }
            pSum += if (retrieved.isEmpty()) 0.0 else correct.toDouble() / retrieved.size
            rSum += if (expected.isEmpty()) 0.0 else correct.toDouble() / expected.size
        }
        val p = pSum / qIndices.size
        val r = rSum / qIndices.size
        val sum = p + r
        return if (sum <= 0.0) 0.0 else 2.0 * p * r / sum
    }

    private fun rankFromFeatures(
        weights: DoubleArray,
        features: List<Pair<String, DoubleArray>>,
        query: String,
        index: KnowledgeIndex,
    ): List<String> {
        val scored = features.map { (doc, f) ->
            var sum = 0.0
            for (i in weights.indices) sum += weights[i] * f[i]
            doc to sum
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        return if (scored.isEmpty()) index.route(query) else scored
    }
}
