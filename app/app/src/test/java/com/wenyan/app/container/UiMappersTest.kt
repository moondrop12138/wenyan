package com.wenyan.app.container

import com.wenyan.app.ui.contract.InputKindUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiMappers.parseCoachCard 双 schema 映射测试（prompt-architecture §5 渲染映射 + §6 防御）
 */
class UiMappersTest {

    private val legacyJson = """
        {
          "steps": [
            {"key":"emotion","title":"情绪落地","content":"你感到失落","items":[]},
            {"key":"facts","title":"事实拆分","content":"","items":["已知事实1","推测2"]},
            {"key":"interests","title":"利益判断","content":"","items":["互惠稳定"]},
            {"key":"advice","title":"明确建议","content":"首选建议","items":["理由A"]},
            {"key":"action","title":"行动收束","content":"小动作","items":["今晚别发消息"]}
          ],
          "reply": "可以直接发的话术",
          "reply_timing": "今晚发送",
          "citations": ["实战话术编排器：从一句回复到后续分支.md"],
          "safety_override": false,
          "safety_message": "",
          "token_estimate": 1200,
          "input_kind": "relayed_quote"
        }
    """.trimIndent()

    private val v2Json = """
        {
          "input_kind": "pasted_chat",
          "empathy": "这件事确实让人心里发堵",
          "reply": "今天先忙自己的事",
          "reply_timing": "明早回",
          "facts": {
            "known": ["她连续三天主动找话题"],
            "assumed": ["她可能对你有好感"],
            "unknown": ["她现在的真实想法"]
          },
          "advice": {
            "tag": "常规主动",
            "core": "保持低强度主动",
            "reasons": ["持续主动是真实信号"],
            "styles": [
              {"key": "steady", "label": "稳健", "text": "明天问一句"},
              {"key": "charming", "label": "会撩", "text": "周末看展"},
              {"key": "assertive", "label": "强势", "text": "直接约"}
            ]
          },
          "actions": [
            {"label": "小动作", "text": "今晚8点前不发消息"},
            {"label": "观察窗口", "text": "观察3天"}
          ],
          "citations": ["00-导读与使用分级.md"],
          "safety_override": false,
          "safety_message": "",
          "token_estimate": 1500
        }
    """.trimIndent()

    // ---- 老五步法 → CoachCard 映射 ----

