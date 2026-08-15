package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O7: KnowledgeIndex.routeByBm25 候选生成（离线评测用，不切生产） */
class KnowledgeIndexBm25Test {

    private val index = KnowledgeIndex(
        """
        {
          "files": {
            "a.md": {"title": "沟通冲突"},
            "b.md": {"title": "约会穿搭"},
            "c.md": {"title": "分手修复"}
          },
          "routes": [
            {"keywords": ["冲突"], "docs": ["a.md"]},
            {"keywords": ["穿搭"], "docs": ["b.md"]},
            {"keywords": ["分手"], "docs": ["c.md"]}
          ]
        }
        """.trimIndent()
    )

    private val docs = mapOf(
        "a.md" to "我们冷战了，沟通冲突需要修复",
        "b.md" to "约会穿搭的搭配建议",
        "c.md" to "分手后如何修复关系",
    )

    @Test
    fun `bm25 route finds semantic bigram overlap`() {
        val hits = index.routeByBm25("我们冷战了", docs, topK = 1)
        assertEquals(listOf("a.md"), hits)
    }

    @Test
    fun `bm25 route skips nonmatching docs`() {
        val hits = index.routeByBm25("今天天气", docs, topK = 3)
        assertTrue(hits.isEmpty())
    }
}
