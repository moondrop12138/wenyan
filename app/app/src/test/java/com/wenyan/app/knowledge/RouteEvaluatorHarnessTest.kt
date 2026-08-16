package com.wenyan.app.knowledge

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * O7: 路由评测运行器（使用公开素材采集的真实 query 集）。
 * 对比 contains 基线与纯 BM25 的 precision@3 / recall@3。
 */
class RouteEvaluatorHarnessTest {

    @Test
    fun `evaluate contains vs bm25 on real query set`() {
        val userDir = System.getProperty("user.dir") ?: "."
        val roots = listOf(File(userDir), File(userDir, "app"))
        val routesFile = roots.map { File(it, "src/main/assets/knowledge/routes-v2.json") }.firstOrNull { it.exists() }
            ?: error("routes.json not found from $userDir")
        val routesJson = routesFile.readText(Charsets.UTF_8)
        val index = KnowledgeIndex(routesJson)

        val docTexts = mutableMapOf<String, String>()
        for (doc in index.allDocs()) {
            val f = File(routesFile.parentFile, doc)
            if (f.exists()) docTexts[doc] = f.readText(Charsets.UTF_8)
        }

        val queriesFile = roots.map { File(it, "src/test/resources/route_eval_queries.json") }.firstOrNull { it.exists() }
            ?: error("route_eval_queries.json not found from $userDir")
        val root = JSONArray(queriesFile.readText(Charsets.UTF_8))
        val queries = (0 until root.length()).map { i ->
            val obj = root.getJSONObject(i)
            val expected = (0 until obj.optJSONArray("expectedDocs").length()).map { j ->
                obj.optJSONArray("expectedDocs").getString(j)
            }
            RouteEvaluator.EvalQuery(obj.getString("query"), expected)
        }

        val containsResult = RouteEvaluator.evaluate(queries, router = { q -> index.route(q) })
        val bm25Result = RouteEvaluator.evaluate(queries, router = { q -> index.routeByBm25(q, docTexts, topK = 3) })

        println("=== O7 route eval (${queries.size} queries) ===")
        println("contains: precision@3=${containsResult.precisionAtK}, recall@3=${containsResult.recallAtK}")
        println("bm25    : precision@3=${bm25Result.precisionAtK}, recall@3=${bm25Result.recallAtK}")
        println("recall lift = ${bm25Result.recallAtK - containsResult.recallAtK}")
        println("precision delta = ${bm25Result.precisionAtK - containsResult.precisionAtK}")
        assertEquals(queries.size, containsResult.queryCount)
        assertEquals(queries.size, bm25Result.queryCount)
        bm25Result.perQuery.filter { it.recall < 1.0 }.take(25).forEach {
            println("MISS: ${it.query} expected=${it.expected} retrieved=${it.retrieved}")
        }
    }
}
