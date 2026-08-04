package com.wenyan.app.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SessionTitle 纯函数测试（v1.2.1）：
 * 抽屉标题三级回退 / 素材提取 / prompt 构建 / 输出清洗。
 */
class SessionTitleTest {

    // ===== resolveSessionTitle：三级回退 =====

    @Test
    fun `stored title wins over first message`() {
        val title = SessionTitle.resolveSessionTitle("她拒绝了我的邀约", "她说我们只是朋友")
        assertEquals("她拒绝了我的邀约", title)
    }

    @Test
    fun `stored title truncated to 30 chars`() {
        val long = "长标题".repeat(20) // 40 chars
        val title = SessionTitle.resolveSessionTitle(long, "首条消息")
        assertEquals(long.take(30), title)
    }

    @Test
    fun `falls back to first user message when stored is blank`() {
        assertEquals("她说我们只是朋友", SessionTitle.resolveSessionTitle("", "她说我们只是朋友"))
        assertEquals("她说我们只是朋友", SessionTitle.resolveSessionTitle(null, "她说我们只是朋友"))
    }

    @Test
    fun `whitespace stored title falls back`() {
        assertEquals("首条消息", SessionTitle.resolveSessionTitle("   ", "首条消息"))
    }

    @Test
    fun `first message normalized and truncated to 30`() {
        val first = "第一句  第二句".replace("  ", "\n")
        val title = SessionTitle.resolveSessionTitle("", first)
        assertEquals("第一句 第二句".take(30), title)
    }

    @Test
    fun `both empty yields new session placeholder`() {
        assertEquals("新会话", SessionTitle.resolveSessionTitle("", ""))
        assertEquals("新会话", SessionTitle.resolveSessionTitle(null, null))
        assertEquals("新会话", SessionTitle.resolveSessionTitle("", "   "))
    }

    // ===== buildTitleMaterial：素材提取 =====

    @Test
    fun `material collapses whitespace and truncates`() {
        val user = "第一行\n第二行 " + "x".repeat(40)
        val reply = "回复内容 " + "y".repeat(60)
        val (userLine, replyLine) = SessionTitle.buildTitleMaterial(user, reply)
        // "第一行 第二行 " = 8 字符 + 22 个 x = 30
        assertEquals("第一行 第二行 ${"x".repeat(22)}", userLine)
        assertEquals(30, userLine.length)
        assertEquals(40, replyLine.length)
        assertTrue(replyLine.startsWith("回复内容 "))
    }

    @Test
    fun `material keeps short texts intact`() {
        val (userLine, replyLine) = SessionTitle.buildTitleMaterial("她说我们只是朋友", "先理解她的边界")
        assertEquals("她说我们只是朋友", userLine)
        assertEquals("先理解她的边界", replyLine)
    }

    @Test
    fun `material with blank reply yields empty reply line`() {
        val (userLine, replyLine) = SessionTitle.buildTitleMaterial("你好", "")
        assertEquals("你好", userLine)
        assertTrue(replyLine.isBlank())
    }

    // ===== buildTitlePrompt：prompt 构建 =====

    @Test
    fun `prompt contains constraints and both materials`() {
        val prompt = SessionTitle.buildTitlePrompt("她说我们只是朋友", "先理解她的边界")
        assertTrue(prompt.contains("不超过12个字"))
        assertTrue(prompt.contains("用户说：她说我们只是朋友"))
        assertTrue(prompt.contains("温言回：先理解她的边界"))
        assertTrue(prompt.contains("标题："))
    }

    @Test
    fun `prompt omits reply line when reply blank`() {
        val prompt = SessionTitle.buildTitlePrompt("你好", "")
        assertTrue(prompt.contains("用户说：你好"))
        assertFalse(prompt.contains("温言回"))
    }

    // ===== sanitizeTitle：输出清洗 =====

    @Test
    fun `sanitize strips punctuation`() {
        assertEquals("她拒绝了我的邀约", SessionTitle.sanitizeTitle("她拒绝了我的邀约！"))
        assertEquals("如何应对冷暴力", SessionTitle.sanitizeTitle("如何应对冷暴力？"))
    }

    @Test
    fun `sanitize strips emoji and symbols`() {
        assertEquals("深夜聊天", SessionTitle.sanitizeTitle("深夜聊天😅"))
    }

    @Test
    fun `sanitize strips title prefix`() {
        assertEquals("她拒绝了我的邀约", SessionTitle.sanitizeTitle("标题：她拒绝了我的邀约"))
        assertEquals("她拒绝了我的邀约", SessionTitle.sanitizeTitle("标题:她拒绝了我的邀约"))
    }

    @Test
    fun `sanitize truncates to 12 chars`() {
        val raw = "一二三四五六七八九十一二三四五" // 15 chars
        assertEquals("一二三四五六七八九十一二", SessionTitle.sanitizeTitle(raw))
        assertEquals(12, SessionTitle.sanitizeTitle(raw).length)
    }

    @Test
    fun `sanitize blank input yields empty`() {
        assertEquals("", SessionTitle.sanitizeTitle(""))
        assertEquals("", SessionTitle.sanitizeTitle("   "))
        assertEquals("", SessionTitle.sanitizeTitle("！？😅"))
    }
}
