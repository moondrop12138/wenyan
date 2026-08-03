package com.goutoujunshi.app.prompt

import com.goutoujunshi.app.data.db.ProfileEntity
import com.goutoujunshi.app.data.db.TargetEntity
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
     */
    fun buildSystem(
        profile: ProfileEntity?,
        target: TargetEntity?,
        knowledgeInjection: String,
    ): String = buildString {
        append("【system-核心】\n").append(CorePrompt.text)
        append("\n\n【system-档案】\n").append(buildProfileJson(profile, target))
        if (knowledgeInjection.isNotBlank()) {
            append("\n\n【system-知识】\n").append(knowledgeInjection)
        }
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

    /** §3.1 文本粘贴 */
    fun buildUserText(rawText: String): String = buildString {
        append("以下是用户粘贴的聊天记录，请按五步法分析：\n")
        append("【聊天记录开始】\n").append(rawText).append("\n【聊天记录结束】")
    }

    /** §3.2 截图转述（通道 B） */
    fun buildUserTranscription(transcription: String): String = buildString {
        append("以下内容是 AI 从用户聊天截图中提取的文字（已尽量保留说话人、顺序、间隔，可能有误差）：\n")
        append("【截图转述开始】\n").append(transcription).append("\n【截图转述结束】\n")
        append("请基于以上内容按五步法分析；无法确认的细节标注\"转述提示\"而非事实。")
    }

    /** §3.3 "这句怎么回" */
    fun buildUserReply(quote: String, context: String?): String = buildString {
        append("用户只问这一句怎么回，不要求完整分析。\n")
        append("待回复消息：").append(quote)
        if (!context.isNullOrBlank()) {
            append("\n（可选）聊天上下文：").append(context)
        }
        append("\n请先给一条可复制成品话术，再给发送时机、主要代价和积极/含糊/不回应的后续。")
    }
}
