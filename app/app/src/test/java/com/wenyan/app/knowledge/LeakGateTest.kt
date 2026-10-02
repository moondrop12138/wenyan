package com.wenyan.app.knowledge

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泄漏门禁：评测集 query 与变体库（route_query_variants.json）任意 query×变体的
 * 最长公共子串必须 < 8 字符，违规即失败。
 *
 * 变体库由 LLM 对文档生成、评测集为人工采集：若两边出现 ≥8 字公共子串，说明评测 query
 * 疑似从变体库（或同一来源）抄来——「变体路由 F1 高」会退化为背题而非泛化能力。
 * 门禁失败即评测集污染，需换题而不是放宽阈值。
 */
class LeakGateTest {

    @Test
    fun `no eval query shares a long common substring with variant library`() {
        val queries = KnowledgeEvalCorpus.loadQueries().map { it.query }.distinct()
        val variants = KnowledgeEvalCorpus.loadVariants().values.flatten().distinct()
        val threshold = 8

        val violations = mutableListOf<String>()
        outer@ for (q in queries) {
            for (v in variants) {
                val (len, sub) = longestCommonSubstring(q, v)
                if (len >= threshold) {
                    violations += "query=\"$q\" × variant=\"$v\" lcs=$len 子串=\"$sub\""
                    if (violations.size >= 10) break@outer // 失败信息列前 10 处即可定位
                }
            }
        }
        assertTrue(
            "发现 ${violations.size}+ 处评测集×变体库泄漏（最长公共子串 >= $threshold 字符）：\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** 最长公共子串（滚动行 DP，O(|a|·|b|)）；返回 (长度, 子串本身) */
    private fun longestCommonSubstring(a: String, b: String): Pair<Int, String> {
        if (a.isEmpty() || b.isEmpty()) return 0 to ""
        val x = a.toCharArray()
        val y = b.toCharArray()
        var best = 0
        var bestEnd = 0 // a 中结束位置（exclusive）
        var prev = IntArray(y.size + 1)
        var cur = IntArray(y.size + 1)
        for (i in x.indices) {
            for (j in y.indices) {
                cur[j + 1] = if (x[i] == y[j]) prev[j] + 1 else 0
                if (cur[j + 1] > best) {
                    best = cur[j + 1]
                    bestEnd = i + 1
                }
            }
            val tmp = prev
            prev = cur
            cur = tmp // cur 各位下一轮全量覆写（下标 0 恒 0），无需清零
        }
        return best to if (best == 0) "" else a.substring(bestEnd - best, bestEnd)
    }
}
