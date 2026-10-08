package com.wenyan.app.ui.chat

import com.wenyan.app.container.UiMappers
import com.wenyan.app.ui.contract.ActionItemUi
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRole
import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.InputKindUi
import com.wenyan.app.ui.contract.MessageType
import com.wenyan.app.ui.contract.ScriptStyle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回答逐段淡入上浮单测：
 * - RevealPlan 保序去重（重复 key 保留首现）与 indexOf；
 * - coachCardRevealPlan key 表顺序与入表条件（全字段卡片 / 稀疏卡片缺失段跳过）；
 * - RevealController.alpha 时序（起点全 0 / 错峰窗口 / 单调不回退 / finished 全 1 / 未知 key 恒 1 / n=1 边界）；
 * - revealDurationMs 段数换算与上下限夹逼；
 * - buildRevealPlan 各消息类型分支；
 * - MessageRevealTracker 触发规则（首次登记 / 新消息 / 内容变化 / 类型与危机卡排除 / 切会话）。
 * （不测 unit()/graphicsLayer：需 Compose 运行时，alpha 与时长均为纯逻辑可直测。）
 */
class RevealControllerTest {

    // ===== RevealPlan =====

    @Test
    fun `RevealPlan - 去重保序重复 key 保留首现`() {
        val plan = RevealPlan(listOf("a", "b", "a", "c", "b"))
        assertEquals(listOf("a", "b", "c"), plan.keys)
        assertEquals(3, plan.unitCount)
        assertEquals(0, plan.indexOf("a")) // 首现位置，不落到第二次出现处
        assertEquals(1, plan.indexOf("b"))
        assertEquals(2, plan.indexOf("c"))
    }

    @Test
    fun `RevealPlan - indexOf 未知 key 返回 null`() {
        val plan = RevealPlan(listOf("head", "tail"))
        assertEquals(0, plan.indexOf("head"))
        assertEquals(1, plan.indexOf("tail"))
        assertNull(plan.indexOf("unknown"))
        // 空计划：全表未知
        val empty = RevealPlan(emptyList())
        assertEquals(0, empty.unitCount)
        assertNull(empty.indexOf("body"))
    }

    // ===== coachCardRevealPlan：key 表按渲染顺序且缺失段跳过 =====

    @Test
    fun `全字段卡片 - key 表按渲染顺序完整`() {
        val plan = coachCardRevealPlan(fullCard())
        assertKeys(plan, listOf(
            "brand",
            "time",
            "core",
            "kickerEmpathyCn", "kickerEmpathyEn", "empathy",
            "kickerFactsCn", "kickerFactsEn",
            "factsKnownLabel", "factsKnown[0]", "factsKnown[1]",
            "factsAssumedLabel", "factsAssumed[0]",
            "factsUnknownLabel", "factsUnknown[0]",
            "kickerAdviceCn", "kickerAdviceEn", "adviceTag",
            "reasonNo[0]", "reason[0]", "reasonNo[1]", "reason[1]",
            "styleTab[0]", "styleTab[1]",
            "scriptLabel", "scriptText", "scriptCopy", "replyTiming",
            "kickerActionsCn", "kickerActionsEn",
            "actionNo[0]", "action[0]", "actionNo[1]", "action[1]",
            "citations",
            "memoryLabel", "memory[0]", "memory[1]", "memory[2]",
            "tokenEstimate",
        ))
        // 记忆依据最多 3 条：第 4 条不入表
        assertNull(plan.indexOf("memory[3]"))
    }

