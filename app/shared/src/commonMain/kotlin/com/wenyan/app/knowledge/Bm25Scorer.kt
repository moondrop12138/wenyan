package com.wenyan.app.knowledge

import kotlin.math.ln

/**
 * O7: BM25 打分器（零依赖、纯 JVM 可测）。
 * 词项用字符 bigram（文档与 query 统一口径）；k1/b 参数化；内置高频 2 字停用表。
 * 用于知识路由粗召回（top-8）与分块命中打分，替代纯 contains 计数。
 */
class Bm25Scorer(
    private val k1: Double = 1.4,
    private val b: Double = 0.75,
    private val stopWords: Set<String> = DEFAULT_STOP_WORDS,
) {

    /** query 对文档集逐篇打分（BM25），返回与 docs 等长的分数列表 */
    fun score(query: String, docs: List<String>): List<Double> {
        val docTerms = docs.map { tokenize(it) }
        val n = docs.size
        if (n == 0) return emptyList()
        val docLengths = docTerms.map { it.size }
        val avgDl = docLengths.average().takeIf { it > 0 } ?: 1.0

        // 文档频率 df：某 bigram 出现在多少篇文档里
        val df = mutableMapOf<String, Int>()
        docTerms.forEach { terms -> terms.toSet().forEach { t -> df[t] = (df[t] ?: 0) + 1 } }

        val queryTerms = tokenize(query).distinct()
        return docs.indices.map { i ->
            val terms = docTerms[i]
            if (terms.isEmpty()) return@map 0.0
            val tf = terms.groupingBy { it }.eachCount()
            val dl = docLengths[i]
            queryTerms.sumOf { t ->
                val tfi = tf[t] ?: 0
                if (tfi == 0) {
                    0.0
                } else {
                    val dft = df[t] ?: 0
                    val idf = ln((n - dft + 0.5) / (dft + 0.5) + 1.0)
                    val denom = tfi + k1 * (1 - b + b * dl / avgDl)
                    idf * (tfi * (k1 + 1)) / denom
                }
            }
        }
    }

    /** 字符 bigram 分词：去标点/空白后按相邻 2 字切分，过滤停用词；单字直接返回 */
    fun tokenize(text: String): List<String> {
        val cleaned = text.replace(Regex("[\\s，。！？、,.!?；;：:（）()《》「」\"'“”]"), "")
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length == 1) return listOf(cleaned)
        return buildList {
            for (i in 0 until cleaned.length - 1) {
                val bg = cleaned.substring(i, i + 2)
                if (bg !in stopWords) add(bg)
            }
        }
    }

    companion object {
        val DEFAULT_STOP_WORDS = setOf(
            "我们", "他们", "你们", "这个", "那个", "什么", "怎么", "就是", "但是",
            "因为", "所以", "现在", "已经", "可以", "还是", "一个", "没有", "自己",
            "不是", "知道", "觉得", "时候", "这样", "那样", "如果", "然后", "关于",
            "这些", "那些",
        )
    }
}
