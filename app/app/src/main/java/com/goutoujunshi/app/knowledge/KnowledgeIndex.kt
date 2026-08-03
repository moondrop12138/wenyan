package com.goutoujunshi.app.knowledge

import org.json.JSONArray
import org.json.JSONObject

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
        val root = JSONObject(rawJson)
        fileTitles = buildMap {
            val files = root.optJSONObject("files") ?: JSONObject()
            files.keys().forEach { key ->
                val value = files.optJSONObject(key)
                put(key, value?.optString("title", "") ?: "")
            }
        }
        routes = buildList {
            val arr = root.optJSONArray("routes") ?: JSONArray()
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

    private fun toStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.getString(i))
        }
    }
}
