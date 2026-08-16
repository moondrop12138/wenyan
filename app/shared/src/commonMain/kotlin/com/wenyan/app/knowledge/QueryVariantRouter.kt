package com.wenyan.app.knowledge

/**
 * Query 变体路由（重方案实验）。
 *
 * 每份文档预先用 LLM 生成一批“用户可能输入的真实问法”作为变体库；
 * 路由时把用户 query 对所有变体做 BM25，取每份文档的最高变体分作为文档分，
 * 返回 top-3。这相当于“问题库检索”，比直接对文档全文打分更贴近用户表达。
 */
class QueryVariantRouter(
    private val variantsByDoc: Map<String, List<String>>,
) {
    private val scorer = Bm25Scorer()

    fun route(query: String, topK: Int = 3): List<String> {
        if (query.isBlank() || variantsByDoc.isEmpty()) return emptyList()
        return variantsByDoc.map { (doc, variants) ->
            val maxScore = if (variants.isEmpty()) 0.0
            else scorer.score(query, variants).maxOrNull() ?: 0.0
            doc to maxScore
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
}
