package com.wenyan.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * freetext 话术拆分单测（v1.3.1，FreetextSplitter.split）。
 *
 * 覆盖：引导词命中/回退、引号提取、首行回退、边界 trim、多行正文。
 */
class FreetextSplitTest {

    @Test
    fun `无引导词 - 整体回退为正文`() {
        val s = FreetextSplitter.split("她这次真的生气了，我觉得是我的问题。")
        assertEquals("", s.reply)
        assertEquals("她这次真的生气了，我觉得是我的问题。", s.body)
    }

    @Test
    fun `话术在开头 - 正文为空`() {
        val s = FreetextSplitter.split("可以发：晚上我先不打了，想自己待会儿。")
        assertEquals("晚上我先不打了，想自己待会儿。", s.reply)
        assertEquals("", s.body)
    }

    @Test
    fun `引号完整提取 - 话术与正文分离`() {
        val raw = "她退了一步把主动权交给你。\n可以直接发「我不是不想理你，是最近脑子有点乱。给我两天缓一缓。」\n这样既定了心，又不算冷落她。"
        val s = FreetextSplitter.split(raw)
        assertEquals("我不是不想理你，是最近脑子有点乱。给我两天缓一缓。", s.reply)
        assertEquals("她退了一步把主动权交给你。\n这样既定了心，又不算冷落她。", s.body)
    }

    @Test
    fun `无引号 - 取引导词后首行`() {
        val raw = "先接住情绪。\n可以回：谢谢你的坦诚，我知道了。\n之后别再提这事。"
        val s = FreetextSplitter.split(raw)
        assertEquals("谢谢你的坦诚，我知道了。", s.reply)
        assertEquals("先接住情绪。\n之后别再提这事。", s.body)
    }

    @Test
    fun `多处引导词 - 只取首个`() {
        val raw = "这样回：“可以再给我一点时间吗”。另外也可以直接说：今天先这样。"
        val s = FreetextSplitter.split(raw)
        assertEquals("可以再给我一点时间吗", s.reply)
        // 第二个引导词留在正文
        assertEquals("另外也可以直接说：今天先这样。", s.body)
    }

    @Test
    fun `引号未闭合 - 回退首行并剥前导引号`() {
        val raw = "可以发：“晚上我们聊聊吧"
        val s = FreetextSplitter.split(raw)
        assertEquals("晚上我们聊聊吧", s.reply)
        assertEquals("", s.body)
    }

    @Test
    fun `提取为空 - 回退纯文本`() {
        val s = FreetextSplitter.split("可以发：")
        assertEquals("", s.reply)
        assertEquals("可以发：", s.body)
    }

    @Test
    fun `全角冒号句号与无标点 - 均命中`() {
        assertEquals("回柳州再说。", FreetextSplitter.split("可以发：回柳州再说。").reply)
        assertEquals("回柳州再说。", FreetextSplitter.split("可以发。回柳州再说。").reply)
        assertEquals("回柳州再说", FreetextSplitter.split("可以发 回柳州再说").reply)
    }

    @Test
    fun `前后空行 - 自动 trim`() {
        val s = FreetextSplitter.split("\n\n先说结论。\n\n可以发：周末见一面吧。\n\n")
        assertEquals("周末见一面吧。", s.reply)
        assertEquals("先说结论。", s.body)
    }

    @Test
    fun `多行正文 - 保留换行`() {
        val raw = "第一行解释。\n第二行解释。\n可以回「收到，我知道了。」"
        val s = FreetextSplitter.split(raw)
        assertEquals("收到，我知道了。", s.reply)
        assertEquals("第一行解释。\n第二行解释。", s.body)
    }
}