    @Test
    fun `legacy json maps emotion content to empathy`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals("你感到失落", card.empathy)
    }

    @Test
    fun `legacy json maps facts items to known only`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals(listOf("已知事实1", "推测2"), card.factsKnown)
        assertTrue(card.factsAssumed.isEmpty())
        assertTrue(card.factsUnknown.isEmpty())
    }

    @Test
    fun `legacy json maps advice content to core and items plus interests to reasons`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals("首选建议", card.adviceCore)
        assertTrue(card.adviceTag.isEmpty())
        assertEquals(listOf("理由A", "互惠稳定"), card.reasons)
    }

    @Test
    fun `legacy json maps reply to single steady style`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals("可以直接发的话术", card.reply)
        assertEquals(1, card.styles.size)
        assertEquals("steady", card.styles[0].key)
        assertEquals("稳健", card.styles[0].label)
        assertEquals("可以直接发的话术", card.styles[0].text)
    }

    @Test
    fun `legacy json maps action items to actions`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals(1, card.actions.size)
        assertEquals("小动作", card.actions[0].label)
        assertEquals("今晚别发消息", card.actions[0].text)
    }

    @Test
    fun `legacy json passes through timing citations safety kind`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertEquals("今晚发送", card.replyTiming)
        assertEquals(listOf("实战话术编排器：从一句回复到后续分支.md"), card.citations)
        assertFalse(card.safetyOverride)
        assertEquals(1200, card.tokenEstimate)
        assertEquals(InputKindUi.RELAYED_QUOTE, card.inputKind)
        assertFalse(card.isClarification)
    }

    // ---- v2 直映射 ----

    @Test
    fun `v2 json maps all sections`() {
        val card = UiMappers.parseCoachCard(v2Json)!!
        assertEquals(InputKindUi.PASTED_CHAT, card.inputKind)
        assertEquals("这件事确实让人心里发堵", card.empathy)
        assertEquals(listOf("她连续三天主动找话题"), card.factsKnown)
        assertEquals(listOf("她可能对你有好感"), card.factsAssumed)
        assertEquals(listOf("她现在的真实想法"), card.factsUnknown)
        assertEquals("常规主动", card.adviceTag)
        assertEquals("保持低强度主动", card.adviceCore)
        assertEquals(listOf("持续主动是真实信号"), card.reasons)
        assertEquals(3, card.styles.size)
        assertEquals("会撩", card.styles[1].label)
        assertEquals("直接约", card.styles[2].text)
        assertEquals(2, card.actions.size)
        assertEquals("观察窗口", card.actions[1].label)
        assertEquals("今天先忙自己的事", card.reply)
        assertEquals("明早回", card.replyTiming)
        assertEquals(listOf("00-导读与使用分级.md"), card.citations)
        assertFalse(card.safetyOverride)
        assertEquals(1500, card.tokenEstimate)
        assertFalse(card.isClarification)
    }

    @Test
    fun `v2 uncertain is clarification`() {
        val json = """{
          "input_kind": "uncertain",
          "empathy": "拿不准",
          "reply": "这是她对你说的，对吧？",
          "facts": {"known": [], "assumed": [], "unknown": []},
          "advice": {"tag": "", "core": "先确认", "reasons": [], "styles": []},
          "actions": [],
          "citations": [],
          "safety_override": false,
          "safety_message": ""
        }"""
        val card = UiMappers.parseCoachCard(json)!!
        assertEquals(InputKindUi.UNCERTAIN, card.inputKind)
        assertTrue(card.isClarification)
        assertTrue(card.styles.isEmpty())
    }

    // ---- 防御 ----

    @Test
    fun `invalid json returns null`() {
        assertNull(UiMappers.parseCoachCard("not-json"))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(UiMappers.parseCoachCard(""))
    }

    @Test
    fun `empty object maps to default card`() {
        val card = UiMappers.parseCoachCard("""{}""")!!
        assertEquals(InputKindUi.UNKNOWN, card.inputKind)
        assertTrue(card.empathy.isEmpty())
        assertTrue(card.factsKnown.isEmpty())
        assertTrue(card.actions.isEmpty())
        assertFalse(card.isClarification)
    }

    // ---- v1.6.2 coachCardToSelectableText（"部分选择"模式可选文本拼接）----

    @Test
    fun `selectable text joins all sections with blank lines`() {
        val card = UiMappers.parseCoachCard(v2Json)!!
        val text = UiMappers.coachCardToSelectableText(card)
        assertTrue(text.startsWith("这件事确实让人心里发堵\n\n【事实】"))
        assertTrue(text.contains("【推测】\n· 她可能对你有好感"))
        assertTrue(text.contains("【未知】\n· 她现在的真实想法"))
        assertTrue(text.contains("【军师建议】\n策略：常规主动\n保持低强度主动\n1. 持续主动是真实信号"))
        assertTrue(text.contains("【现在可以做什么】\n· 小动作：今晚8点前不发消息\n· 观察窗口：观察3天"))
        assertTrue(text.contains("发送时机：明早回"))
        assertTrue(text.contains("\n可以直接发：\n明天问一句"))
    }

    @Test
    fun `selectable text keeps only first style`() {
        val card = UiMappers.parseCoachCard(v2Json)!!
        val text = UiMappers.coachCardToSelectableText(card)
        assertTrue(text.contains("明天问一句"))
        assertFalse(text.contains("会撩"))
        assertFalse(text.contains("直接约"))
    }

    @Test
    fun `selectable text omits empty sections`() {
        val card = UiMappers.parseCoachCard("""{}""")!!
        assertEquals("", UiMappers.coachCardToSelectableText(card))
    }

    @Test
    fun `selectable text outputs safety message when overridden`() {
        val card = UiMappers.parseCoachCard(v2Json)!!.copy(safetyOverride = true, safetyMessage = "立即联系紧急联系人")
        assertEquals("立即联系紧急联系人", UiMappers.coachCardToSelectableText(card))
    }

    @Test
    fun `selectable text falls back when safety message blank`() {
        val card = UiMappers.parseCoachCard(v2Json)!!.copy(safetyOverride = true, safetyMessage = "")
        assertEquals("先处理安全，再处理关系", UiMappers.coachCardToSelectableText(card))
    }

    // ===== v1.7.3 memory_citations 映射 + 全字段 TargetUi / MemoryFactUi =====

    @Test
    fun `v2 json maps memory citations through coach card`() {
        val json = """{
          "input_kind": "pasted_chat",
          "empathy": "e",
          "reply": "r",
          "facts": {"known": [], "assumed": [], "unknown": []},
          "advice": {"core": "c", "styles": []},
          "actions": [],
          "citations": [],
          "memory_citations": ["她喜欢猫", "她怕黑"],
          "safety_override": false,
          "safety_message": ""
        }"""
        val card = UiMappers.parseCoachCard(json)!!
        assertEquals(listOf("她喜欢猫", "她怕黑"), card.memoryCitations)
    }

    @Test
    fun `legacy json maps memory citations empty`() {
        val card = UiMappers.parseCoachCard(legacyJson)!!
        assertTrue(card.memoryCitations.isEmpty())
    }

    @Test
    fun `toTargetUi maps structured fields`() {
        val entity = com.wenyan.app.data.db.TargetEntity(
            id = 7L,
            codeName = "小A",
            mbti = "INFJ",
            score = 80,
            relationStatus = "暧昧中",
            timeline = """[{"time":"认识","event":"朋友介绍"}]""",
            note = "旧记忆",
            createdAt = 123L,
        )
        val ui = UiMappers.toTargetUi(entity, isActive = true, factCount = 5)
        assertEquals(7L, ui.id)
        assertEquals("小A", ui.name)
        assertEquals("INFJ", ui.mbti)
        assertEquals(80, ui.score)
        assertEquals("暧昧中", ui.relationStatus)
        assertTrue(ui.timeline.contains("朋友介绍"))
        assertTrue(ui.isActive)
        // v1.7.3-fix：事实数映射透传（默认 0）
        assertEquals(5, ui.factCount)
        assertEquals(0, UiMappers.toTargetUi(entity, isActive = false).factCount)
    }

    @Test
    fun `toMemoryFactUi maps entity`() {
        val entity = com.wenyan.app.data.db.MemoryFactEntity(
            id = 3L,
            targetId = 7L,
            text = "她喜欢猫",
            createdAt = 456L,
        )
        val ui = UiMappers.toMemoryFactUi(entity)
        assertEquals(3L, ui.id)
        assertEquals("她喜欢猫", ui.text)
        assertEquals(456L, ui.createdAt)
    }
}
