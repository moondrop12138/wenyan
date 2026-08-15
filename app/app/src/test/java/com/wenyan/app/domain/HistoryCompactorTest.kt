package com.wenyan.app.domain

import com.wenyan.app.llm.ChatHistoryMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.1 HistoryCompactor 预算选择式压缩测试：
 * 工作集保留 / 早期消息裁剪保头 / 成对丢弃 / 预算边界。
 */
class HistoryCompactorTest {

    private fun msgs(n: Int, len: Int = 50): List<ChatHistoryMessage> =
        (0 until n).map { i ->
            ChatHistoryMessage(
                role = if (i % 2 == 0) "user" else "assistant",
                content = "消息${i}：" + "字".repeat(len),
            )
        }

    @Test
    fun `within budget returns unchanged without truncation`() {
        val list = msgs(4, len = 100)
        val (out, truncated) = HistoryCompactor.compact(list)
        assertEquals(list, out)
        assertFalse(truncated)
    }

    @Test
    fun `two or fewer messages never truncated`() {
        val list = msgs(2, len = 100_000)
        val (out, truncated) = HistoryCompactor.compact(list)
        assertEquals(list, out)
        assertFalse(truncated)
    }

    @Test
    fun `over budget trims early messages keeping head`() {
        // 20 条 × 1500 字 → 估算 ~30k token > 24k 预算（M6: CJK 1 字≈1 token），触发阶段一；裁剪后 < 预算不触发阶段二
        val list = msgs(20, len = 1500)
        val originalLengths = list.map { it.content.length }
        val (out, truncated) = HistoryCompactor.compact(list, maxTokens = 24_000)
        // 工作集末尾 6 轮（12 条）保持完整（长度与原文一致）
        val tail = out.takeLast(12)
        assertTrue(
            tail.indices.all { i ->
                tail[i].content.length == originalLengths[originalLengths.size - 12 + i]
            },
        )
        // 早期消息被裁剪到预算内 + 截断标记
        val early = out.take(out.size - 12)
        assertTrue(early.isNotEmpty())
        assertTrue(
            early.all {
                it.content.length <= HistoryCompactor.EARLY_MSG_CHAR_BUDGET + HistoryCompactor.TRUNC_MARK.length
            },
        )
        assertTrue(early.any { it.content.endsWith(HistoryCompactor.TRUNC_MARK) })
    }

    @Test
    fun `extreme budget drops oldest rounds pairwise`() {
        val list = msgs(30, len = 10000) // 30*10000/4 = 75000 token
        val (out, truncated) = HistoryCompactor.compact(list, maxTokens = 24_000)
        assertTrue(truncated)
        assertTrue(HistoryCompactor.estimatedTokens(out) <= 24_000)
        // 仍保留工作集轮次（至少 2 条，末尾 12 条完整）
        assertTrue(out.size >= 2)
        assertEquals(list.takeLast(out.size), out)
    }

    @Test
    fun `compact is deterministic and idempotent on second pass`() {
        val list = msgs(25, len = 5000)
        val (once, _) = HistoryCompactor.compact(list)
        val (twice, _) = HistoryCompactor.compact(once)
        assertEquals(once, twice)
    }
}
