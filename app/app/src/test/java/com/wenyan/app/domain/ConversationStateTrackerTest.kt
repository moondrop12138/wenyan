package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConversationStateTracker 状态机单测（v1.3）。
 *
 * 覆盖：
 * - 同题追问 turnCount 递增、新话题重置
 * - onModelReply 记录结论/话术、查重标记
 * - buildStatePrefix 生成禁止复读的状态前缀
 * - isSameTopic 启发式：追问开场=同题、聊天记录粘贴=新题、默认同题
 */
class ConversationStateTrackerTest {

    private val tracker = ConversationStateTracker()

    // ===== onUserInput =====

    @Test
    fun `无进行中话题时输入不改变状态`() {
        val next = tracker.onUserInput(ConversationState.EMPTY, "你好")
        assertEquals(ConversationState.EMPTY, next)
    }

    @Test
    fun `同题追问时轮次加一`() {
        val state = ConversationState(topicSummary = "她划清朋友边界", turnCount = 1)
        val next = tracker.onUserInput(state, "那我还该追她吗")
        assertEquals(2, next.turnCount)
        assertEquals("她划清朋友边界", next.topicSummary)
    }

    @Test
    fun `本地判为新话题时重置为空白第一轮`() {
        val state = ConversationState(
            topicSummary = "她划清朋友边界",
            conclusionGiven = "尊重边界",
            lastReplyText = "好的，理解",
            replyAlreadyGiven = true,
            turnCount = 3,
        )
        // 粘贴完整聊天记录 = 换题信号
        val next = tracker.onUserInput(state, "小明：在吗\n小红：怎么了")
        assertEquals(ConversationState(turnCount = 1), next)
    }

    // ===== onModelReply =====

    @Test
    fun `模型回复落地后记录结论与话术`() {
        val state = ConversationState(topicSummary = "她划清朋友边界", turnCount = 1)
        val next = tracker.onModelReply(
            state = state,
            topicSummary = "",
            conclusion = "尊重她的边界，别再推进",
            reply = "好的，我尊重你的想法，我们还是做朋友吧",
        )
        assertEquals("尊重她的边界，别再推进", next.conclusionGiven)
        assertEquals("好的，我尊重你的想法，我们还是做朋友吧", next.lastReplyText)
        assertTrue(next.replyAlreadyGiven)
    }

    @Test
    fun `本轮未给话术时不覆盖旧话术但保留已给标记`() {
        val state = ConversationState(
            topicSummary = "她划清朋友边界",
            lastReplyText = "旧话术",
            replyAlreadyGiven = true,
            turnCount = 2,
        )
        val next = tracker.onModelReply(state, "", "新结论", "")
        assertEquals("旧话术", next.lastReplyText)
        assertTrue(next.replyAlreadyGiven)
        assertEquals("新结论", next.conclusionGiven)
    }

    @Test
    fun `新话题时更新话题摘要`() {
        val next = tracker.onModelReply(
            state = ConversationState(turnCount = 1),
            topicSummary = "她划清朋友边界",
            conclusion = "尊重边界",
            reply = "",
        )
        assertEquals("她划清朋友边界", next.topicSummary)
        assertFalse(next.replyAlreadyGiven)
    }

    // ===== buildStatePrefix =====

    @Test
    fun `无进行中话题时前缀为空`() {
        assertEquals("", tracker.buildStatePrefix(ConversationState.EMPTY))
    }

    @Test
    fun `有话题时前缀包含话题结论话术轮次与禁复读规则`() {
        val state = ConversationState(
            topicSummary = "她划清朋友边界",
            conclusionGiven = "尊重边界",
            lastReplyText = "好的，理解",
            replyAlreadyGiven = true,
            turnCount = 1,
        )
        val prefix = tracker.buildStatePrefix(state)
        assertTrue(prefix.contains("她划清朋友边界"))
        assertTrue(prefix.contains("尊重边界"))
        assertTrue(prefix.contains("好的，理解"))
        assertTrue(prefix.contains("第 2 轮"))
        assertTrue(prefix.contains("禁止重复"))
    }

    @Test
    fun `未给过话术时前缀显示无`() {
        val state = ConversationState(topicSummary = "刚认识怎么开场", turnCount = 0)
        val prefix = tracker.buildStatePrefix(state)
        assertTrue(prefix.contains("已给话术：无"))
        assertTrue(prefix.contains("第 1 轮"))
    }

    // ===== isSameTopic =====

    @Test
    fun `无进行中话题时任何输入都不算同题`() {
        assertFalse(tracker.isSameTopic(ConversationState.EMPTY, "那我还该追她吗"))
    }

    @Test
    fun `追问指代开场判为同题`() {
        val state = ConversationState(topicSummary = "她划清朋友边界")
        assertTrue(tracker.isSameTopic(state, "那我还该追她吗"))
        assertTrue(tracker.isSameTopic(state, "所以我现在该怎么办"))
        assertTrue(tracker.isSameTopic(state, "要不要直接问她"))
    }

    @Test
    fun `聊天记录粘贴判为新题`() {
        val state = ConversationState(topicSummary = "她划清朋友边界")
        assertFalse(tracker.isSameTopic(state, "小明：在吗\n小红：怎么了\n小明：周末出来玩"))
    }

    @Test
    fun `共享关键词判为同题`() {
        val state = ConversationState(topicSummary = "相亲对象三天没回消息")
        assertTrue(tracker.isSameTopic(state, "那个相亲对象终于回了"))
    }

    @Test
    fun `无明显信号时默认同题`() {
        val state = ConversationState(topicSummary = "她划清朋友边界")
        // 无追问词、无共享关键词：默认同题（连续对话里追问远多于硬切题）
        assertTrue(tracker.isSameTopic(state, "嗯"))
    }
}
