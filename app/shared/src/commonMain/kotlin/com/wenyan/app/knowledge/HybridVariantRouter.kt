package com.wenyan.app.knowledge

/**
 * 混合路由：contains 优先，contains 命中不足 2 份时用 query 变体库补漏。
 *
 * 评测（1098 条，41/41 文档）：
 *   contains      P=0.207 R=0.201 F1=0.204
 *   hybridFillOne P=0.348 R=0.649 F1=0.453
 * 相比“完全没命中才补”，fill-one 在全量 41 文档覆盖下同时提升 precision 与 recall。
 */
class HybridVariantRouter(
    private val index: KnowledgeIndex,
    private val variantsByDoc: Map<String, List<String>>,
) {
    private val variantRouter = QueryVariantRouter(variantsByDoc)

    fun route(query: String, topK: Int = 3): List<String> {
        val base = index.route(query).take(topK).toMutableList()
        val fillTarget = minOf(2, topK)
        if (base.size < fillTarget) {
            variantRouter.route(query, topK = fillTarget + 1).forEach { d ->
                if (d !in base && base.size < fillTarget) base.add(d)
            }
        }
        return base
    }
}
