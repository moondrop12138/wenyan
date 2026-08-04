package com.goutoujunshi.app.domain

/**
 * 对话状态机（v1.3）。
 *
 * 职责：
 * 1. 本地启发式初判本轮输入是否与进行中的话题同题（连续追问）；
 * 2. 驱动状态流转——同题追问 turnCount+1，新话题重置；
 * 3. 生成注入 prompt 的状态前缀，让模型知道"已给过什么"，避免重复。
 *
 * 话题判定以"模型确认为主、本地为辅"：本地只做高置信度初判喂给 prompt，
 * 模型在回复时如判断话题已切换可纠正（本轮 MVP 先靠本地 + 历史上下文，模型纠正
 * 通过 prompt 规则引导，不回写状态——保持单向数据流简单可靠）。
 *
 * 纯逻辑，无 Android 依赖，JVM 可测。
 */
class ConversationStateTracker {

    /**
     * 基于当前状态与用户新输入，推进状态。
     *
     * @param state 当前会话状态
     * @param userInput 本轮用户输入原文
     * @return 推进后的新状态（同题 turnCount+1；新话题则等待模型产出后再由 [onModelReply] 落地）
     */
    fun onUserInput(state: ConversationState, userInput: String): ConversationState {
        if (!state.hasActiveTopic) return state
        return if (isSameTopic(state, userInput)) {
            state.copy(turnCount = state.turnCount + 1)
        } else {
            // 本地判为新话题：清空进行中的话题痕迹，等新结论落地
            ConversationState(turnCount = 1)
        }
    }

    /**
     * 模型回复落地后更新状态（记录结论与话术，供下轮查重）。
     *
     * @param conclusion 本轮结论（structured 取 advice 段；freetext 取首句摘要）
     * @param reply 本轮给出的可发送话术（无则空串，表示本轮没给话术）
     * @param topicSummary 本轮话题摘要（新话题时由调用方从输入/结论提炼）
     */
    fun onModelReply(
        state: ConversationState,
        topicSummary: String,
        conclusion: String,
        reply: String,
    ): ConversationState {
        val gaveReply = reply.isNotBlank()
        return state.copy(
            topicSummary = topicSummary.ifBlank { state.topicSummary },
            conclusionGiven = conclusion.ifBlank { state.conclusionGiven },
            lastReplyText = if (gaveReply) reply else state.lastReplyText,
            replyAlreadyGiven = state.replyAlreadyGiven || gaveReply,
        )
    }

    /**
     * 生成注入 user prompt 的状态前缀（仅在有进行中话题时非空）。
     * 告诉模型：当前话题、已下结论、已给话术、第几轮——禁止重复。
     */
    fun buildStatePrefix(state: ConversationState): String {
        if (!state.hasActiveTopic) return ""
        return buildString {
            append("【对话状态】当前话题：").append(state.topicSummary)
            if (state.conclusionGiven.isNotBlank()) {
                append("；已给结论：").append(state.conclusionGiven)
            }
            append("；已给话术：")
            append(if (state.lastReplyText.isNotBlank()) state.lastReplyText else "无")
            append("；这是同一话题的第 ").append(state.turnCount + 1).append(" 轮。\n")
            append("规则：同一话题的连续追问，禁止重复上面已给的结论和话术——")
            append("要么推进到新角度，要么直接回答追问本身；用户在要判断/建议而不是话术时，不要给可发送话术。")
        }
    }

    /**
     * 本地启发式：判断本轮输入是否延续进行中的话题。
     *
     * 高置信度信号（命中即同题）：
     * - 追问/指代词：那、还有、然后呢、所以、该、要不要、是不是、怎么办
     * - 与话题摘要共享关键词（"她/他/我们"等关系代词延续）
     *
     * 明确新话题信号（命中即新题）：完整的另一段聊天记录粘贴、明显的换题开场。
     * 拿不准时默认同题（连续对话里追问远多于硬切题），交给模型在 prompt 里纠正。
     */
    fun isSameTopic(state: ConversationState, userInput: String): Boolean {
        if (!state.hasActiveTopic) return false
        val t = userInput.trim()
        if (t.isEmpty()) return true
        // 完整聊天记录粘贴 = 新分析任务，换题
        if (t.contains('\n') && t.contains("：")) return false
        // 追问/指代开场 = 强同题信号
        if (FOLLOW_UP_PATTERN.containsMatchIn(t)) return true
        // 与话题摘要共享实义词 = 同题
        if (sharesKeyword(state.topicSummary, t)) return true
        // 默认同题（追问多于硬切题），模型可纠正
        return true
    }

    private fun sharesKeyword(topic: String, input: String): Boolean {
        val topicTokens = tokenize(topic)
        if (topicTokens.isEmpty()) return false
        val inputClean = tokenize(input).toSet()
        return topicTokens.any { it in inputClean }
    }

    /** 粗粒度分词：提取 2 字以上中文/英文实义片段，去停用代词 */
    private fun tokenize(text: String): List<String> {
        val cleaned = text.replace(Regex("[\\s，。！？、,.!?；;：:（）()《》「」\"'“”]"), " ")
        return cleaned.split(" ")
            .filter { it.length >= 2 }
            .filter { it !in STOP_TOKENS }
    }

    private companion object {
        val FOLLOW_UP_PATTERN = Regex(
            "^(那|然后|所以|那我还|我还|要不要|该不该|是不是|怎么办|接下来|后来|对了|那现在)"
        )
        val STOP_TOKENS = setOf(
            "我们", "我俩", "之间", "现在", "这样", "怎么", "什么", "是不是", "知道",
        )
    }
}
