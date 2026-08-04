package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识路由引擎测试（AC-06）
 */
class KnowledgeIndexTest {

    private val fakeRoutesJson = """
        {
          "version": 1,
          "files": {
            "practical/实战话术编排器：从一句回复到后续分支.md": {"title": "实战话术编排器"},
            "knowledge/07-沟通冲突与修复.md": {"title": "沟通冲突与修复"},
            "knowledge/17-中国法律安全与危机转介.md": {"title": "安全转介"}
          },
          "routes": [
            {"keywords": ["回复", "怎么回", "话术"], "docs": ["practical/实战话术编排器：从一句回复到后续分支.md"]},
            {"keywords": ["冲突", "吵架", "矛盾"], "docs": ["knowledge/07-沟通冲突与修复.md"]},
            {"keywords": ["家暴", "跟踪", "自杀"], "docs": ["knowledge/17-中国法律安全与危机转介.md"]}
          ]
        }
    """.trimIndent()

    @Test
    fun `route hits reply scenario`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        val docs = index.route("这句怎么回比较好")
        assertEquals(1, docs.size)
        assertTrue(docs[0].contains("实战话术编排器"))
    }

    @Test
    fun `route hits conflict scenario`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        val docs = index.route("我们最近总吵架，怎么修复")
        assertTrue(docs.any { it.contains("沟通冲突") })
    }

    @Test
    fun `route hits crisis scenario`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        val docs = index.route("我被他跟踪了，很害怕")
        assertTrue(docs.any { it.contains("危机转介") })
    }

    @Test
    fun `route returns at most 3 docs`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        val docs = index.route("话术冲突怎么回")
        assertTrue(docs.size <= 3)
        assertEquals(docs.size, docs.distinct().size)
    }

    @Test
    fun `route empty input returns empty`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        assertTrue(index.route("").isEmpty())
        assertTrue(index.route("   ").isEmpty())
    }

    @Test
    fun `titleOf returns known title`() {
        val index = KnowledgeIndex(fakeRoutesJson)
        assertEquals("实战话术编排器", index.titleOf("practical/实战话术编排器：从一句回复到后续分支.md"))
    }
}