    @Test
    fun `部分字段卡片 - 只有核心建议时缺失段全部跳过`() {
        val plan = coachCardRevealPlan(CoachCard(adviceCore = "只有一句核心建议"))
        assertKeys(plan, listOf("brand", "time", "core"))
        assertAbsent(plan, listOf(
            "kickerEmpathyCn", "kickerEmpathyEn", "empathy",
            "kickerFactsCn", "kickerFactsEn",
            "factsKnownLabel", "factsAssumedLabel", "factsUnknownLabel",
            "kickerAdviceCn", "kickerAdviceEn", "adviceTag",
            "reasonNo[0]", "reason[0]", "styleTab[0]",
            "scriptLabel", "scriptText", "scriptCopy", "replyTiming",
            "kickerActionsCn", "kickerActionsEn", "actionNo[0]", "action[0]",
            "citations", "memoryLabel", "memory[0]", "tokenEstimate",
        ))
    }

    @Test
    fun `部分字段卡片 - 只有事实与行动时跳过标题共情建议`() {
        val plan = coachCardRevealPlan(
            CoachCard(
                factsAssumed = listOf("她可能在忙"),
                actions = listOf(ActionItemUi(label = "", text = "今晚不追问")),
            )
        )
        assertKeys(plan, listOf(
            "brand", "time",
            "kickerFactsCn", "kickerFactsEn",
            "factsAssumedLabel", "factsAssumed[0]",
            "kickerActionsCn", "kickerActionsEn",
            "actionNo[0]", "action[0]",
        ))
        // 段头随段存在：无已知/未知列表则组标签不入表；无建议段则 kicker 不入表
        assertAbsent(plan, listOf(
            "core", "empathy", "kickerEmpathyCn",
            "factsKnownLabel", "factsUnknownLabel",
            "kickerAdviceCn", "adviceTag", "scriptLabel", "replyTiming",
            "citations", "memoryLabel", "tokenEstimate",
        ))
    }

    @Test
    fun `scriptText - 按渲染条件入表 UNCERTAIN 反问轮不入 scriptCopy`() {
        // 淡入版无字符预算，入表条件 = 渲染条件本身（任一风格话术非空即定框，渲染选中恒为首个非空；
        // 反问轮不渲染复制链接）
        val plan = coachCardRevealPlan(
            CoachCard(
                adviceTag = "轻接触策略",
                styles = listOf(ScriptStyle(key = "u", label = "反问", text = "在吗？想确认一件事")),
                inputKind = InputKindUi.UNCERTAIN,
            )
        )
        assertKeys(plan, listOf(
            "brand", "time",
            "kickerAdviceCn", "kickerAdviceEn", "adviceTag", "styleTab[0]",
            "scriptLabel", "scriptText",
        ))
        assertNull(plan.indexOf("scriptCopy"))
        // 全部风格话术为空 → 话术框整组不入表
        val noScript = coachCardRevealPlan(
            CoachCard(adviceTag = "轻接触策略", styles = listOf(ScriptStyle(key = "a", label = "稳健", text = "")))
        )
        assertNull(noScript.indexOf("scriptLabel"))
        assertNull(noScript.indexOf("scriptText"))
    }

    @Test
    fun `scriptText - 非首风格有话术时仍定框`() {
        // 渲染选中恒回落到首个非空话术风格（见 CoachCard 注释），计划与渲染同源：任一非空即定框
        val plan = coachCardRevealPlan(
            CoachCard(
                adviceTag = "轻接触策略",
                styles = listOf(
                    ScriptStyle(key = "a", label = "稳健", text = ""),
                    ScriptStyle(key = "b", label = "会撩", text = "话术乙"),
                ),
            )
        )
        assertNotNull(plan.indexOf("scriptLabel"))
        assertNotNull(plan.indexOf("scriptText"))
        assertNotNull(plan.indexOf("scriptCopy"))
    }

    // ===== RevealController.alpha 时序 =====

    /** 3 段计划：总时长 = 3 × 140 = 420 → 夹到 REVEAL_MIN_MS = 900ms，step = (900 − 320) ÷ 2 = 290ms */
    private fun trioPlan() = RevealPlan(listOf("a", "b", "c"))

