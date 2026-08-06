package com.wenyan.app.prompt

import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * PromptBuilder 三层拼装（prompt-architecture.md，逐字采用）
 *
 * system 单条消息，用 【system-核心】/【system-档案】/【system-知识】 分隔：
 *   【system-核心】CorePrompt.text（§2.1 原文）
 *   【system-档案】问卷结构化 JSON（§2.2）
 *   【system-知识】知识文档注入（§2.3，由 KnowledgeEngine 提供）
 */
class PromptBuilder {

    /**
     * 拼装单条 system 消息（多供应商兼容，llm-contract §2.2）
     *
     * v1.6：全部输入统一走四段结构 JSON，固定追加 structuredOutput（schema v2）。
     */
    fun buildSystem(
        profile: ProfileEntity?,
        target: TargetEntity?,
        knowledgeInjection: String,
    ): String = buildString {
        append("【system-核心】\n").append(CorePrompt.text)
        append("\n\n【system-档案】\n").append(buildProfileJson(profile, target))
        // v1.7.2：档案确有记忆时追加记忆使用规则（保持 prompt 精简）
        if (!target?.note.isNullOrBlank()) {
            append("\n\n").append(CorePrompt.memoryRule)
        }
        if (knowledgeInjection.isNotBlank()) {
            append("\n\n【system-知识】\n").append(knowledgeInjection)
        }
        append("\n\n")
        append(CorePrompt.structuredOutput)
    }

    /**
     * system-档案 JSON（§2.2 模板，字段缺失标 null，不编造）
     */
    fun buildProfileJson(profile: ProfileEntity?, target: TargetEntity?): String {
        val me = JSONObject()
        me.put("mbti", profile?.mbti ?: JSONObject.NULL)
        me.put("score", profile?.score ?: JSONObject.NULL)
        me.put("strengths", profile?.strengths ?: "")
        me.put("weaknesses", profile?.weaknesses ?: "")

        val targetObj = JSONObject()
        targetObj.put("codeName", target?.codeName ?: "")
        targetObj.put("mbti", target?.mbti ?: JSONObject.NULL)
        targetObj.put("score", target?.score ?: JSONObject.NULL)
        targetObj.put("relationStatus", target?.relationStatus ?: "")
        targetObj.put("timeline", parseTimeline(target?.timeline))
        // v1.7.2：记忆正文（跨会话记忆；空串输出空串）
        targetObj.put("memory", target?.note ?: "")

        val root = JSONObject()
        root.put("me", me)
        root.put("target", targetObj)
        root.put("history", "")
        root.put("goal", "")
        val emotion = JSONObject()
        emotion.put("pain_point", "")
        emotion.put("intensity", JSONObject.NULL)
        emotion.put("urgent", false)
        root.put("emotion", emotion)
        return root.toString()
    }

    private fun parseTimeline(timeline: String?): JSONArray {
        if (timeline.isNullOrBlank()) return JSONArray()
        return try {
            JSONArray(timeline)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    // ===== user 消息模板（§3） =====

    /** §3.1 文本粘贴（完整聊天记录，走四段结构） */
    fun buildUserText(rawText: String): String = buildString {
        append("以下是用户粘贴的聊天记录，请按四段结构分析：\n")
        append("【聊天记录开始】\n").append(rawText).append("\n【聊天记录结束】")
    }

    /** §3.2 截图转述（通道 B） */
    fun buildUserTranscription(transcription: String): String = buildString {
        append("以下内容是 AI 从用户聊天截图中提取的文字（已尽量保留说话人、顺序、间隔，可能有误差）：\n")
        append("【截图转述开始】\n").append(transcription).append("\n【截图转述结束】\n")
        append("请基于以上内容按四段结构分析；无法确认的细节标注\"转述提示\"而非事实。")
    }

    /**
     * §3.3 简短输入（v1.6 轻量四段）：
     * 适用于用户随口一句话——"你好"、"他说讨厌我"、"这句怎么回" 这种没粘贴聊天记录的场景。
     * 让模型先做语境判断（转述/提问/倾诉），再决定回应方向；输出同一四段 JSON 结构，但内容精简。
     *
     * @param statePrefix 对话状态前缀（ConversationStateTracker.buildStatePrefix），同题追问时非空
     */
    fun buildUserReply(
        quote: String,
        context: String?,
        statePrefix: String = "",
    ): String = buildString {
        if (statePrefix.isNotBlank()) {
            append(statePrefix).append("\n\n")
        }
        append("用户发来的不是完整聊天记录，而是一句简短的输入（可能是心情倾诉、可能是想问\"这句怎么回\"、可能是在转述对方说过的话、也可能只是打招呼）。\n")
        append("用户输入：").append(quote)
        if (!context.isNullOrBlank()) {
            append("\n（可选）聊天上下文：").append(context)
        }
        append(
            """

请按轻量四段结构输出（同一 JSON Schema，但内容精简，不强行凑满）：
0. 先做语境判断，写入 input_kind：
   - user_question：用户自己在提问或倾诉（第一人称、说自己的事）。
   - relayed_quote：用户在转述对方/第三方说过的话（如"她说我们只是朋友"）——不是用户自己的立场。
   - greeting：纯打招呼。
   - uncertain：以上拿不准时选这个，宁可反问也不硬猜方向（仅方向性事实歧义；不得因"他/她"、称呼、错别字等表面措辞触发）。
1. empathy：1-2 句接住用户此刻的感受或处境，认可但不夸张。
   - 若是 relayed_quote：这里先解读对方那句话的意图和关系信号（如"她这句基本是在划清关系边界"），而不是共情用户。
   - 若是 uncertain：这里写清你为什么拿不准（一两个字就够，别长篇）。
2. reply（仅本轮用户需要一句可发送话术时才给，否则留空字符串）：
   - user_question 且用户在要话术：给一句可直接复制发送的成品话术——贴合用户处境，不要甩"你好呀～你最近怎么样？"这种通用模板。
   - relayed_quote 且适合回一句：给一句用户能发出去的回应话术，方向与你解读出的对方意图一致（对方划清边界就尊重边界，别再给"继续追"的话术）。
   - 用户在追问判断/要不要做（而非要话术）：reply 留空，把分析和建议写进 advice.core。
   - greeting：给一个温和的开场即可。
   - uncertain：给一句简短的确认问句（如"我先确认下——这是她对你说的，对吧？"），不是成品话术。
3. advice：core 必填（一句核心建议）；styles 至少 1 条（uncertain 或用户明确不要话术时留空数组）；tag/reasons 可空。
4. facts/actions：可空数组；reply_timing：一句话发送时机或注意（reply 为空或 uncertain 时留空字符串）。
5. citations 留空数组。safety_override=false。
        """.trimIndent(),
        )
    }
}
