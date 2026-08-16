package com.wenyan.app.knowledge

import com.wenyan.app.json.JsonArray
import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject

/**
 * 编译期路由表解析（routes.json，AC-06 知识路由）
 * 纯 JVM 可测。
 */
data class KnowledgeRoute(
    val keywords: List<String>,
    val docs: List<String>,
)

/**
 * 知识路由索引：加载 routes.json，按关键词命中 1-3 份文档。
 */
class KnowledgeIndex(rawJson: String) {

    private val routes: List<KnowledgeRoute>
    private val fileTitles: Map<String, String>

    init {
        val root = Json.obj(rawJson)
        fileTitles = buildMap {
            val files = root.optJSONObject("files") ?: Json.obj()
            files.keys().forEach { key ->
                val value = files.optJSONObject(key)
                put(key, value?.optString("title", "") ?: "")
            }
        }
        routes = buildList {
            val arr = root.optJSONArray("routes") ?: Json.arr()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    KnowledgeRoute(
                        keywords = toStringList(obj.optJSONArray("keywords")),
                        docs = toStringList(obj.optJSONArray("docs")),
                    )
                )
            }
        }
    }

    fun titleOf(relativePath: String): String = fileTitles[relativePath] ?: relativePath

    /**
     * 按输入文本路由：命中关键词最多的规则优先，返回 1-3 份文档（去重、限 3 份）
     */
    fun route(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val scored = routes.mapNotNull { route ->
            val hits = route.keywords.count { input.contains(it) }
            if (hits > 0) hits to route.docs else null
        }.sortedByDescending { it.first }

        val result = linkedSetOf<String>()
        for ((_, docs) in scored) {
            for (doc in docs) {
                if (result.size >= 3) return result.toList()
                result.add(doc)
            }
        }
        return result.toList()
    }

    fun allDocs(): List<String> = routes.flatMap { it.docs }.distinct()

    /** 返回映射到指定文档的所有 route（DocProfile 构造用） */
    fun routesFor(doc: String): List<KnowledgeRoute> = routes.filter { doc in it.docs }

    /**
     * O7: 纯 BM25 路由（两阶段检索的粗召回候选生成）。
     * 当前仅用于离线评测对比，生产 route() 仍保持 contains 基线；
     * 待真实评测集 recall 提升 ≥15% 决策门通过后再切换（见 RouteEvaluator）。
     */
    fun routeByBm25(
        input: String,
        docTexts: Map<String, String>,
        topK: Int = 3,
    ): List<String> {
        if (input.isBlank() || docTexts.isEmpty()) return emptyList()
        val docs = allDocs().filter { docTexts[it] != null }
        if (docs.isEmpty()) return emptyList()
        val scorer = Bm25Scorer()
        val scores = scorer.score(input, docs.map { docTexts[it] ?: "" })
        return docs.indices
            .map { i -> docs[i] to scores[i] }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun toStringList(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.getString(i))
        }
    }
}
