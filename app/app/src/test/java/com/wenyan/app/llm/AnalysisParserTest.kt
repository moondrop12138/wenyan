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
        assertEquals(InputKind.RELAYED_QUOTE, AnalysisParser.parse(json).inputKind)
    }

    @Test
    fun `input_kind uncertain parsed`() {
        val json = """{"steps":[],"reply":"我先确认下——这是她对你说的，对吧？","citations":[],"input_kind":"uncertain"}"""
        assertEquals(InputKind.UNCERTAIN, AnalysisParser.parse(json).inputKind)
    }

    @Test
    fun `input_kind missing falls back to UNKNOWN`() {
        // 旧模型无此字段 → UNKNOWN，不崩
        assertEquals(InputKind.UNKNOWN, AnalysisParser.parse(validJson).inputKind)
    }

    @Test
    fun `input_kind invalid value falls back to UNKNOWN`() {
        val json = """{"steps":[],"reply":"","citations":[],"input_kind":"bogus"}"""
        assertEquals(InputKind.UNKNOWN, AnalysisParser.parse(json).inputKind)
    }

    // ===== v1.6 四段结构（schema v2 / parseAny） =====

    private val v2Json = """
        {
          "input_kind": "pasted_chat",
          "empathy": "这件事确实让人心里发堵",
          "reply": "今天先忙自己的事，明天再回她",
          "reply_timing": "今晚别发，明早回",
          "facts": {
            "known": ["她连续三天主动找话题", "上周约了一次线下"],
            "assumed": ["她可能对你有好感", "冷淡可能只是忙"],
            "unknown": ["她现在的真实想法", "是否有其他人选"]
          },
          "advice": {
            "tag": "常规主动",
            "core": "保持低强度主动，先不追问",
            "reasons": ["持续主动是真实信号", "追问会显得有压迫感"],
            "styles": [
              {"key": "steady", "label": "稳健", "text": "明天下午问一句周末有空吗"},
              {"key": "charming", "label": "会撩", "text": "周末有个展，想拉个人一起"},
              {"key": "assertive", "label": "强势", "text": "直接约，行就约不行就撤"}
            ]
          },
          "actions": [
            {"label": "小动作", "text": "今晚8点前不发消息"},
            {"label": "观察窗口", "text": "观察3天她是否主动"}
          ],
          "citations": ["实战话术编排器：从一句回复到后续分支.md"],
          "safety_override": false,
          "safety_message": "",
          "token_estimate": 1500
        }
    """.trimIndent()

    @Test
    fun `parseAny v2 json full fields`() {
        val c = AnalysisParser.parseAny(v2Json)
        assertEquals(InputKind.PASTED_CHAT, c.inputKind)
        assertEquals("这件事确实让人心里发堵", c.empathy)
        assertEquals("今天先忙自己的事，明天再回她", c.reply)
        assertEquals("今晚别发，明早回", c.replyTiming)
        assertEquals(listOf("她连续三天主动找话题", "上周约了一次线下"), c.facts.known)
        assertEquals(2, c.facts.assumed.size)
        assertEquals(2, c.facts.unknown.size)
        assertEquals("常规主动", c.advice.tag)
        assertEquals("保持低强度主动，先不追问", c.advice.core)
        assertEquals(2, c.advice.reasons.size)
        assertEquals(3, c.advice.styles.size)
        assertEquals("steady", c.advice.styles[0].key)
        assertEquals("会撩", c.advice.styles[1].label)
        assertEquals(2, c.actions.size)
        assertEquals("小动作", c.actions[0].label)
        assertEquals("观察窗口", c.actions[1].label)
        assertEquals(1, c.citations.size)
        assertFalse(c.safetyOverride)
        assertEquals(1500, c.tokenEstimate)
    }

    @Test
    fun `parseAny legacy json maps into v2 structure`() {
        val c = AnalysisParser.parseAny(validJson)
        // emotion.content → empathy
        assertEquals("你感到失落", c.empathy)
        // facts.items → known
        assertEquals(listOf("已知事实1", "推测2"), c.facts.known)
        assertTrue(c.facts.assumed.isEmpty())
        assertTrue(c.facts.unknown.isEmpty())
        // advice.content → core
        assertEquals("首选建议", c.advice.core)
        assertTrue(c.advice.tag.isEmpty())
        // reply → 单条稳健风格
        assertEquals(1, c.advice.styles.size)
        assertEquals("稳健", c.advice.styles[0].label)
        assertEquals("可以直接发的话术", c.reply)
        // action 段 content 非 items → actions 为空
        assertTrue(c.actions.isEmpty())
        assertEquals("今晚发送", c.replyTiming)
    }

    @Test
    fun `parseAny with fence strips it`() {
        val c = AnalysisParser.parseAny("```json\n$v2Json\n```")
        assertEquals(3, c.advice.styles.size)
    }

    @Test
    fun `parseV2 uncertain question`() {
        val json = """{
          "input_kind": "uncertain",
          "empathy": "这里我拿不准一个关键信息",
          "reply": "我先确认下——这是她对你说的，对吧？",
          "facts": {"known": [], "assumed": [], "unknown": []},
          "advice": {"tag": "", "core": "先确认再判断", "reasons": [], "styles": []},
          "actions": [],
          "citations": [],
          "safety_override": false,
          "safety_message": "",
          "token_estimate": 400
        }"""
        val c = AnalysisParser.parseV2(json)
        assertEquals(InputKind.UNCERTAIN, c.inputKind)
        assertTrue(c.advice.styles.isEmpty())
        assertTrue(c.actions.isEmpty())
    }

    @Test
    fun `parseV2 invalid json throws`() {
        org.junit.Assert.assertThrows(AnalysisParser.AnalysisParseException::class.java) {
            AnalysisParser.parseV2("not-json")
        }
    }

    @Test
    fun `parseAny invalid json throws`() {
        org.junit.Assert.assertThrows(AnalysisParser.AnalysisParseException::class.java) {
            AnalysisParser.parseAny("not-json")
        }
    }

    @Test
    fun `parseAny missing fields default safely`() {
        val c = AnalysisParser.parseAny("""{"input_kind":"greeting"}""")
        assertEquals(InputKind.GREETING, c.inputKind)
        assertTrue(c.empathy.isEmpty())
        assertTrue(c.facts.known.isEmpty())
        assertTrue(c.advice.styles.isEmpty())
        assertTrue(c.actions.isEmpty())
        assertNull(c.tokenEstimate)
    }
}