    @Test
    fun `alpha - progress 0 时在表 key 全为 0`() {
        val c = RevealController(trioPlan())
        assertEquals(0f, c.progress, 0f)
        assertFalse(c.finished)
        assertEquals(0f, c.alpha("a"), 0f)
        assertEquals(0f, c.alpha("b"), 0f)
        assertEquals(0f, c.alpha("c"), 0f)
    }

    @Test
    fun `alpha - 推进中前序 key 已满 当前半透明 后续为 0`() {
        val c = RevealController(trioPlan())
        c.setProgressForTest(0.5f) // 已过 450ms：a 窗口[0,320]已满，b 窗口[290,610]正中，c 窗口[580,900]未开
        assertEquals(1f, c.alpha("a"), 0f)
        val mid = c.alpha("b")
        assertTrue("当前段应在 (0,1)，实际=$mid", mid > 0f && mid < 1f)
        assertEquals(0f, c.alpha("c"), 0f)
    }

    @Test
    fun `alpha - 首段淡入中后续两段均为 0`() {
        val c = RevealController(trioPlan())
        c.setProgressForTest(160f / 900f) // 约 160ms：a 窗口正中
        val first = c.alpha("a")
        assertTrue("首段应在 (0,1)，实际=$first", first > 0f && first < 1f)
        assertEquals(0f, c.alpha("b"), 0f)
        assertEquals(0f, c.alpha("c"), 0f)
    }

    @Test
    fun `alpha - 全程单调不回退`() {
        val c = RevealController(trioPlan())
        val keys = listOf("a", "b", "c")
        val last = keys.associateWith { 0f }.toMutableMap()
        for (i in 0..100) {
            c.setProgressForTest(i / 100f)
            for (k in keys) {
                val a = c.alpha(k)
                assertTrue("progress=${i / 100f} key=$k 回退：${last[k]} -> $a", a >= last[k]!!)
                last[k] = a
            }
        }
        // 终点全 1（progress=1 → finished）
        assertEquals(1f, c.alpha("a"), 0f)
        assertEquals(1f, c.alpha("b"), 0f)
        assertEquals(1f, c.alpha("c"), 0f)
    }

    @Test
    fun `alpha - finishNow 与 setProgress 1 后全为 1`() {
        val c = RevealController(trioPlan())
        c.finishNow()
        assertEquals(1f, c.progress, 0f)
        assertTrue(c.finished)
        assertEquals(1f, c.alpha("a"), 0f)
        assertEquals(1f, c.alpha("b"), 0f)
        assertEquals(1f, c.alpha("c"), 0f)

        val c2 = RevealController(trioPlan())
        c2.setProgressForTest(1f)
        assertTrue(c2.finished)
        assertEquals(1f, c2.alpha("a"), 0f)
        assertEquals(1f, c2.alpha("b"), 0f)
        assertEquals(1f, c2.alpha("c"), 0f)
    }

    @Test
    fun `alpha - setProgress 夹逼 0f 到 1f`() {
        val c = RevealController(trioPlan())
        c.setProgressForTest(-0.5f)
        assertEquals(0f, c.progress, 0f)
        assertFalse(c.finished)
        c.setProgressForTest(1.5f)
        assertEquals(1f, c.progress, 0f)
        assertTrue(c.finished)
    }

    @Test
    fun `alpha - 未知 key 恒为 1`() {
        val c = RevealController(trioPlan())
        assertEquals(1f, c.alpha("unknown"), 0f) // progress=0
        c.setProgressForTest(0.5f)
        assertEquals(1f, c.alpha("unknown"), 0f)
        c.finishNow()
        assertEquals(1f, c.alpha("unknown"), 0f)
        // 空计划上的任意 key 同样恒显
        val empty = RevealController(RevealPlan(emptyList()))
        assertEquals(1f, empty.alpha("body"), 0f)
    }

