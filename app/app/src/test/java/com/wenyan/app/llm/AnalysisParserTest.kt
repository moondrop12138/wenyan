package com.wenyan.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五步法 JSON 解析测试（prompt-architecture §4/§6 防御性解析）
 */
class AnalysisParserTest {

    private val validJson = """
        {
          "steps": [
            {"key":"emotion","title":"情绪落地","content":"你感到失落","items":[]},
            {"key":"facts","title":"事实拆分","content":"","items":["已知事实1","推测2"]},
            {"key":"interests","title":"利益判断","content":"","items":[]},
            {"key":"advice","title":"明确建议","content":"首选建议","items":[]},
            {"key":"action","title":"行动收束","content":"小动作","items":[]}
          ],
          "reply": "可以直接发的话术",
          "reply_timing": "今晚发送",
          "citations": ["实战话术编排器：从一句回复到后续分支.md"],
          "safety_override": false,
          "safety_message": "",
          "token_estimate": 1200
        }
    """.trimIndent()

    @Test
    fun `parse valid json`() {
        val analysis = AnalysisParser.parse(validJson)
        assertEquals(5, analysis.steps.size)
        assertEquals("emotion", analysis.steps[0].key)
        assertEquals("可以直接发的话术", analysis.reply)
        assertEquals(1, analysis.citations.size)
        assertFalse(analysis.safetyOverride)
        assertEquals(1200, analysis.tokenEstimate)
    }

    @Test
    fun `parse json with fence`() {
        val fenced = "```json\n$validJson\n```"
        val analysis = AnalysisParser.parse(fenced)
        assertEquals(5, analysis.steps.size)
    }

    @Test
    fun `parse missing steps returns empty list`() {
        val analysis = AnalysisParser.parse("""{"reply":"x","citations":[]}""")
        assertTrue(analysis.steps.isEmpty())
    }

    @Test
    fun `parse invalid json throws`() {
        org.junit.Assert.assertThrows(AnalysisParser.AnalysisParseException::class.java) {
            AnalysisParser.parse("not-json")
        }
    }

    @Test
    fun `strip fence handles language tag`() {
        assertEquals("{\"a\":1}", AnalysisParser.stripFence("```json\n{\"a\":1}\n```"))
        assertEquals("{\"a\":1}", AnalysisParser.stripFence("{\"a\":1}"))
    }

    @Test
    fun `defaults applied for missing fields`() {
        val analysis = AnalysisParser.parse("""{"steps":[]}""")
        assertEquals("", analysis.reply)
        assertEquals("", analysis.replyTiming)
        assertTrue(analysis.citations.isEmpty())
        assertFalse(analysis.safetyOverride)
        assertEquals("", analysis.safetyMessage)
        assertNull(analysis.tokenEstimate)
    }

    // ===== v1.2 input_kind =====

    @Test
    fun `input_kind relayed_quote parsed`() {
        val json = """{"steps":[],"reply":"尊重她的边界","citations":[],"input_kind":"relayed_quote"}"""
        assertEquals(FiveStepAnalysis.InputKind.RELAYED_QUOTE, AnalysisParser.parse(json).inputKind)
    }

    @Test
    fun `input_kind uncertain parsed`() {
        val json = """{"steps":[],"reply":"我先确认下——这是她对你说的，对吧？","citations":[],"input_kind":"uncertain"}"""
        assertEquals(FiveStepAnalysis.InputKind.UNCERTAIN, AnalysisParser.parse(json).inputKind)
    }

    @Test
    fun `input_kind missing falls back to UNKNOWN`() {
        // 旧模型无此字段 → UNKNOWN，不崩
        assertEquals(FiveStepAnalysis.InputKind.UNKNOWN, AnalysisParser.parse(validJson).inputKind)
    }

    @Test
    fun `input_kind invalid value falls back to UNKNOWN`() {
        val json = """{"steps":[],"reply":"","citations":[],"input_kind":"bogus"}"""
        assertEquals(FiveStepAnalysis.InputKind.UNKNOWN, AnalysisParser.parse(json).inputKind)
    }
}
