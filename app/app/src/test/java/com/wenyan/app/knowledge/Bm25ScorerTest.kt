package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O7: BM25 打分器测试 */
class Bm25ScorerTest {

    @Test
    fun `chinese bigram tokenization filters stop words`() {
        val s = Bm25Scorer()
        val terms = s.tokenize("我们冷战了")
        // "我们"是停用词，应被过滤
        assertTrue("我们" !in terms)
        assertTrue("冷战" in terms)
    }

    @Test
    fun `single char returns itself`() {
        assertEquals(listOf("分"), Bm25Scorer().tokenize("分"))
    }

    @Test
    fun `score ranks matching doc higher`() {
        val s = Bm25Scorer()
        val docs = listOf("沟通冲突与修复的方法", "约会穿搭的搭配建议", "冷战与冲突的修复")
        val scores = s.score("我们冷战了", docs)
        val best = docs.indices.maxByOrNull { scores[it] }!!
        assertTrue(scores[best] > 0.0)
        assertTrue(docs[best].contains("冷战"))
    }

    @Test
    fun `empty query scores zero`() {
        val scores = Bm25Scorer().score("", listOf("abc", "def"))
        assertEquals(listOf(0.0, 0.0), scores)
    }

    @Test
    fun `idf downweights common terms across docs`() {
        val s = Bm25Scorer()
        // "冷战"只出现在一篇，"方法"出现在多篇 → "冷战"的贡献应更高
        val docs = listOf("冷战修复", "方法一", "方法二")
        val rare = s.score("冷战", docs)
        val common = s.score("方法", docs)
        assertTrue(rare[0] > common[0])
    }
}
