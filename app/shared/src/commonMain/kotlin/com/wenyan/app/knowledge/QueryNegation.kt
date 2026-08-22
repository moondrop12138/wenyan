package com.wenyan.app.knowledge

/**
 * M13: 查询否定判定（HybridRouter / RouteReranker 共用）。
 *
 * 原实现用单字「不」「没」子串匹配整个查询——「不错」「要不要」「不好意思」
 * 全被判成否定查询：文档被错误过滤 + f5 特征罚分。评测结论失真，切生产后直接生效。
 *
 * 修复：否定判定改为「关键词出现位置的前缀检查」——只有否定词直接前缀修饰
 * 路由关键词时才判否定（「不感兴趣」命中，「不错」不再误伤）。
 */
internal object QueryNegation {

    // 否定前缀（endsWith 匹配；长短语在前保证优先语义）
    private val NEG_PREFIXES = listOf(
        "从来没有", "根本不", "完全不", "一点都不", "已经不", "再也不会",
        "没有", "不曾", "并未", "并不", "从不", "从未", "不再", "不会",
        "没", "不", "别", "无",
    )

    /**
     * [keywords] 中是否存在任一关键词在 [query] 里被否定修饰。
     * 隐含「关键词本身出现在查询中」（与原 hasNegation && keywordHit 语义等价）。
     */
    fun anyNegated(query: String, keywords: Collection<String>): Boolean {
        for (kw in keywords) {
            if (kw.isEmpty()) continue
            var idx = query.indexOf(kw)
            while (idx >= 0) {
                val prefix = query.substring(maxOf(0, idx - 5), idx)
                if (NEG_PREFIXES.any { prefix.endsWith(it) }) return true
                idx = query.indexOf(kw, idx + 1)
            }
        }
        return false
    }
}
