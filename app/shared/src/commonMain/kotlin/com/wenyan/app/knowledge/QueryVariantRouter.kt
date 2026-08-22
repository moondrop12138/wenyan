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

    /** L7 修复：全变体统一语料（doc → variant 平铺），df/N/avgdl 全局一致，跨文档分数可比 */
    private val flatVariants: List<Pair<String, String>> =
        variantsByDoc.flatMap { (doc, vs) -> vs.map { doc to it } }

    fun route(query: String, topK: Int = 3): List<String> {
        // L7 修复：原对每份文档的变体列表独立建 BM25 语料——N/avgdl/df 均为列表内统计，
        // 跨文档分数不可比，排序系统性偏向变体少的文档。现一次性对全局语料打分，
        // 文档分 = 其名下变体的最高分。
        if (query.isBlank() || flatVariants.isEmpty()) return emptyList()
        val scores = scorer.score(query, flatVariants.map { it.second })
        val bestByDoc = HashMap<String, Double>()
        flatVariants.forEachIndexed { i, (doc, _) ->
            val s = scores.getOrElse(i) { 0.0 }
            if (s > (bestByDoc[doc] ?: 0.0)) bestByDoc[doc] = s
        }
        return bestByDoc.filter { it.value > 0.0 }
            .entries.sortedByDescending { it.value }
            .take(topK)
            .map { it.key }
    }
}
