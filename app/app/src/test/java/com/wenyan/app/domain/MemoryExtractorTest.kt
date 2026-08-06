package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 MemoryExtractor 纯逻辑测试：
 * buildPrompt 契约 / parseFacts 防御（围栏/垃圾/上限）/ mergeNote 去重、幂等与 2000 字上限。
 */
class MemoryExtractorTest {

    // ===== buildPrompt =====

    @Test
    fun `buildPrompt mentions facts json contract and existing note`() {
        val prompt = MemoryExtractor.buildPrompt("她喜欢猫", "军师回复：可以送猫粮", "已记住：她怕黑")
        assertTrue(prompt.contains("facts"))
        assertTrue(prompt.contains("她喜欢猫"))
        assertTrue(prompt.contains("已记住：她怕黑"))
    }

    @Test
    fun `buildPrompt caps long inputs`() {
        val prompt = MemoryExtractor.buildPrompt("字".repeat(5000), "字".repeat(5000), "")
        // 用户输入截 1000、回复截 2000、已有记忆截 2000，不爆炸
        assertTrue(prompt.length < 5000)
    }

    // ===== parseFacts =====

    @Test
    fun `parseFacts returns list from valid json`() {
        val facts = MemoryExtractor.parseFacts("""{"facts":["她喜欢猫","她怕黑"]}""")
        assertEquals(listOf("她喜欢猫", "她怕黑"), facts)
    }

    @Test
    fun `parseFacts strips code fence`() {
        val facts = MemoryExtractor.parseFacts("```json\n{\"facts\":[\"她喜欢猫\"]}\n```")
        assertEquals(listOf("她喜欢猫"), facts)
    }

    @Test
    fun `parseFacts defensive returns empty on garbage`() {
        assertEquals(emptyList<String>(), MemoryExtractor.parseFacts("not json"))
        assertEquals(emptyList<String>(), MemoryExtractor.parseFacts(""))
        assertEquals(emptyList<String>(), MemoryExtractor.parseFacts("{}"))
        assertEquals(emptyList<String>(), MemoryExtractor.parseFacts("""{"other":1}"""))
        assertEquals(emptyList<String>(), MemoryExtractor.parseFacts("""{"facts":"not array"}"""))
    }

    @Test
    fun `parseFacts caps to 5 facts and 40 chars each`() {
        val longFact = "字".repeat(100)
        val json = """{"facts":[${(0 until 10).joinToString(",") { "\"$longFact\"" }}]}"""
        val facts = MemoryExtractor.parseFacts(json)
        assertEquals(5, facts.size)
        assertTrue(facts.all { it.length <= 40 })
    }

    @Test
    fun `parseFacts drops blank entries`() {
        val facts = MemoryExtractor.parseFacts("""{"facts":["", "   ", "她喜欢猫"]}""")
        assertEquals(listOf("她喜欢猫"), facts)
    }

    // ===== mergeNote =====

    @Test
    fun `mergeNote appends new facts with separator`() {
        val merged = MemoryExtractor.mergeNote("她喜欢猫", listOf("她怕黑"))
        assertEquals("她喜欢猫；她怕黑", merged)
    }

    @Test
    fun `mergeNote joins from empty note`() {
        val merged = MemoryExtractor.mergeNote("", listOf("她喜欢猫", "她怕黑"))
        assertEquals("她喜欢猫；她怕黑", merged)
    }

    @Test
    fun `mergeNote skips exact duplicate`() {
        val merged = MemoryExtractor.mergeNote("她喜欢猫", listOf("她喜欢猫"))
        assertEquals("她喜欢猫", merged)
    }

    @Test
    fun `mergeNote skips overlapping segment`() {
        val merged = MemoryExtractor.mergeNote("她喜欢猫，讨厌香菜", listOf("她喜欢猫"))
        assertEquals("她喜欢猫，讨厌香菜", merged)
    }

    @Test
    fun `mergeNote idempotent on repeat trigger`() {
        val note = "她喜欢猫"
        val once = MemoryExtractor.mergeNote(note, listOf("她怕黑"))
        val twice = MemoryExtractor.mergeNote(once, listOf("她怕黑"))
        assertEquals(once, twice)
    }

