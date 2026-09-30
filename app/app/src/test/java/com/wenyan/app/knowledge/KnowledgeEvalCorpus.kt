package com.wenyan.app.knowledge

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * F64/F68 精简：评测脚手架收敛为单份 fixture——原先「userDir/roots 双根查找、
 * routes-v2.json / route_eval_queries.json / route_query_variants.json 定位、docTexts 加载
 * 循环、queries/variants JSON 解析、f1Of」在 RouteEvaluatorHarnessTest / RouteRerankerTrainTest /
 * HybridRouterTrainTest / QueryVariantRouterTest 四处逐字复制粘贴（连错误文案都各自漂移），
 * 评测集路径或格式一变需同步 4 处。
 */
internal object KnowledgeEvalCorpus {

    private val roots: List<File> by lazy {
        val userDir = System.getProperty("user.dir") ?: "."
        listOf(File(userDir), File(userDir, "app"))
    }

    private fun locate(relativePath: String): File =
        roots.map { File(it, relativePath) }.firstOrNull { it.exists() }
            ?: error("$relativePath not found from ${roots[0]}")

    /** 生产路由表（routes-v2.json；F28 起为唯一副本，routes.json 已删） */
    val routesFile: File by lazy { locate("src/main/assets/knowledge/routes-v2.json") }

    fun loadIndex(): KnowledgeIndex = KnowledgeIndex(routesFile.readText(Charsets.UTF_8))

    /** 路由表 files 段的文档键（QueryVariantRouterTest 变体库覆盖率校验用） */
    fun loadRouteFileKeys(): List<String> =
        JSONObject(routesFile.readText(Charsets.UTF_8))
            .getJSONObject("files").keys().asSequence().toList()

    /** 全文档原文（缺失文件跳过），BM25 / 画像精排共用 */
    fun loadDocTexts(index: KnowledgeIndex): Map<String, String> {
        val docTexts = mutableMapOf<String, String>()
        for (doc in index.allDocs()) {
            val f = File(routesFile.parentFile, doc)
            if (f.exists()) docTexts[doc] = f.readText(Charsets.UTF_8)
        }
        return docTexts
    }

    /** 评测集：真实素材 query + expectedDocs */
    fun loadQueries(): List<RouteEvaluator.EvalQuery> {
        val file = locate("src/test/resources/route_eval_queries.json")
        val root = JSONArray(file.readText(Charsets.UTF_8))
        return (0 until root.length()).map { i ->
            val obj = root.getJSONObject(i)
            val expected = (0 until obj.optJSONArray("expectedDocs").length()).map { j ->
                obj.optJSONArray("expectedDocs").getString(j)
            }
            RouteEvaluator.EvalQuery(obj.getString("query"), expected)
        }
    }

    /** LLM 生成的 query 变体库：文档路径 → 变体列表 */
    fun loadVariants(): Map<String, List<String>> {
        val file = locate("src/test/resources/route_query_variants.json")
        val variantsRoot = JSONObject(file.readText(Charsets.UTF_8))
        return variantsRoot.keys().asSequence().associateWith { key ->
            val arr = variantsRoot.getJSONArray(key)
            (0 until arr.length()).map { arr.getString(it) }
        }
    }

    /** F1@3（四个评测测试共用的同一公式，原先各拷一份） */
    fun f1(r: RouteEvaluator.EvalResult): Double {
        val sum = r.precisionAtK + r.recallAtK
        return if (sum <= 0.0) 0.0 else 2.0 * r.precisionAtK * r.recallAtK / sum
    }
}
