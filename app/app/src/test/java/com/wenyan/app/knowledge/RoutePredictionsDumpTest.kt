package com.wenyan.app.knowledge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 评测基线落盘（:app 模块 build/reports/route-eval/）：
 *  - predictions-baseline.json：contains(KnowledgeIndex.route) / 纯变体(QueryVariantRouter) /
 *    hybridFillOne(HybridVariantRouter) 对「评测集 + 金种子」全部条目的逐条预测；
 *  - baseline-buckets.json：各方法×各桶（negative/multi/core/longtail）宏平均 {P,R,F1,n}，
 *    分桶口径见 [RouteBucketMetrics]，桶统计在评测集（route_eval_queries.json）上计算。
 * 产物是路由改动的 diff 基线：换路由实现后重跑对比逐条预测，无需翻测试 stdout。
 *
 * JSON 用手写序列化而不用 org.json 写：JSONObject(Map) 内部落 HashMap，键序被打乱，
 * 基线 diff 需要稳定键序（file/index/query/contains/variant/hybrid、negative→longtail 固定）。
 */
class RoutePredictionsDumpTest {

    @Test
    fun `dump predictions and bucket metrics baseline`() {
        val index = KnowledgeEvalCorpus.loadIndex()
        val coreDocs = index.allDocs().toSet() // routes-v2.json routes 覆盖的文档集
        val variants = KnowledgeEvalCorpus.loadVariants()
        val variantRouter = QueryVariantRouter(variants)
        val hybridRouter = HybridVariantRouter(index, variants)
        val mainQueries = KnowledgeEvalCorpus.loadQueries()
        val goldQueries = KnowledgeEvalCorpus.loadGold()

        val methods: Map<String, (String) -> List<String>> = mapOf(
            "contains" to { q -> index.route(q) },
            "variant" to { q -> variantRouter.route(q) },
            "hybrid" to { q -> hybridRouter.route(q) },
        )

        // —— 逐条预测：main + gold，index 为各文件内 0 起下标 ——
        val entryLines = mutableListOf<String>()
        for ((file, queries) in listOf("main" to mainQueries, "gold" to goldQueries)) {
            queries.forEachIndexed { i, eq ->
                val preds = methods.mapValues { (_, router) -> router(eq.query) }
                entryLines += buildString {
                    append("    {\n")
                    append("      \"file\": ${jsonStr(file)},\n")
                    append("      \"index\": $i,\n")
                    append("      \"query\": ${jsonStr(eq.query)},\n")
                    append(preds.entries.joinToString(",\n") { (name, docs) ->
                        "      ${jsonStr(name)}: ${jsonArr(docs)}"
                    })
                    append("\n    }")
                }
            }
        }
        assertEquals(mainQueries.size + goldQueries.size, entryLines.size)
        val predictionsJson = buildString {
            append("{\n  \"entries\": [\n")
            append(entryLines.joinToString(",\n"))
            append("\n  ]\n}")
        }

        // —— 分桶宏平均：在评测集上计算，方法×桶 固定键序 ——
        val methodStats = mutableMapOf<String, Map<String, RouteBucketMetrics.BucketStat>>()
        for ((name, router) in methods) {
            val perQuery = mainQueries.map { eq ->
                val pred = router(eq.query)
                RouteBucketMetrics.bucketOf(eq.expectedDocs, coreDocs) to
                    RouteBucketMetrics.prf(pred, eq.expectedDocs)
            }
            val byBucket = RouteBucketMetrics.macroByBucket(perQuery)
            // 桶划分是划分（partition）：各桶 n 之和必须等于评测集条数
            assertEquals(
                "桶 n 之和应等于评测集条数（method=$name）",
                mainQueries.size,
                byBucket.values.sumOf { it.n },
            )
            methodStats[name] = byBucket
        }
        val bucketsJson = buildString {
            append("{\n  \"methods\": {\n")
            append(methodStats.entries.joinToString(",\n") { (name, byBucket) ->
                val buckets = RouteBucketMetrics.allBuckets.joinToString(",\n") { bucket ->
                    val s = byBucket.getValue(bucket)
                    "      ${jsonStr(bucket)}: {\"P\": ${s.p}, \"R\": ${s.r}, \"F1\": ${s.f1}, \"n\": ${s.n}}"
                }
                "    ${jsonStr(name)}: {\n$buckets\n    }"
            })
            append("\n  }\n}")
        }

        // —— 落盘（:app 模块 build/reports/route-eval/）——
        val reportDir = resolveReportDir()
        reportDir.mkdirs()
        val predictionsFile = reportDir.resolve("predictions-baseline.json")
        val bucketsFile = reportDir.resolve("baseline-buckets.json")
        predictionsFile.writeText(predictionsJson, Charsets.UTF_8)
        bucketsFile.writeText(bucketsJson, Charsets.UTF_8)

        // 落盘自检：文件非空且可回读，条数一致
        assertTrue(predictionsFile.length() > 0)
        assertTrue(bucketsFile.length() > 0)
        val reread = JSONObject(predictionsFile.readText(Charsets.UTF_8)).getJSONArray("entries")
        assertEquals(mainQueries.size + goldQueries.size, reread.length())
        println("predictions -> ${predictionsFile.absolutePath} (entries=${reread.length()})")
        println("buckets     -> ${bucketsFile.absolutePath}")
    }

    /** 字符串 JSON 转义（含控制字符），中文按 UTF-8 原样输出 */
    private fun jsonStr(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private fun jsonArr(docs: List<String>): String =
        docs.joinToString(", ", "[", "]") { jsonStr(it) }

    /**
     * :app 模块目录探测（与 [KnowledgeEvalCorpus] 双根探测同款）：
     * Gradle 单测工作目录一般是模块目录，从仓库根跑时退一层；以
     * src/test/resources/route_eval_queries.json 存在者为 :app 模块根。
     */
    private fun resolveReportDir(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val moduleDir = listOf(userDir, userDir.resolve("app"))
            .firstOrNull { it.resolve("src/test/resources/route_eval_queries.json").exists() }
            ?: error("无法从 ${userDir.absolutePath} 定位 :app 模块目录")
        return moduleDir.resolve("build/reports/route-eval")
    }
}