    @Test
    fun `alpha - n=1 边界按最短时长完整推进`() {
        // unitCount=1 → 淡入窗口拉伸到总时长（= REVEAL_MIN_MS，见 alpha 注释）：
        // 整段窗口就是一次 900ms 淡入，起点 0、中点半透明、终点 1
        val c = RevealController(RevealPlan(listOf("only")))
        assertEquals(0f, c.alpha("only"), 0f)
        c.setProgressForTest(0.5f)
        val mid = c.alpha("only")
        assertTrue("n=1 中点应在 (0,1)，实际=$mid", mid > 0f && mid < 1f)
        c.finishNow()
        assertEquals(1f, c.alpha("only"), 0f)
    }

    // ===== 时长公式 =====

    @Test
    fun `渐显常量 - 契约值`() {
        assertEquals(320, REVEAL_FADE_MS)
        assertEquals(140, REVEAL_STAGGER_MS)
        assertEquals(900L, REVEAL_MIN_MS)
        assertEquals(2600L, REVEAL_MAX_MS)
    }

    @Test
    fun `revealDurationMs - 单段及空计划同样夹到最短时长`() {
        // REVEAL_MIN_MS 契约「内容再短也完整展示一次浮现节奏」+ CHANGELOG/docs 口径 0.9–2.6s：
        // 单段不退化为 320ms，同样夹到下限
        assertEquals(REVEAL_MIN_MS, revealDurationMs(1))
        assertEquals(REVEAL_MIN_MS, revealDurationMs(0))
    }

    @Test
    fun `revealDurationMs - 段数时长夹逼上下限`() {
        // 小段数：2 × 140 = 280 → 夹到最短
        assertEquals(REVEAL_MIN_MS, revealDurationMs(2))
        assertEquals(REVEAL_MIN_MS, revealDurationMs(6)) // 840 → 最短
        // 区间内保持 段数 × 错峰基准
        assertEquals(980L, revealDurationMs(7)) // 7 × 140
        assertEquals(2520L, revealDurationMs(18)) // 18 × 140（正上限内）
        // 大段数：越界夹逼
        assertEquals(REVEAL_MAX_MS, revealDurationMs(19)) // 2660 → 最长
        assertEquals(REVEAL_MAX_MS, revealDurationMs(1000))
        // 一切段数的取值都落在 [REVEAL_MIN_MS, REVEAL_MAX_MS]
        for (n in 0..100) {
            val ms = revealDurationMs(n)
            assertTrue("n=$n 时长 $ms 越界", ms in REVEAL_MIN_MS..REVEAL_MAX_MS)
        }
    }

    // ===== buildRevealPlan =====

    @Test
    fun `buildRevealPlan - TEXT 整条按 body`() {
        val plan = buildRevealPlan(msg(type = MessageType.TEXT, content = "你好世界"))
        assertEquals(listOf("body"), plan.keys)
        assertEquals(0, plan.indexOf("body"))
        assertNull(plan.indexOf("brand"))
    }

    @Test
    fun `buildRevealPlan - FREETEXT 有话术时 reply 在前 body 在后`() {
        val content = "先听我说。可以发：晚上八点见。"
        val split = FreetextSplitter.split(content)
        assertTrue("样例应拆出话术段", split.reply.isNotBlank())
        val plan = buildRevealPlan(msg(type = MessageType.FREETEXT, content = content))
        assertEquals(listOf("reply", "body"), plan.keys)
        assertEquals(0, plan.indexOf("reply"))
        assertEquals(1, plan.indexOf("body"))
    }

    @Test
    fun `buildRevealPlan - FREETEXT 无话术时整条按 body`() {
        val content = "她这次真的生气了，我觉得是我的问题。"
        val plan = buildRevealPlan(msg(type = MessageType.FREETEXT, content = content))
        assertEquals(listOf("body"), plan.keys)
        assertNull(plan.indexOf("reply"))
    }

