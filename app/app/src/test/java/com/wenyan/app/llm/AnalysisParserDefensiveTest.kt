package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA 独立补充：五步法 JSON 防御解析（prompt-architecture §4/§6）
 *
 * 攻击点：残缺（steps 非 5 项 / 缺 key / items 缺失）、多字段（多余 key 不崩）、非法 JSON。
 * 期望：绝不整段崩溃；缺失部分用默认值/空占位；非法 JSON 抛 AnalysisParseException。
 */
class AnalysisParserDefensiveTest {

    private fun stepJson(key: String, title: String = key, content: String = "内容", items: String = "[]"): String =
        """{"key":"$key","title":"$title","content":"$content","items":$items}"""

    // ---- 残缺 ----

    @Test
    fun `steps fewer than 5 renders actual count`() {
        val json = """{"steps":[${stepJson("emotion")},${stepJson("facts")},${stepJson("interests")}]}"""
        val analysis = AnalysisParser.parse(json)
        assertEquals("steps 非 5 项应按实际渲染", 3, analysis.steps.size)
    }

    @Test
    fun `steps greater than 5 keeps extra items`() {
        val extra = (1..6).joinToString(",") { stepJson("k$it", content = "c$it") }
        val analysis = AnalysisParser.parse("""{"steps":[$extra]}""")
        assertEquals(6, analysis.steps.size)
    }

    @Test
    fun `step missing key title content uses empty defaults`() {
        val analysis = AnalysisParser.parse("""{"steps":[{"key":"emotion"}]}""")
        val step = analysis.steps.single()
        assertEquals("emotion", step.key)
        assertEquals("", step.title)
        assertEquals("", step.content)
        assertTrue(step.items.isEmpty())
    }

    @Test
    fun `step items missing or non-array yields empty list`() {
        val analysis = AnalysisParser.parse("""{"steps":[{"key":"facts","items":"not-an-array"}]}""")
        // optJSONArray("items") 返回 null → 空列表；不崩溃
        assertTrue(analysis.steps.single().items.isEmpty())
    }

    @Test
    fun `steps entries that are not objects are skipped`() {
        val json = """{"steps":[{"key":"emotion"},"garbage",123,{"key":"action"}]}"""
        val analysis = AnalysisParser.parse(json)
        assertEquals(2, analysis.steps.size)
    }

    @Test
    fun `steps absent yields empty list not crash`() {
        val analysis = AnalysisParser.parse("""{"reply":"x"}""")
        assertTrue(analysis.steps.isEmpty())
    }

    // ---- 多字段 ----

    @Test
    fun `extra unknown fields are ignored`() {
        val json = """
            {"steps":[${stepJson("action")}],"reply":"r","extra":{"a":1},"unexpected":true,"token_estimate":"999"}
        """.trimIndent()
        val analysis = AnalysisParser.parse(json)
        assertEquals("r", analysis.reply)
        // token_estimate 为数字字符串时 optInt 可解析为 999（容错强，不崩溃）
        assertEquals(999, analysis.tokenEstimate)
    }

    @Test
    fun `token estimate parses as int when present`() {
        val analysis = AnalysisParser.parse("""{"steps":[],"token_estimate":1234}""")
        assertEquals(1234, analysis.tokenEstimate)
    }

    // ---- 非法 JSON ----

    @Test
    fun `invalid json throws parse exception`() {
        try {
            AnalysisParser.parse("not json at all {{{")
            throw AssertionError("非法 JSON 应抛 AnalysisParseException")
        } catch (e: AnalysisParser.AnalysisParseException) {
            // 期望路径
        }
    }

    @Test
    fun `empty input throws parse exception`() {
        try {
            AnalysisParser.parse("")
            throw AssertionError("空输入应抛 AnalysisParseException")
        } catch (e: AnalysisParser.AnalysisParseException) {
            // 期望路径
        }
    }

    @Test
    fun `fence with language tag and trailing text stripped`() {
        val raw = "```json\n{\"steps\":[]}\n```"
        assertEquals("{\"steps\":[]}", AnalysisParser.stripFence(raw))
    }

    // ---- v1.6 四段结构（schema v2）防御 ----

    @Test
    fun `v2 advice non-object yields empty advice`() {
        val c = AnalysisParser.parseV2("""{"advice":"not-an-object","empathy":"e"}""")
        assertTrue(c.advice.core.isEmpty())
        assertTrue(c.advice.reasons.isEmpty())
        assertTrue(c.advice.styles.isEmpty())
    }

    @Test
    fun `v2 advice missing entirely yields empty advice`() {
        val c = AnalysisParser.parseV2("""{"empathy":"e","reply":"r"}""")
        assertEquals("e", c.empathy)
        assertEquals("r", c.reply)
        assertTrue(c.advice.core.isEmpty())
    }

    @Test
    fun `v2 facts missing groups yield empty groups`() {
        val c = AnalysisParser.parseV2("""{"facts":{}}""")
        assertTrue(c.facts.known.isEmpty())
        assertTrue(c.facts.assumed.isEmpty())
        assertTrue(c.facts.unknown.isEmpty())
    }

    @Test
    fun `v2 facts non-object yields empty groups`() {
        val c = AnalysisParser.parseV2("""{"facts":["a","b"]}""")
        assertTrue(c.facts.known.isEmpty())
    }

    @Test
    fun `v2 styles skip blank text entries`() {
        val c = AnalysisParser.parseV2(
            """{"advice":{"styles":[{"key":"steady","label":"稳健","text":""},{"key":"charming","label":"会撩","text":"有效话术"}]}}"""
        )
        assertEquals(1, c.advice.styles.size)
        assertEquals("会撩", c.advice.styles[0].label)
    }

    @Test
    fun `v2 styles non-object entries skipped`() {
        val c = AnalysisParser.parseV2(
            """{"advice":{"styles":["garbage",123,{"key":"assertive","label":"强势","text":"直接约"}]}}"""
        )
        assertEquals(1, c.advice.styles.size)
        assertEquals("assertive", c.advice.styles[0].key)
    }

    @Test
    fun `v2 actions skip blank text and non-object entries`() {
        val c = AnalysisParser.parseV2(
            """{"actions":["x",{"label":"观察窗口","text":""},{"label":"小动作","text":"今晚别发"}]}"""
        )
        assertEquals(1, c.actions.size)
        assertEquals("小动作", c.actions[0].label)
    }

    @Test
    fun `v2 parseAny with both steps and v2 keys prefers legacy`() {
        val json = """{"steps":[{"key":"emotion","title":"t","content":"c","items":[]}],"advice":{"core":"x"}}"""
        val c = AnalysisParser.parseAny(json)
        // 有 steps → 按老五步法走；advice.core 不会被读
        assertEquals("c", c.empathy)
        assertTrue(c.advice.core.isEmpty())
    }

    @Test
    fun `v2 empty input throws parse exception`() {
        try {
            AnalysisParser.parseV2("")
            throw AssertionError("空输入应抛 AnalysisParseException")
        } catch (e: AnalysisParser.AnalysisParseException) {
            // 期望路径
        }
    }

    @Test
    fun `v2 empty array citations token default`() {
        val c = AnalysisParser.parseV2("""{"citations":[],"token_estimate":0}""")
        assertTrue(c.citations.isEmpty())
        assertEquals(0, c.tokenEstimate)
    }
}
