package com.wenyan.app.prompt

import com.wenyan.app.data.db.TargetEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 PromptBuilder 记忆注入测试：
 * buildProfileJson 的 target.memory 字段（=note，空串输出空串）+ CorePrompt.memoryRule 存在性与按需追加。
 */
class PromptBuilderMemoryTest {

    private val builder = PromptBuilder()

    @Test
    fun `profile json includes memory field from note`() {
        val target = TargetEntity(codeName = "小A", note = "喜欢猫，讨厌香菜")
        val root = JSONObject(builder.buildProfileJson(null, target))
        assertEquals("喜欢猫，讨厌香菜", root.getJSONObject("target").getString("memory"))
    }

    @Test
    fun `profile json memory empty string when note blank`() {
        val target = TargetEntity(codeName = "小A")
        val root = JSONObject(builder.buildProfileJson(null, target))
        assertEquals("", root.getJSONObject("target").getString("memory"))
    }

    @Test
    fun `profile json memory empty string when target null`() {
        val root = JSONObject(builder.buildProfileJson(null, null))
        assertEquals("", root.getJSONObject("target").getString("memory"))
    }

    @Test
    fun `system appends memory rule when note present`() {
        val target = TargetEntity(codeName = "小A", note = "喜欢猫")
        val system = builder.buildSystem(null, target, "")
        assertTrue(system.contains("记忆使用规则"))
        assertTrue(system.contains("不得与已记住信息矛盾"))
    }

    @Test
    fun `system omits memory rule when note blank`() {
        val system = builder.buildSystem(null, null, "")
        assertTrue(!system.contains("记忆使用规则"))
    }

    @Test
    fun `memoryRule constant exists and carries usage rules`() {
        assertTrue(CorePrompt.memoryRule.isNotBlank())
        assertTrue(CorePrompt.memoryRule.contains("不得与已记住信息矛盾"))
        assertTrue(CorePrompt.memoryRule.contains("以新信息为准"))
    }

    // ===== QA 边界补充（独立核验，2026-08-06） =====

    @Test
    fun `system appends memory rule and keeps knowledge section`() {
        val target = TargetEntity(codeName = "小A", note = "喜欢猫")
        val system = builder.buildSystem(null, target, "知识文档A")
        assertTrue(system.contains("记忆使用规则"))
        assertTrue(system.contains("【system-知识】"))
        assertTrue(system.contains("知识文档A"))
    }

    @Test
    fun `memory field always present when target null with non-null profile`() {
        val profile = com.wenyan.app.data.db.ProfileEntity(mbti = "INTJ")
        val root = JSONObject(builder.buildProfileJson(profile, null))
        assertEquals("", root.getJSONObject("target").getString("memory"))
        assertEquals("INTJ", root.getJSONObject("me").getString("mbti"))
    }

    @Test
    fun `buildSystem signature compatible with note-only target`() {
        // buildSystem(profile, target, knowledge) 三参签名：note 非空时 memoryRule 追加在档案段后
        val target = TargetEntity(codeName = "小B", note = "她怕黑")
        val system = builder.buildSystem(null, target, "")
        val archiveIdx = system.indexOf("【system-档案】")
        val memoryIdx = system.indexOf("记忆使用规则")
        assertTrue(archiveIdx >= 0 && memoryIdx > archiveIdx)
    }
}
