package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConversationState JSON 序列化/反序列化单测（v1.3）。
 * 覆盖：完整往返、空串/损坏 JSON 兜底为 EMPTY、hasActiveTopic 判定。
 */
class ConversationStateTest {

    @Test
    fun `序列化后反序列化完整还原`() {
        val state = ConversationState(
            topicSummary = "她划清朋友边界",
            conclusionGiven = "尊重边界，别再推进",
            lastReplyText = "好的，我尊重你的想法",
            replyAlreadyGiven = true,
            turnCount = 3,
        )
        val restored = ConversationState.fromJson(state.toJson())
        assertEquals(state, restored)
    }

    @Test
    fun `空串反序列化为 EMPTY`() {
        assertEquals(ConversationState.EMPTY, ConversationState.fromJson(""))
        assertEquals(ConversationState.EMPTY, ConversationState.fromJson(null))
        assertEquals(ConversationState.EMPTY, ConversationState.fromJson("   "))
    }

    @Test
    fun `损坏 JSON 反序列化兜底为 EMPTY`() {
        assertEquals(ConversationState.EMPTY, ConversationState.fromJson("{broken json"))
        assertEquals(ConversationState.EMPTY, ConversationState.fromJson("not json at all"))
    }

    @Test
    fun `缺字段 JSON 用默认值补齐`() {
        val restored = ConversationState.fromJson("""{"topicSummary":"旧话题"}""")
        assertEquals("旧话题", restored.topicSummary)
        assertEquals("", restored.conclusionGiven)
        assertFalse(restored.replyAlreadyGiven)
        assertEquals(0, restored.turnCount)
    }

    @Test
    fun `hasActiveTopic 仅当话题摘要非空`() {
        assertFalse(ConversationState.EMPTY.hasActiveTopic)
        assertFalse(ConversationState(conclusionGiven = "有结论但没话题").hasActiveTopic)
        assertTrue(ConversationState(topicSummary = "她划清朋友边界").hasActiveTopic)
    }
}