    @Test
    fun `buildRevealPlan - ANALYSIS 可解析走军师卡计划`() {
        assertNotNull("样例 JSON 应可解析", UiMappers.parseCoachCard(PARSEABLE_JSON))
        val plan = buildRevealPlan(msg(type = MessageType.ANALYSIS, content = PARSEABLE_JSON))
        assertNotNull(plan.indexOf("brand"))
        assertNotNull(plan.indexOf("core"))
        assertNotNull(plan.indexOf("scriptText"))
        assertNull(plan.indexOf("body"))
    }

    @Test
    fun `buildRevealPlan - ANALYSIS 解析失败回落 body 全文`() {
        val content = "这不是一段合法的 JSON"
        val plan = buildRevealPlan(msg(type = MessageType.ANALYSIS, content = content))
        assertEquals(listOf("body"), plan.keys)
        assertNull(plan.indexOf("brand"))
    }

    // ===== MessageRevealTracker =====

    @Test
    fun `首次登记 - 返回 null 且无变化不再触发`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        assertNull(t.onMessages(10L, base))
        assertNull(t.onMessages(10L, base))
    }

    @Test
    fun `首次空登记 - 回填历史不触发 后续新消息正常触发`() {
        // 冷启动/退出重开：首帧 Room 未回填的空列表不建快照，回填的整段历史按首次登记直出
        val t = MessageRevealTracker()
        assertNull(t.onMessages(10L, emptyList()))
        val history = listOf(msg(1, ChatRole.USER), msg(2))
        assertNull(t.onMessages(10L, history))
        assertNull(t.onMessages(10L, history))
        // 此后真实新消息正常触发
        assertEquals(3L, t.onMessages(10L, history + msg(3)))
    }

    @Test
    fun `全删清空 - 不吞掉清空后的第一条新消息`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        assertNull(t.onMessages(10L, base))
        // 全删：空列表不碰快照
        assertNull(t.onMessages(10L, emptyList()))
        assertNull(t.onMessages(10L, emptyList()))
        // 清空后的第一条真实新消息必须触发（registry.isEmpty() 误判首次登记会吞掉它）
        val fresh = listOf(msg(10, ChatRole.USER), msg(11))
        assertEquals(11L, t.onMessages(10L, fresh))
    }

    @Test
    fun `空会话首答 - 用户消息登记 AI 回复触发`() {
        // 空会话：首个非空列表（用户消息）触发首次登记，随后 AI 回复 diff 触发
        val t = MessageRevealTracker()
        assertNull(t.onMessages(10L, emptyList()))
        val userOnly = listOf(msg(1, ChatRole.USER, MessageType.TEXT, "你好"))
        assertNull(t.onMessages(10L, userOnly))
        assertEquals(2L, t.onMessages(10L, userOnly + msg(2)))
    }

    @Test
    fun `新 ASSISTANT 消息 - 触发其 id 且登记后不再重复触发`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)
        val next = base + msg(3)
        assertEquals(3L, t.onMessages(10L, next))
        assertNull(t.onMessages(10L, next))
    }

    @Test
    fun `同 id 内容变化 - 触发`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)
        val changed = listOf(msg(1, ChatRole.USER), msg(2, content = "答（重写）"))
        assertEquals(2L, t.onMessages(10L, changed))
    }

    @Test
    fun `USER 与 IMAGE 与 TRANSCRIPTION - 永不触发`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)

        val withUser = base + msg(3, ChatRole.USER, MessageType.TEXT, "用户新消息")
        assertNull(t.onMessages(10L, withUser))

        val withImage = withUser + msg(4, ChatRole.ASSISTANT, MessageType.IMAGE, "data:image/png;base64,xxx")
        assertNull(t.onMessages(10L, withImage))

        val withTrans = withImage + msg(5, ChatRole.ASSISTANT, MessageType.TRANSCRIPTION, "转述确认卡")
        assertNull(t.onMessages(10L, withTrans))

        // 对照：同轮新增普通 ASSISTANT 文本仍触发（排除仅针对上面三类）
        val withText = withTrans + msg(6)
        assertEquals(6L, t.onMessages(10L, withText))
    }

    @Test
    fun `ANALYSIS 与 FREETEXT 非危机 - 同样触发`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)
        val withAnalysis = base + msg(3, ChatRole.ASSISTANT, MessageType.ANALYSIS, PARSEABLE_JSON)
        assertEquals(3L, t.onMessages(10L, withAnalysis))
        val withFreetext = withAnalysis + msg(4, ChatRole.ASSISTANT, MessageType.FREETEXT, "可以发：晚上见。")
        assertEquals(4L, t.onMessages(10L, withFreetext))
    }

    @Test
    fun `危机卡 - 新增与内容变化均不触发`() {
        // 解析自检：危机 JSON 解析失败会让消息被判为可渐显，下面的 null 断言随之失败
        assertEquals(true, UiMappers.parseCoachCard(CRISIS_JSON)?.safetyOverride)
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)

        val crisis = msg(3, ChatRole.ASSISTANT, MessageType.ANALYSIS, CRISIS_JSON)
        assertNull(t.onMessages(10L, base + crisis))

        // 同 id 内容变化（仍是危机卡）→ 依旧不触发
        val changed = base + crisis.copy(content = CRISIS_JSON.replace("先稳住情绪", "先别慌"))
        assertNull(t.onMessages(10L, changed))
    }

    @Test
    fun `切会话 - 重新初始化不触发`() {
        val t = MessageRevealTracker()
        t.onMessages(10L, listOf(msg(1, ChatRole.USER), msg(2)))
        // 换会话：全新消息（新 id）只登记不触发
        val session2 = listOf(msg(100, ChatRole.USER), msg(101))
        assertNull(t.onMessages(20L, session2))
        assertNull(t.onMessages(20L, session2))
        // 新会话内新增 → 正常触发
        assertEquals(102L, t.onMessages(20L, session2 + msg(102)))
        // 再切回旧会话 id：与「上次」不同 → 重新初始化
        assertNull(t.onMessages(10L, listOf(msg(1, ChatRole.USER), msg(2), msg(3))))
    }

    @Test
    fun `多个候选 - 取列表最后一个`() {
        val t = MessageRevealTracker()
        val base = listOf(msg(1, ChatRole.USER), msg(2))
        t.onMessages(10L, base)
        val next = base + msg(3) + msg(4)
        assertEquals(4L, t.onMessages(10L, next))
    }

    // ===== animate/finishNow 竞态 =====
    // 说明：animate() 的帧推进需 Compose MonotonicFrameClock（Animatable.animateTo 经 withFrameNanos
    // 取帧时钟），纯 JVM 单测无此 element——直接调用抛 IllegalStateException（已实测），故全程推进、
    // 「中途 finishNow 不回写半途值」守卫、末尾恒置 1f 三项在单测无覆盖（见提交结果的未覆盖声明）。
    // 此处只覆盖不依赖帧时钟的前置守卫：已 finished 直接返回（不挂起、不抛、不回退）。

    @Test
    fun `animate - 已 finished 直接返回不回退`() = runTest {
        val c = RevealController(trioPlan())
        c.finishNow()
        c.animate()
        assertEquals(1f, c.progress, 0f)
    }

    @Test
    fun `animate - 无帧时钟时抛 IllegalStateException（单测不可跑全程的证据）`() = runTest {
        // JVM 单测 context 里没有 MonotonicFrameClock，animateTo 取不到帧时钟即抛；
        // 本用例锁定该前提：若未来某天它不抛了，说明帧推进可测了，应补全程用例
        val c = RevealController(trioPlan())
        var thrown: Exception? = null
        try {
            c.animate()
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull("无帧时钟时 animate 应抛 IllegalStateException", thrown)
    }

    @Test
    fun `lastKey - 末 key 与空计划`() {
        assertEquals("c", RevealController(trioPlan()).lastKey)
        assertNull(RevealController(RevealPlan(emptyList())).lastKey)
        // revealLastUnit 空计划时原样返回（Modifier 链测试需 Compose 运行时，此处只断 key）
    }

    // ===== 样例与断言工具 =====

    private fun msg(
        id: Long = 0L,
        role: ChatRole = ChatRole.ASSISTANT,
        type: MessageType = MessageType.TEXT,
        content: String = "内容$id",
    ) = ChatMessageUi(id = id, role = role, type = type, content = content, createdAt = id)

    /** key 表三合一：顺序逐项一致、indexOf 与序号对齐、unitCount = 表长 */
    private fun assertKeys(plan: RevealPlan, expected: List<String>) {
        assertEquals("key 表应与渲染顺序一致", expected, plan.keys)
        assertEquals("unitCount 应等于表长", expected.size, plan.unitCount)
        expected.forEachIndexed { index, key ->
            assertEquals("key=$index 应为 $key", key, plan.keys[index])
            assertEquals("indexOf($key) 应为 $index", index, plan.indexOf(key))
        }
    }

    private fun assertAbsent(plan: RevealPlan, keys: List<String>) {
        for (key in keys) assertNull("key=$key 不应入表", plan.indexOf(key))
    }

    /** 全字段军师卡：所有段与尾部字段齐备（记忆引用 4 条验证 take(3)） */
    private fun fullCard() = CoachCard(
        empathy = "这件事确实让人心里发堵。",
        factsKnown = listOf("已知事实一", "已知事实二"),
        factsAssumed = listOf("推测事实一"),
        factsUnknown = listOf("未知事实一"),
        adviceTag = "轻接触策略",
        adviceCore = "先别急着道歉，你们缺的是一次轻接触。",
        reasons = listOf("追问会把她的低能量归因到你身上", "单字回复期需要零负担入口"),
        styles = listOf(
            ScriptStyle(key = "steady", label = "稳健", text = "话术甲"),
            ScriptStyle(key = "charming", label = "会撩", text = "话术乙"),
        ),
        actions = listOf(
            ActionItemUi(label = "", text = "今晚不发追问"),
            ActionItemUi(label = "", text = "明晚发轻内容"),
        ),
        reply = "话术甲",
        replyTiming = "今晚八点",
        citations = listOf("00-导读.md", "01-边界.md"),
        memoryCitations = listOf("记忆一", "记忆二", "记忆三", "记忆四"),
        tokenEstimate = 1200,
    )

    private companion object {
        /** 可解析的 v2 四段 JSON（非危机） */
        val PARSEABLE_JSON = """
            {
              "input_kind": "user_question",
              "empathy": "先接住情绪",
              "reply": "可以直接发的话术",
              "facts": {"known": ["事实一"], "assumed": [], "unknown": []},
              "advice": {
                "tag": "常规主动",
                "core": "保持低强度主动",
                "reasons": ["持续主动是真实信号"],
                "styles": [{"key": "steady", "label": "稳健", "text": "明天问一句"}]
              },
              "actions": [{"label": "小动作", "text": "今晚不发消息"}],
              "citations": ["00-导读.md"],
              "memory_citations": ["她喜欢猫"],
              "safety_override": false,
              "safety_message": "",
              "token_estimate": 900
            }
        """.trimIndent()

        /** 危机卡 JSON：parseCoachCard 应解析成功且 safetyOverride = true */
        val CRISIS_JSON = """
            {
              "input_kind": "user_question",
              "empathy": "先稳住情绪",
              "reply": "",
              "facts": {"known": [], "assumed": [], "unknown": []},
              "advice": {"tag": "", "core": "", "reasons": [], "styles": []},
              "actions": [],
              "citations": [],
              "safety_override": true,
              "safety_message": "如果你有伤害自己的念头，请及时拨打心理援助热线。"
            }
        """.trimIndent()
    }
}
