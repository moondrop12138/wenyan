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

    /** M8: 可剥除的否定前缀（「拒绝/否认」是独立动词不算前缀） */
    private val negationPrefixes = listOf("不", "没", "没有", "从未", "不再", "别")

    /** M8: 功能词 bigram——共享核心词判定时过滤，防「她说」这类窗口放大误报 */
    private val stopBigrams = setOf(
        "她说", "他说", "我说", "你说", "他们", "她们", "我们", "你们", "自己",
        "就是", "还是", "已经", "这个", "那个", "什么", "没有", "不会", "不能",
        "一下", "一直", "非常", "觉得", "感觉", "现在", "以前", "时候", "但是",
    )

    /** 判断两条事实是否矛盾 */
    fun conflicts(a: String, b: String): Boolean {
        val x = a.trim(); val y = b.trim()
        if (x.isEmpty() || y.isEmpty() || x == y) return false
        // M8 修复：反义对只在「双方均为肯定式出现」时判矛盾——原纯 contains 把
        // 「我不喜欢她了」vs「她很讨厌我」判成矛盾（前者「喜欢」带否定前缀 = 讨厌义，
        // 与后者一致而非冲突）。
        for ((pos, neg) in antonymPairs) {
            val xPos = hasAffirmative(x, pos); val xNeg = hasAffirmative(x, neg)
            val yPos = hasAffirmative(y, pos); val yNeg = hasAffirmative(y, neg)
            if ((xPos && yNeg) || (xNeg && yPos)) return true
        }
        // 否定词不对称 + 共享核心词 → 疑似矛盾
        val xNeg = negationWords.any { x.contains(it) }
        val yNeg = negationWords.any { y.contains(it) }
        if (xNeg != yNeg && sharedCore(x, y).isNotBlank()) return true
        return false
    }

    /** M8: word 在 text 中以「肯定式」出现（存在未被否定前缀覆盖的出现位置） */
    private fun hasAffirmative(text: String, word: String): Boolean {
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val prefix = text.substring(maxOf(0, idx - 2), idx)
            if (negationPrefixes.none { prefix.endsWith(it) }) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    /** 返回与 newFact 矛盾的既有事实原文列表 */
    fun findConflicts(newFact: String, existing: List<String>): List<String> =
        existing.filter { conflicts(newFact, it) }

    /** 共享核心词（取一个长度 ≥2 的公共 bigram；M8: 过滤功能词窗口） */
    private fun sharedCore(a: String, b: String): String {
        val setA = bigrams(a).toSet()
        return bigrams(b).firstOrNull { it in setA && it !in stopBigrams } ?: ""
    }

    private fun bigrams(s: String): List<String> =
        if (s.length < 2) emptyList() else (0..s.length - 2).map { s.substring(it, it + 2) }
}