    @Test
    fun `mergeNote empty facts returns original`() {
        val note = "她喜欢猫"
        assertEquals(note, MemoryExtractor.mergeNote(note, emptyList()))
        assertEquals(note, MemoryExtractor.mergeNote(note, listOf("", "  ")))
    }

    @Test
    fun `mergeNote trims facts`() {
        val merged = MemoryExtractor.mergeNote("她喜欢猫", listOf("  她怕黑  "))
        assertEquals("她喜欢猫；她怕黑", merged)
    }

    @Test
    fun `mergeNote truncates to limit`() {
        val note = "字".repeat(1998)
        val merged = MemoryExtractor.mergeNote(note, listOf("新事实"))
        assertEquals(2000, merged.length)
    }

    // ===== QA 边界补充（独立核验，2026-08-06） =====

    @Test
    fun `mergeNote whitespace-only existing treated as empty`() {
        assertEquals("她怕黑", MemoryExtractor.mergeNote("   ", listOf("她怕黑")))
        assertEquals("她怕黑", MemoryExtractor.mergeNote("\n\t", listOf("她怕黑")))
    }

    @Test
    fun `mergeNote skips full segment containment both directions`() {
        val note = "她喜欢猫讨厌吃鱼。她怕黑"
        assertEquals(note, MemoryExtractor.mergeNote(note, listOf("她喜欢猫讨厌吃鱼")))
        assertEquals(note, MemoryExtractor.mergeNote(note, listOf("她怕黑。她喜欢猫讨厌吃鱼")))
    }

    @Test
    fun `mergeNote skips via 6-char prefix containment`() {
        // fact 首 6 字「她喜欢猫讨厌」已存在于片段，但整句互不含 → 走 prefix 判定跳过
        val note = "她喜欢猫讨厌吃鱼且怕黑"
        val fact = "她喜欢猫讨厌香菜，但不怕黑"
        assertFalse(note.contains(fact))
        assertFalse(fact.contains(note))
        val merged = MemoryExtractor.mergeNote(note, listOf(fact))
        assertEquals(note, merged)
    }

    @Test
    fun `mergeNote dedupes multiple facts and keeps new ones`() {
        val note = "她喜欢猫；她怕黑"
        val merged = MemoryExtractor.mergeNote(note, listOf("她喜欢猫", "她怕黑", "她是夜猫子"))
        assertEquals("她喜欢猫；她怕黑；她是夜猫子", merged)
    }

    @Test
    fun `mergeNote respects custom limit parameter`() {
        val merged = MemoryExtractor.mergeNote("她喜欢猫", listOf("她怕黑", "她是夜猫子"), limit = 9)
        assertTrue(merged.length <= 9)
        // 追加式截断：保留原 note 前缀
        assertTrue(merged.startsWith("她喜欢猫"))
    }

    @Test
    fun `mergeNote boundary truncation keeps note within limit`() {
        // 上限边界：1999 字 + 分隔符 + 新事实 → take(2000) 截断，结果不超限
        val note = "字".repeat(1999)
        val merged = MemoryExtractor.mergeNote(note, listOf("新"))
        assertEquals(2000, merged.length)
    }

    @Test
    fun `parseFacts handles non-string array elements defensively`() {
        // org.json optString 对数字/布尔转字符串、null → 空串被过滤，绝不抛异常
        val facts = MemoryExtractor.parseFacts("""{"facts":[1, true, null, "她喜欢猫"]}""")
        assertTrue(facts.contains("她喜欢猫"))
        assertTrue(facts.all { it.isNotEmpty() })
    }

    @Test
    fun `parseFacts preserves order and caps at 5`() {
        val json = """{"facts":["一","二","三","四","五","六"]}"""
        val facts = MemoryExtractor.parseFacts(json)
        assertEquals(5, facts.size)
        assertEquals("一", facts.first())
    }

    @Test
    fun `buildPrompt empty inputs does not throw and keeps contract`() {
        val prompt = MemoryExtractor.buildPrompt("", "", "")
        assertTrue(prompt.contains("facts"))
        assertTrue(prompt.contains("没有已记住的内容"))
    }
}
