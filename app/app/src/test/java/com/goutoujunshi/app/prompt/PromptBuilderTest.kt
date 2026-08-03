package com.goutoujunshi.app.prompt

import com.goutoujunshi.app.data.db.ProfileEntity
import com.goutoujunshi.app.data.db.TargetEntity
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
    fun `user reply asks for copyable reply first`() {
        val user = builder.buildUserReply("周末有空吗", null)
        assertTrue(user.contains("待回复消息：周末有空吗"))
        assertTrue(user.contains("请先给一条可复制成品话术"))
        assertTrue(user.contains("发送时机、主要代价和积极/含糊/不回应的后续"))
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
