package com.wenyan.app.knowledge

/**
 * 文档画像精排器（确定性，无外部依赖）。
 *
 * 对每个候选文档计算 6 个特征，线性加权后取 top-3：
 *  f0 路由关键词命中数
 *  f1 标题 n-gram 重叠
 *  f2 章节标题 n-gram 重叠
 *  f3 画像 BM25（归一化）
 *  f4 全文 BM25（归一化）
 *  f5 否定词 + 关键词同时出现（应为负权重）
 *
 * 权重由 RouteRerankerTrainTest 在真实素材评测集上坐标搜索得到。
 */
class RouteReranker(
    private val index: KnowledgeIndex,
    private val docTexts: Map<String, String>,
    private val profiles: Map<String, DocProfile>,
    private val weights: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, -1.0),
) {
    private val docs: List<String> = index.allDocs().filter { docTexts[it] != null && profiles[it] != null }


    fun rank(query: String, topK: Int = 3): List<String> {
        if (query.isBlank() || docs.isEmpty()) return emptyList()
        val scored = featureVectors(query)
            .map { (doc, f) -> doc to dot(weights, f) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
        return if (scored.isEmpty()) index.route(query) else scored
    }

    /** 返回每个文档的完整特征向量（BM25 特征已按本次查询归一化） */
    fun featureVectors(query: String): List<Pair<String, DoubleArray>> {
        val (profileBm25, fullBm25) = bm25Scores(query)
        val raw = docs.map { d -> d to features(query, d) }
        val f3Norm = normalize(raw.map { profileBm25[it.first] ?: 0.0 })
        val f4Norm = normalize(raw.map { fullBm25[it.first] ?: 0.0 })
        return raw.indices.map { i ->
            val doc = raw[i].first
            val f = raw[i].second
            doc to doubleArrayOf(f[0], f[1], f[2], f3Norm[i], f4Norm[i], f[5])
        }
    }

    fun profileFor(doc: String): DocProfile? = profiles[doc]

    fun score(f: DoubleArray): Double = dot(weights, f)

    private fun features(query: String, doc: String): DoubleArray {
        val profile = profiles[doc] ?: return DoubleArray(6)
        val keywordHits = profile.keywords.count { query.contains(it) }
        val titleOverlap = DocProfile.overlap(query, profile.title)
        val headingOverlap = DocProfile.overlap(query, profile.headings.joinToString(" "))
        // M13 修复：罚分只对「被否定的关键词」生效——原单字「不/没」子串匹配整句，
        // 「不错/要不要」也吃罚分。与 HybridRouter 共用 QueryNegation。
        val negationPenalty = if (QueryNegation.anyNegated(query, profile.keywords)) 1.0 else 0.0
        return doubleArrayOf(
            keywordHits.toDouble(),
            titleOverlap,
            headingOverlap,
            0.0,
            0.0,
            negationPenalty,
        )
    }

    /** 计算某文档的 BM25 原始分（供训练/归一化使用） */
    fun bm25Scores(query: String): Pair<Map<String, Double>, Map<String, Double>> {
        val scorer = Bm25Scorer()
        val profileBm25 = scorer.score(query, docs.map { profiles[it]?.profileText ?: "" })
        val fullBm25 = scorer.score(query, docs.map { docTexts[it] ?: "" })
        return docs.indices.associate { docs[it] to profileBm25[it] } to
            docs.indices.associate { docs[it] to fullBm25[it] }
    }

    private fun normalize(values: List<Double>): List<Double> {
        val max = values.maxOrNull() ?: 0.0
        if (max <= 0.0) return values.map { 0.0 }
        return values.map { it / max }
    }

    private fun dot(w: DoubleArray, f: DoubleArray): Double {
        var sum = 0.0
        for (i in w.indices) sum += w[i] * f[i]
        return sum
    }
}
