package com.wenyan.app.domain

/**
 * O2: 记忆事实冲突检测（纯启发式，JVM 可测）。
 * 新事实入库前与既有事实做矛盾判定——反义/极性对 + 否定词 + 共享核心词。
 * 命中即标冲突对，供记忆页裁决；裁决后不再重复告警（由调用方持久化裁决结果）。
 */
object MemoryConflictDetector {

    // 常见极性/反义对（一方出现即可能与另一方矛盾）
    private val antonymPairs = listOf(
        "喜欢" to "讨厌", "喜欢" to "没感觉",
        "爱" to "恨", "爱" to "不爱",
        "主动" to "被动", "热情" to "冷淡",
        "想要" to "拒绝", "在追" to "拒绝", "感兴趣" to "没兴趣",
        "在一起" to "分手", "复合" to "放下",
    )

    private val negationWords = listOf("不", "没", "从未", "拒绝", "否认", "不想", "没有")

    /** 判断两条事实是否矛盾 */
    fun conflicts(a: String, b: String): Boolean {
        val x = a.trim(); val y = b.trim()
        if (x.isEmpty() || y.isEmpty() || x == y) return false
        // 反义/极性对命中
        for ((pos, neg) in antonymPairs) {
            if ((x.contains(pos) && y.contains(neg)) || (x.contains(neg) && y.contains(pos))) return true
        }
        // 否定词不对称 + 共享核心词 → 疑似矛盾
        val xNeg = negationWords.any { x.contains(it) }
        val yNeg = negationWords.any { y.contains(it) }
        if (xNeg != yNeg && sharedCore(x, y).isNotBlank()) return true
        return false
    }

    /** 返回与 newFact 矛盾的既有事实原文列表 */
    fun findConflicts(newFact: String, existing: List<String>): List<String> =
        existing.filter { conflicts(newFact, it) }

    /** 共享核心词（取一个长度 ≥2 的公共 bigram） */
    private fun sharedCore(a: String, b: String): String {
        val setA = bigrams(a).toSet()
        return bigrams(b).firstOrNull { it in setA } ?: ""
    }

    private fun bigrams(s: String): List<String> =
        if (s.length < 2) emptyList() else (0..s.length - 2).map { s.substring(it, it + 2) }
}
