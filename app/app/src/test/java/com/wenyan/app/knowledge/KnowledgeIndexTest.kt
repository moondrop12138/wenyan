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
            "knowledge/17-中国法律安全与危机转介.md": {"title": "安全转介"},
            "knowledge/08-嫉妒与吃醋.md": {"title": "嫉妒与吃醋"}
          },
          "routes": [
            {"keywords": ["回复", "怎么回", "话术"], "docs": ["practical/实战话术编排器：从一句回复到后续分支.md"]},
            {"keywords": ["冲突", "吵架", "矛盾"], "docs": ["knowledge/07-沟通冲突与修复.md"]},
            {"keywords": ["家暴", "跟踪", "自杀"], "docs": ["knowledge/17-中国法律安全与危机转介.md"]},
            {"keywords": ["吃醋", "嫉妒"], "docs": ["knowledge/08-嫉妒与吃醋.md"]}
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
        // F67 修复：原 fixture 全部路由合计只有 3 份文档（cap-3 被删掉该断言也不会失败，
        // 恒真）。现 fixture 增加第 4 条路由/文档，输入命中全部 4 条路由——无 cap 实现会
        // 返回 4 份，cap-3 上限逻辑真正被测到
        val docs = index.route("回复冲突跟踪吃醋")
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
