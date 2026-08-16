package com.wenyan.app.knowledge

/**
 * 文档画像：每份可路由文档的精简语义指纹。
 * 只用标题/章节标题/路由关键词，不用全文，避免 BM25 被正文噪声稀释。
 */
data class DocProfile(
    val doc: String,
    val title: String,
    val headings: List<String>,
    val keywords: List<String>,
    /** 画像文本：标题 + 章节标题 + 路由关键词（供 BM25 使用） */
    val profileText: String,
) {
    companion object {
        /** 从 routes.json 索引 + 文档全文构造画像 */
        fun build(index: KnowledgeIndex, docTexts: Map<String, String>): Map<String, DocProfile> {
            return index.allDocs().mapNotNull { doc ->
                val text = docTexts[doc] ?: return@mapNotNull null
                val title = index.titleOf(doc)
                val headings = text.lineSequence()
                    .filter { it.trimStart().startsWith("#") }
                    .map { it.trim().trimStart('#', ' ', '\t') }
                    .filter { it.isNotBlank() }
                    .toList()
                val keywords = index.routesFor(doc).flatMap { it.keywords }.distinct()
                val profileText = listOf(title, headings.joinToString(" "), keywords.joinToString(" "))
                    .joinToString(" ")
                doc to DocProfile(doc, title, headings, keywords, profileText)
            }.toMap()
        }

        /** 从文本中提取 2-4 字 n-gram（去标点/空白） */
        fun ngrams(text: String, minLen: Int = 2, maxLen: Int = 4): Set<String> {
            val cleaned = text.replace(Regex("[\\s，。！？、,.!?；;：:（）()《》「」\"'“”]"), "")
            if (cleaned.length < minLen) return emptySet()
            val result = mutableSetOf<String>()
            for (len in minLen..maxLen) {
                if (len > cleaned.length) break
                for (i in 0..cleaned.length - len) {
                    result.add(cleaned.substring(i, i + len))
                }
            }
            return result
        }

        /** 查询与一段文本的 n-gram 重叠比例（0..1） */
        fun overlap(query: String, text: String): Double {
            if (query.isBlank() || text.isBlank()) return 0.0
            val qGrams = ngrams(query)
            val tGrams = ngrams(text)
            if (tGrams.isEmpty()) return 0.0
            return qGrams.count { it in tGrams }.toDouble() / tGrams.size
        }
    }
}
