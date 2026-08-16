package com.wenyan.app.knowledge

/**
 * 混合路由：contains 优先，完全没命中时用 LLM 生成的 query 变体库补漏。
 *
 * 评测（618 条真实素材）：
 *   contains      P=0.368 R=0.357 F1=0.362
 *   hybridFillEmpty P=0.440 R=0.508 F1=0.472
 * 这个策略同时提升 precision 与 recall，适合作为生产候选。
 */
class HybridVariantRouter(
    private val index: KnowledgeIndex,
    private val variantsByDoc: Map<String, List<String>>,
) {
    private val variantRouter = QueryVariantRouter(variantsByDoc)

    fun route(query: String, topK: Int = 3): List<String> {
        val base = index.route(query).take(topK).toMutableList()
        // contains 一旦有命中，就信任精确关键词，不再用变体稀释 precision；
        // contains 完全没命中时，才用变体库按用户问法召回。
        return if (base.isEmpty()) variantRouter.route(query, topK) else base
    }
}
