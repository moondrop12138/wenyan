package com.wenyan.app.prompt

import com.wenyan.app.data.db.ProfileEntity
import com.wenyan.app.data.db.TargetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * PromptBuilder 测试（prompt-architecture 三层拼装 + v1.6 四段结构）
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
        assertTrue(system.contains("你是\"温言\""))
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
        assertTrue(user.startsWith("以下是用户粘贴的聊天记录，请按四段结构分析："))
        assertTrue(user.contains("【聊天记录开始】\n你好\n在吗\n【聊天记录结束】"))
    }

    @Test
    fun `user transcription marks uncertainty`() {
        val user = builder.buildUserTranscription("转述内容")
        assertTrue(user.contains("【截图转述开始】\n转述内容\n【截图转述结束】"))
        assertTrue(user.contains("转述提示"))
    }

    @Test
    fun `user reply is light four-section json contract`() {
        // v1.6 轻量四段变体：input_kind 判断 + empathy + reply-on-demand + advice 精简
        val user = builder.buildUserReply("周末有空吗", null)
        assertTrue(user.contains("用户输入：周末有空吗"))
        assertTrue(user.contains("input_kind"))
        assertTrue(user.contains("轻量四段"))
        assertTrue(user.contains("advice"))
        assertTrue(user.contains("这句怎么回"))
        // 不应再有五步法 steps 契约字眼
        assertTrue(!user.contains("steps 数组"))
    }

    @Test
    fun `user reply state prefix injected when provided`() {
        val prefix = "【对话状态】当前话题：她划清朋友边界；已给话术：无；这是同一话题的第 2 轮。\n规则：禁止重复。"
        val user = builder.buildUserReply("那我还该追她吗", null, prefix)
        assertTrue(user.startsWith("【对话状态】"))
        assertTrue(user.contains("她划清朋友边界"))
        assertTrue(user.contains("那我还该追她吗"))
    }

    @Test
    fun `buildSystem always appends structured output block`() {
        val system = builder.buildSystem(null, null, "")
        assertTrue(system.contains("【system-核心】"))
        // v1.6 固定内嵌四段 schema：empathy / styles / actions
        assertTrue(system.contains("\"empathy\""))
        assertTrue(system.contains("\"styles\""))
        assertTrue(system.contains("\"actions\""))
        assertTrue(system.contains("schema_version"))
    }

    @Test
    fun `freetext output removed`() {
        // v1.6 删除 freetext 输出要求
        val core = CorePrompt.text
        assertTrue(!core.contains("自由对话"))
        // 无独立 freetextOutput 常量
        assertTrue(!CorePrompt::class.java.methods.any { it.name == "getFreetextOutput" })
    }

    @Test
    fun `four section structure present in core prompt`() {
        val core = CorePrompt.text
        assertTrue(core.contains("接住你"))
        assertTrue(core.contains("先分清事实"))
        assertTrue(core.contains("军师建议"))
        assertTrue(core.contains("现在可以做什么"))
        // 三档风格惯例引用知识库
        assertTrue(core.contains("00-导读与使用分级"))
        // 原五步法标题不再出现
        assertTrue(!core.contains("五个步骤"))
        assertTrue(!core.contains("利益判断："))
    }

    @Test
    fun `core prompt forbids emoji`() {
        // P0 规则：prompt 中无 emoji 字符
        val hasEmoji = CorePrompt.text.codePoints().anyMatch { it in 0x1F300..0x1FAFF }
        assertTrue("core prompt contains emoji", !hasEmoji)
    }

    @Test
    fun `styles are judged by model not mandatory`() {
        // v1.9.2 场景判断制：styles 由模型自主决定给或留空，且 reply 与 styles 一致性收紧
        val output = CorePrompt.structuredOutput
        assertTrue(output.contains("styles 留空数组"))
        assertTrue(output.contains("reply 必须同时留空字符串"))
        assertTrue(output.contains("用户明确要\"这句怎么回/怎么发给对方\""))
        // 旧"必填 reply / styles 至少 1 条"的强制措辞已移除
        assertTrue(!output.contains("styles 至少 1 条"))
        assertTrue(!output.contains("styles 给满 3 条"))
    }
}
