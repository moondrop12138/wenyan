package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 MemoryExtractor 纯逻辑测试：
 * buildPrompt 契约 / parseFacts 防御（围栏/垃圾/上限）/ mergeNote 去重、幂等与 2000 字上限。
 * v1.9.0：parseFacts 返回 {text,kind}（ExtractedFact），兼容旧纯字符串格式。
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
        assertEquals(listOf("她喜欢猫", "她怕黑"), facts.map { it.text })
        assertTrue(facts.all { it.kind == MemoryExtractor.KIND_FACT })
    }

    @Test
    fun `parseFacts parses kind hypothesis from object format`() {
        val facts = MemoryExtractor.parseFacts(
            """{"facts":[{"text":"她喜欢猫","kind":"fact"},{"text":"她可能是回避型依恋","kind":"hypothesis"}]}"""
        )
        assertEquals(2, facts.size)
        assertEquals(MemoryExtractor.KIND_FACT, facts[0].kind)
        assertEquals(MemoryExtractor.KIND_HYPOTHESIS, facts[1].kind)
        assertEquals("她可能是回避型依恋", facts[1].text)
    }

    @Test
    fun `parseFacts default kind fact when kind missing`() {
        val facts = MemoryExtractor.parseFacts("""{"facts":[{"text":"她喜欢猫"},{"text":"她怕黑","kind":"其他"}]}""")
        assertTrue(facts.all { it.kind == MemoryExtractor.KIND_FACT })
    }

    @Test
    fun `parseFacts strips code fence`() {
        val facts = MemoryExtractor.parseFacts("```json\n{\"facts\":[\"她喜欢猫\"]}\n```")
        assertEquals(listOf("她喜欢猫"), facts.map { it.text })
    }

    @Test
    fun `parseFacts defensive returns empty on garbage`() {
        assertEquals(emptyList<MemoryExtractor.ExtractedFact>(), MemoryExtractor.parseFacts("not json"))
        assertEquals(emptyList<MemoryExtractor.ExtractedFact>(), MemoryExtractor.parseFacts(""))
        assertEquals(emptyList<MemoryExtractor.ExtractedFact>(), MemoryExtractor.parseFacts("{}"))
        assertEquals(emptyList<MemoryExtractor.ExtractedFact>(), MemoryExtractor.parseFacts("""{"other":1}"""))
        assertEquals(emptyList<MemoryExtractor.ExtractedFact>(), MemoryExtractor.parseFacts("""{"facts":"not array"}"""))
    }

    @Test
    fun `parseFacts caps to 5 facts and 40 chars each`() {
        val longFact = "字".repeat(100)
        val json = """{"facts":[${(0 until 10).joinToString(",") { "\"$longFact\"" }}]}"""
        val facts = MemoryExtractor.parseFacts(json)
        assertEquals(5, facts.size)
        assertTrue(facts.all { it.text.length <= 40 })
    }

    @Test
    fun `parseFacts drops blank entries`() {
        val facts = MemoryExtractor.parseFacts("""{"facts":["", "   ", "她喜欢猫"]}""")
        assertEquals(listOf("她喜欢猫"), facts.map { it.text })
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
        // org.json opt 对数字/布尔 → JSONObject 分支不匹配、null → null 分支，均被忽略，绝不抛异常
        val facts = MemoryExtractor.parseFacts("""{"facts":[1, true, null, "她喜欢猫"]}""")
        assertTrue(facts.map { it.text }.contains("她喜欢猫"))
        assertTrue(facts.all { it.text.isNotEmpty() })
    }

    @Test
    fun `parseFacts preserves order and caps at 5`() {
        val json = """{"facts":["一","二","三","四","五","六"]}"""
        val facts = MemoryExtractor.parseFacts(json)
        assertEquals(5, facts.size)
        assertEquals("一", facts.first().text)
    }

    @Test
    fun `buildPrompt empty inputs does not throw and keeps contract`() {
        val prompt = MemoryExtractor.buildPrompt("", "", "")
        assertTrue(prompt.contains("facts"))
        assertTrue(prompt.contains("没有已记住的内容"))
    }

    // ===== v1.7.3 mergeFacts / splitNoteToFacts =====

    @Test
    fun `mergeFacts appends new facts preserving order`() {
        val merged = MemoryExtractor.mergeFacts(listOf("她喜欢猫"), listOf("她怕黑", "她是夜猫子"))
        assertEquals(listOf("她喜欢猫", "她怕黑", "她是夜猫子"), merged)
    }

    @Test
    fun `mergeFacts from empty existing`() {
        assertEquals(listOf("她喜欢猫"), MemoryExtractor.mergeFacts(emptyList(), listOf("她喜欢猫")))
        assertEquals(emptyList<String>(), MemoryExtractor.mergeFacts(emptyList(), emptyList()))
    }

    @Test
    fun `mergeFacts skips exact duplicate and overlap`() {
        val existing = listOf("她喜欢猫", "她怕黑")
        assertEquals(existing, MemoryExtractor.mergeFacts(existing, listOf("她喜欢猫", "她怕黑")))
        // 6 字前缀重叠也跳过
        assertEquals(existing, MemoryExtractor.mergeFacts(existing, listOf("她喜欢猫讨厌吃鱼")))
    }

    @Test
    fun `mergeFacts idempotent on repeat trigger`() {
        val existing = listOf("她喜欢猫")
        val once = MemoryExtractor.mergeFacts(existing, listOf("她怕黑"))
        val twice = MemoryExtractor.mergeFacts(once, listOf("她怕黑"))
        assertEquals(once, twice)
    }

    @Test
    fun `mergeFacts trims and drops blanks`() {
        val merged = MemoryExtractor.mergeFacts(listOf("她喜欢猫"), listOf("  她怕黑  ", "", "  "))
        assertEquals(listOf("她喜欢猫", "她怕黑"), merged)
    }

    @Test
    fun `mergeFacts caps total at limit`() {
        val existing = (1..50).map { "事实$it" }
        val merged = MemoryExtractor.mergeFacts(existing, listOf("新事实A", "新事实B"))
        assertEquals(50, merged.size)
        assertFalse(merged.contains("新事实A"))
    }

    @Test
    fun `splitNoteToFacts splits and truncates each to 40 chars`() {
        val note = "她喜欢猫。她怕黑；她是个夜猫子\n她喜欢读书"
        val facts = MemoryExtractor.splitNoteToFacts(note)
        assertEquals(listOf("她喜欢猫", "她怕黑", "她是个夜猫子", "她喜欢读书"), facts)
        val longFact = MemoryExtractor.splitNoteToFacts("字".repeat(60))
        assertTrue(longFact.single().length <= 40)
    }

    @Test
    fun `splitNoteToFacts trims and drops empties`() {
        val facts = MemoryExtractor.splitNoteToFacts("  她喜欢猫  。。；；\n  ")
        assertEquals(listOf("她喜欢猫"), facts)
    }

    // ===== QA 独立补充：mergeFacts / splitNoteToFacts 边界（2026-08-07） =====

    @Test
    fun `mergeFacts limit zero returns empty`() {
        val merged = MemoryExtractor.mergeFacts(listOf("她喜欢猫"), listOf("她怕黑"), limit = 0)
        assertEquals(emptyList<String>(), merged)
    }

    @Test
    fun `mergeFacts limit smaller than existing truncates`() {
        val existing = listOf("一", "二", "三", "四")
        val merged = MemoryExtractor.mergeFacts(existing, listOf("五"), limit = 3)
        assertEquals(3, merged.size)
        assertEquals(listOf("一", "二", "三"), merged)
    }

    @Test
    fun `mergeFacts whitespace-only existing entries dropped`() {
        val merged = MemoryExtractor.mergeFacts(listOf("", "  ", "她喜欢猫"), listOf("她怕黑"))
        assertEquals(listOf("她喜欢猫", "她怕黑"), merged)
    }

    @Test
    fun `mergeFacts dedupes against multi-segment existing fact`() {
        // existing 单条含多个分句 → 拆段后逐段 overlaps 判定，新事实命中任意段即跳过
        val existing = listOf("她喜欢猫，讨厌香菜")
        assertEquals(existing, MemoryExtractor.mergeFacts(existing, listOf("她喜欢猫")))
        assertEquals(existing, MemoryExtractor.mergeFacts(existing, listOf("讨厌香菜")))
    }

    @Test
    fun `mergeFacts empty facts returns existing capped at limit`() {
        val existing = (1..60).map { "事实$it" }
        val merged = MemoryExtractor.mergeFacts(existing, emptyList())
        assertEquals(50, merged.size)
    }

    @Test
    fun `mergeFacts all-duplicates returns existing unchanged`() {
        val existing = listOf("她喜欢猫", "她怕黑")
        assertEquals(existing, MemoryExtractor.mergeFacts(existing, listOf("她喜欢猫", "她怕黑", "她喜欢猫讨厌吃鱼")))
    }

    @Test
    fun `splitNoteToFacts blank or separator-only note yields empty`() {
        assertEquals(emptyList<String>(), MemoryExtractor.splitNoteToFacts(""))
        assertEquals(emptyList<String>(), MemoryExtractor.splitNoteToFacts("   "))
        assertEquals(emptyList<String>(), MemoryExtractor.splitNoteToFacts("。；\n"))
    }

    @Test
    fun `mergeFacts preserves existing order when appending`() {
        val existing = listOf("最早事实", "中间事实")
        val merged = MemoryExtractor.mergeFacts(existing, listOf("新事实"))
        assertEquals(listOf("最早事实", "中间事实", "新事实"), merged)
    }

    // ===== v1.9.1 expires_in 解析 + 过期换算 =====

    @Test
    fun `parseFacts parses expires_in today and week`() {
        val facts = MemoryExtractor.parseFacts(
            """{"facts":[
                {"text":"她今天忙","kind":"fact","expires_in":"today"},
                {"text":"她这周要考试","kind":"fact","expires_in":"week"},
                {"text":"她喜欢猫","kind":"fact"}
            ]}"""
        )
        assertEquals(3, facts.size)
        assertEquals(MemoryExtractor.EXPIRES_TODAY, facts[0].expiresIn)
        assertEquals(MemoryExtractor.EXPIRES_WEEK, facts[1].expiresIn)
        assertEquals(null, facts[2].expiresIn)
    }

    @Test
    fun `parseFacts ignores invalid expires_in`() {
        val facts = MemoryExtractor.parseFacts(
            """{"facts":[{"text":"她喜欢猫","expires_in":"year"},{"text":"她怕黑","expires_in":123}]}"""
        )
        assertTrue(facts.all { it.expiresIn == null })
    }

    @Test
    fun `parseFacts legacy string format has no expiry`() {
        val facts = MemoryExtractor.parseFacts("""{"facts":["她喜欢猫"]}""")
        assertEquals(1, facts.size)
        assertEquals(null, facts[0].expiresIn)
    }

    @Test
    fun `computeExpiryMillis today is next midnight in zone`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        // 2026-08-12 12:00 CST → 次日 2026-08-13 00:00 CST
        val now = java.time.ZonedDateTime.of(2026, 8, 12, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expiry = MemoryExtractor.computeExpiryMillis(MemoryExtractor.EXPIRES_TODAY, now, zone)!!
        val expected = java.time.ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, expiry)
    }

    @Test
    fun `computeExpiryMillis week rolls to next monday`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        // 2026-08-12 是周三 → 下周一 2026-08-17 00:00
        val now = java.time.ZonedDateTime.of(2026, 8, 12, 9, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expiry = MemoryExtractor.computeExpiryMillis(MemoryExtractor.EXPIRES_WEEK, now, zone)!!
        val expected = java.time.ZonedDateTime.of(2026, 8, 17, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, expiry)
    }

    @Test
    fun `computeExpiryMillis week on monday rolls to next monday`() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        // 2026-08-10 是周一 → 次周一 2026-08-17
        val now = java.time.ZonedDateTime.of(2026, 8, 10, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expiry = MemoryExtractor.computeExpiryMillis(MemoryExtractor.EXPIRES_WEEK, now, zone)!!
        val expected = java.time.ZonedDateTime.of(2026, 8, 17, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, expiry)
    }

    @Test
    fun `computeExpiryMillis returns null for null or unknown`() {
        assertEquals(null, MemoryExtractor.computeExpiryMillis(null))
        assertEquals(null, MemoryExtractor.computeExpiryMillis("month"))
        assertEquals(null, MemoryExtractor.computeExpiryMillis(""))
    }
}
