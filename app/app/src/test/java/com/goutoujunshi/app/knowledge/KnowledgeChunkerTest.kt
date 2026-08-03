package com.goutoujunshi.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分块截断 + 知识引擎测试（llm-contract §7：单份 ≤4K token）
 */
class KnowledgeChunkerTest {

    private val sampleDoc = """
        # 标题
        前言内容。

        ## 一、目标不是零冲突
        冲突本身普遍存在。沟通质量和关系满意度互相影响。

        ## 二、冲突前先分类
        信息误解：需要澄清事实。可解决问题：时间、预算、任务。

        ## 三、一个可复用的对话结构
        使用具体事实-感受-需要-请求。
    """.trimIndent()

    @Test
    fun `split creates chunks by double-hash headings`() {
        val chunks = KnowledgeChunker.split(sampleDoc)
        // 大标题 # 被跳过，前言 + 三个 ## 块 = 4
        assertEquals(4, chunks.size)
        assertEquals("前言", chunks[0].heading)
        assertEquals("一、目标不是零冲突", chunks[1].heading)
    }

    @Test
    fun `selectChunks prioritizes keyword hit chunks under budget`() {
        val chunks = KnowledgeChunker.split(sampleDoc)
        // 小预算：只能容纳命中块，未命中块被截断
        val result = KnowledgeChunker.selectChunks(chunks, listOf("对话结构"), maxChars = 80)
        assertTrue(result.contains("可复用的对话结构"))
        assertFalse(result.contains("冲突前先分类"))
    }

    @Test
    fun `truncate respects max chars budget`() {
        val chunks = KnowledgeChunker.split(sampleDoc)
        val result = KnowledgeChunker.selectChunks(chunks, emptyList(), maxChars = 50)
        assertTrue(result.length <= 60)
    }

    @Test
    fun `truncate keeps full content when budget large`() {
        val chunks = KnowledgeChunker.split(sampleDoc)
        val result = KnowledgeChunker.selectChunks(chunks, emptyList(), maxChars = 10000)
        assertTrue(result.contains("沟通质量和关系满意度"))
    }

    @Test
    fun `empty doc returns empty`() {
        assertEquals("", KnowledgeChunker.truncate("", emptyList()))
    }
}
