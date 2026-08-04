package com.goutoujunshi.app.prompt

import com.goutoujunshi.app.data.db.ProfileEntity
import com.goutoujunshi.app.data.db.TargetEntity
import com.goutoujunshi.app.llm.ResponseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * PromptBuilder 测试（prompt-architecture 三层拼装 + 五步法结构）
 */
class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `system contains three layers separated by markers`() {
        val system = builder.buildSystem(null, null, "【知识文档 #1】《x.md》\n内容\n【知识文档结束 #1】")
        assertTrue(system.contains("【system-核心】"))
        assertTrue(system.contains("【system-档案】"))
        assertTrue(system.contains("【system-知识】"))
        // 核心模板原文关键词
        assertTrue(system.contains("你是\"狗头军师\""))
        assertTrue(system.contains("先接住情绪，再分清事实，最后给能执行的选择"))
    }

    @Test
    fun `profile json null skeleton when no profile`() {
        val json = builder.buildProfileJson(null, null)
        val root = JSONObject(json)
        assertEquals(JSONObject.NULL, root.getJSONObject("me").get("mbti"))
        assertEquals(JSONObject.NULL, root.getJSONObject("target").get("mbti"))
        assertEquals("", root.getJSONObject("target").getString("codeName"))
    }

    @Test
    fun `profile json filled from entities`() {
        val profile = ProfileEntity(mbti = "INTJ", score = 72, strengths = "冷静", weaknesses = "慢热")
        val target = TargetEntity(codeName = "小A", mbti = "ENFP", score = 88, relationStatus = "暧昧中")
        val root = JSONObject(builder.buildProfileJson(profile, target))
        assertEquals("INTJ", root.getJSONObject("me").getString("mbti"))
        assertEquals(72, root.getJSONObject("me").getInt("score"))
        assertEquals("小A", root.getJSONObject("target").getString("codeName"))
        assertEquals("ENFP", root.getJSONObject("target").getString("mbti"))
    }

    @Test
    fun `user text wraps raw chat in markers`() {
        val user = builder.buildUserText("你好\n在吗")
        assertTrue(user.startsWith("以下是用户粘贴的聊天记录，请按五步法分析："))
        assertTrue(user.contains("【聊天记录开始】\n你好\n在吗\n【聊天记录结束】"))
    }

    @Test
    fun `user transcription marks uncertainty`() {
        val user = builder.buildUserTranscription("转述内容")
        assertTrue(user.contains("【截图转述开始】\n转述内容\n【截图转述结束】"))
        assertTrue(user.contains("转述提示"))
    }

    @Test
    fun `user reply structured variant keeps json contract`() {
        // structured 变体（v1.2 契约保留）：emotion 单步 + reply-on-demand + reply_timing
        val user = builder.buildUserReply("周末有空吗", null, ResponseMode.STRUCTURED)
        assertTrue(user.contains("用户输入：周末有空吗"))
        assertTrue(user.contains("input_kind"))
        assertTrue(user.contains("reply_timing"))
        assertTrue(user.contains("发送时机"))
        // 仍保留"这句怎么回"的场景识别
        assertTrue(user.contains("这句怎么回"))
    }

    @Test
    fun `user reply freetext variant is natural language first`() {
        // v1.3 freetext 变体：自由文本直出，不提 JSON Schema，带禁复读规则
        val user = builder.buildUserReply("周末有空吗", null, ResponseMode.FREETEXT)
        assertTrue(user.contains("用户输入：周末有空吗"))
        assertTrue(user.contains("自由文本，不输出 JSON"))
        assertTrue(user.contains("严格不重复"))
        // freetext 不应出现 structured 契约字段
        assertTrue(!user.contains("input_kind"))
        assertTrue(!user.contains("reply_timing"))
    }

    @Test
    fun `user reply state prefix injected when provided`() {
        val prefix = "【对话状态】当前话题：她划清朋友边界；已给话术：无；这是同一话题的第 2 轮。\n规则：禁止重复。"
        val user = builder.buildUserReply("那我还该追她吗", null, ResponseMode.FREETEXT, prefix)
        assertTrue(user.startsWith("【对话状态】"))
        assertTrue(user.contains("她划清朋友边界"))
        assertTrue(user.contains("那我还该追她吗"))
    }

    @Test
    fun `buildSystem appends mode specific output block`() {
        val freetext = builder.buildSystem(null, null, "", ResponseMode.FREETEXT)
        val structured = builder.buildSystem(null, null, "", ResponseMode.STRUCTURED)
        // 两种模式三层骨架一致，输出要求段不同
        assertTrue(freetext.contains("【system-核心】") && structured.contains("【system-核心】"))
        assertTrue(freetext != structured)
    }

    @Test
    fun `five step keys present in core prompt`() {
        // 五步法结构（AC-04）：核心模板含五个步骤
        val core = CorePrompt.text
        assertTrue(core.contains("情绪落地"))
        assertTrue(core.contains("事实拆分"))
        assertTrue(core.contains("利益判断"))
        assertTrue(core.contains("明确建议"))
        assertTrue(core.contains("行动收束"))
    }

    @Test
    fun `core prompt forbids emoji`() {
        // P0 规则：prompt 中无 emoji 字符
        val hasEmoji = CorePrompt.text.codePoints().anyMatch { it in 0x1F300..0x1FAFF }
        assertTrue("core prompt contains emoji", !hasEmoji)
    }
}
