package com.wenyan.app.knowledge

/**
 * Hybrid 路由：contains 精确命中优先，画像精排只用来补足剩余名额。
 *
 * 设计目标：保住 contains 的 precision，同时用画像精排把 recall 拉上来。
 * 流程：
 *  1. contains 精确命中结果（按命中数排序，最多 topK）
 *  2. 对未命中的剩余文档用 RouteReranker 打分
 *  3. 过滤否定词命中的文档（“没分手”不再补“分手修复”）
 *  4. 低于 fillThreshold 的不补（宁缺毋滥）
 *  5. contains + 补足结果合并，最多 topK
 */
class HybridRouter(
    private val index: KnowledgeIndex,
    private val reranker: RouteReranker,
    private val fillThreshold: Double = 0.0,
) {
    private val docs: List<String> = index.allDocs()
    private val negationWords = setOf("不", "没", "没有", "从未", "不想", "拒绝", "否认", "不是", "从不")

    fun route(query: String, topK: Int = 3): List<String> {
        if (query.isBlank() || docs.isEmpty()) return emptyList()

        // 1. contains 精确命中，先做否定过滤
        val containsDocs = index.route(query).filterNot { negated(query, it) }

        // 2. 剩余文档由画像精排打分
        val featureVectors = reranker.featureVectors(query).toMap()
        val fillCandidates = docs
            .filter { it !in containsDocs }
            .map { doc -> doc to score(query, doc, featureVectors[doc]) }
            .filter { (doc, s) -> !negated(query, doc) && s > fillThreshold }
            .sortedByDescending { it.second }
            .map { it.first }

        // 3. 合并：contains 优先，再用补足填满
        val result = linkedSetOf<String>()
        result.addAll(containsDocs)
        for (doc in fillCandidates) {
            if (result.size >= topK) break
            result.add(doc)
        }
        return result.take(topK)
    }

    private fun score(query: String, doc: String, f: DoubleArray?): Double {
        if (f == null) return 0.0
        return reranker.score(f)
    }

    private fun negated(query: String, doc: String): Boolean {
        val profile = reranker.profileFor(doc) ?: return false
        val hasNegation = negationWords.any { query.contains(it) }
        return hasNegation && profile.keywords.any { query.contains(it) }
    }
}
