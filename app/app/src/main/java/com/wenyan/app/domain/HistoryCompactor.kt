package com.wenyan.app.domain

import com.wenyan.app.llm.ChatHistoryMessage
import com.wenyan.app.llm.estimateTextTokens

/**
 * v1.9.1 历史消息预算选择式压缩（纯逻辑，双端共享，JVM 可测）。
 *
 * 策略（替代 v1.8.x 的「整轮成对丢弃」）：
 * 1. 阶段一：估算超预算时，对「非工作集」的早期消息逐条裁剪保头
 *    （每条最多保留 [EARLY_MSG_CHAR_BUDGET] 字 + 截断标记），工作集 = 末尾 N 轮保持完整；
 * 2. 阶段二：仍超预算时从最早整条成对丢弃（保持轮次完整，user+assistant 一起丢）。
 *
 * 相比纯丢弃：被裁剪消息的关键开头信息仍留在上下文里，模型能感知话题曾发生。
 */
object HistoryCompactor {

    /** 历史消息 token 上限（粗估，字符数/4），与手机/桌面 HISTORY_TOKEN_LIMIT 对齐 */
    const val DEFAULT_MAX_TOKENS = 24_000

    /** 工作集轮数：末尾 N 轮（user+assistant）保持完整不裁剪 */
    const val WORKING_SET_ROUNDS = 6

    /** 早期消息单条保留字符预算（超出截断 + 标记） */
    const val EARLY_MSG_CHAR_BUDGET = 200

    /** 截断标记（提醒模型信息不完整） */
    const val TRUNC_MARK = "…[已省略]"

    /** 粗估 token 数（M6: CJK 1 字≈1 token、ASCII 4 字≈1 token，保守上界） */
    fun estimatedTokens(messages: List<ChatHistoryMessage>): Int =
        messages.sumOf { estimateTextTokens(it.content) }

    /**
     * 预算选择式压缩。
     *
     * @return Pair(处理后的消息列表, 是否发生了整条丢弃截断)
     */
    fun compact(
        messages: List<ChatHistoryMessage>,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
    ): Pair<List<ChatHistoryMessage>, Boolean> {
        if (messages.size <= 2) return messages to false
        val result = messages.toMutableList()

        // 阶段一：早期消息裁剪保头（工作集完整）
        if (estimatedTokens(result) > maxTokens) {
            val keepTail = (WORKING_SET_ROUNDS * 2).coerceAtMost(result.size - 1)
            for (i in 0 until (result.size - keepTail)) {
                val msg = result[i]
                if (msg.content.length > EARLY_MSG_CHAR_BUDGET) {
                    result[i] = msg.copy(content = msg.content.take(EARLY_MSG_CHAR_BUDGET) + TRUNC_MARK)
                }
            }
        }

        // 阶段二：仍超预算从最早整条成对丢弃
        var truncated = false
        while (estimatedTokens(result) > maxTokens && result.size > 2) {
            result.removeAt(0)
            // 尽量成对丢弃（user + assistant），保持轮次完整
            if (result.size > 2) result.removeAt(0)
            truncated = true
        }
        return result to truncated
    }
}
