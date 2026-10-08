package com.wenyan.app.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wenyan.app.container.UiMappers
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRole
import com.wenyan.app.ui.contract.CoachCard
import com.wenyan.app.ui.contract.MessageType

/**
 * 回答逐段淡入上浮（不是流式、不是逐字打字机、不是整体淡入）：
 * 模型完成后整块渲染全文，按【渐显计划】的 key 顺序错峰浮现——每段透明度 0→1 的同时
 * 轻微上浮 6dp，前段未落定后段已起，观感轻柔克制；渐显期间列表自动跟随。
 *
 * 立即全量的触发面（实测接线，逐处见注释；调用 [RevealController.finishNow] 或直接
 * 清掉 ChatScreen 的 revealPair 均可）：点按气泡/卡片本体、图片预览、用户拖动滚动、
 * 进入会话一次性落底、输入/粘贴/发送、转述确认/重选、错误卡重试/取消、删除消息/会话确认、
 * 长按菜单、复制、部分选择、点重新生成、切风格 tab、新一轮流式开始、系统「移除动画」、
 * 切会话/删消息清理。读屏用户走语义动作（卡片点按收尾/长按菜单/复制）同样收尾；渐显中的消息整块对读屏
 * 隐藏（alpha=0 的文字不被聚焦朗读，播完才出现）。
 *
 * 本文件只含纯计划/控制器逻辑（可单测），渲染侧接入由 ChatScreen/CoachCard/FreetextBubble 负责：
 * 文本永远渲染全文，用 [revealUnit] 把「淡入+上浮」层挂到各 key 的文本上，key 表见 [coachCardRevealPlan]。
 */

/** 单段淡入时长（毫秒）：单段透明度 0→1 的窗口长度 */
const val REVEAL_FADE_MS = 320

/** 段间错峰基准（毫秒）：相邻两段起点间隔的基准值（总时长按段数 × 此值夹逼） */
const val REVEAL_STAGGER_MS = 140

/** 单条回答渐显最短时长（毫秒）——内容再短也完整展示一次浮现节奏 */
const val REVEAL_MIN_MS = 900L

/** 单条回答渐显最长时长（毫秒）——内容再多也不至于等成真流式 */
const val REVEAL_MAX_MS = 2600L

/**
 * 渐显总时长：段数 × 错峰基准 → 夹逼 [REVEAL_MIN_MS, REVEAL_MAX_MS]。
 * 段数 ≤1 同样夹到下限——「内容再短也完整展示一次浮现节奏」（[REVEAL_MIN_MS] 的契约），
 * 与 CHANGELOG/docs/uiux.md 声明的 0.9–2.6s 口径一致，不为单段消息退化成 320ms。
 * （RevealController.animate 使用的唯一时长公式，独立出来便于单测。）
 */
internal fun revealDurationMs(unitCount: Int): Long =
    (unitCount * REVEAL_STAGGER_MS).toLong().coerceIn(REVEAL_MIN_MS, REVEAL_MAX_MS)

/**
 * 渐显计划：有序 key 表（构造顺序 = 卡片渲染顺序 = 错峰淡入顺序）。
 * 去重保序：重复 key 只保留首现位置；[indexOf] 返回 key 在表中的序号，不在表 → null。
 */
class RevealPlan(keys: List<String>) {

    /** 有序 key 表（去重保序：重复 key 保留首现） */
    val keys: List<String> = keys.distinct()

    /** 段数（错峰淡入的单元总数） */
    val unitCount: Int = this.keys.size

    /** key → 序号（构造时算一次，查询 O(1)；不在表 → null） */
    private val indexOfKey: Map<String, Int> =
        this.keys.withIndex().associate { (index, key) -> key to index }

    /** key 在表中的序号（错峰起点用）；key 不在表 → null */
    fun indexOf(key: String): Int? = indexOfKey[key]
}

/**
 * 逐段淡入上浮控制器。
 *
 * - [progress] 0f..1f，Compose 快照可观察状态（[unit] 的 graphicsLayer 块内读取，动画帧只刷新层不重组）；
 * - [alpha] 按错峰窗口给出该 key 当前的淡入透明度（不在表/已 finished → 1f）；
 * - [animate] 用 Animatable + tween(LinearEasing) 匀速推进到 1f；
 * - [finishNow] 接线交互（滚动/输入/菜单/复制/重发等，见文件头）立即全量，中断渐显语义由调用方触发。
 */
class RevealController(private val plan: RevealPlan) {

    private val progressState = mutableStateOf(0f)

    /** 0f..1f，可观察状态（快照读取触发层刷新） */
    val progress: Float get() = progressState.value

    /** 是否已全部浮现（progress ≥ 1f） */
    val finished: Boolean get() = progress >= 1f

    /**
     * 该 key 当前的淡入透明度：
     * 各段起点 = 序号 × 段间隔 step，[progress] 推进的时间轴上每段在自己的
     * [REVEAL_FADE_MS] 窗口内经 FastOutSlowIn 由 0 淡入到 1；
     * key 不在计划表 → 1f（原样常显）；已 finished → 1f。
     */
    fun alpha(key: String): Float {
        val index = plan.indexOf(key) ?: return 1f
        if (finished) return 1f
        val n = plan.unitCount
        val totalMs = revealDurationMs(n).toFloat()
        // 单段/空计划：淡入窗口拉伸到整个总时长（≥REVEAL_MIN_MS）——否则 320ms 冲到底、
        // 剩余时长静止，观感仍是「一闪即出」，违反最短时长契约；多段保持 320ms 窗口错峰
        val fadeIn = if (n <= 1) totalMs else REVEAL_FADE_MS.toFloat()
        val step = if (n <= 1) 0f else ((totalMs - fadeIn) / (n - 1)).coerceAtLeast(1f)
        val start = index * step
        val fraction = ((progress * totalMs - start) / fadeIn).coerceIn(0f, 1f)
        return FastOutSlowInEasing.transform(fraction)
    }

    /**
     * 该 key 的「淡入+上浮」层：透明度按 [alpha] 错峰推进，同时越晚出现的段
     * 起始位置越低（未浮现时下沉 6dp，随透明度升到 1 归位）。
     * 在 graphicsLayer 块内读 progressState——动画帧只更新层属性，不触发重组。
     */
    fun unit(key: String): Modifier = Modifier.graphicsLayer {
        val a = alpha(key)
        alpha = a
        translationY = (1f - a) * 6.dp.toPx()
    }

    /**
     * 把 progress 从 0f 匀速推到 1f：
     * 时长 = revealDurationMs(unitCount)（段数 × 错峰基准，夹逼 [REVEAL_MIN_MS, REVEAL_MAX_MS]）；
     * LinearEasing 保证段间隔恒定。已 finished（如已被 finishNow 全量）则直接返回，不回退。
     */
    suspend fun animate() {
        if (finished) return
        val animator = Animatable(0f)
        animator.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = revealDurationMs(plan.unitCount).toInt(),
                easing = LinearEasing,
            ),
        ) {
            // 外部已 finishNow（交互打断）后不再回写半途值，避免全量后又闪回
            if (!finished) progressState.value = value
        }
        progressState.value = 1f
    }

    /** 立即全量：progress 直接置 1f（交互打断用） */
    fun finishNow() {
        progressState.value = 1f
    }

    /** 计划表最后一个 key——结构性附属元素（如「重新生成」行）与末段内容齐淡入用 */
    val lastKey: String? get() = plan.keys.lastOrNull()

    /**
     * 仅供单测：直接写入中间进度（夹逼 0f..1f）。
     * 生产路径只有 [animate]（动画帧推进）与 [finishNow]（立即全量）两个写入口。
     */
    internal fun setProgressForTest(value: Float) {
        progressState.value = value.coerceIn(0f, 1f)
    }
}

/** 把 [RevealController.unit] 的淡入上浮层挂到任意文本 Modifier 上（reveal 为 null 时原样返回） */
fun Modifier.revealUnit(reveal: RevealController?, key: String): Modifier =
    if (reveal == null) this else this.then(reveal.unit(key))

/**
 * 与计划表【最后一段】齐淡入：给不占独立 key、但排在内容之后的结构性附属元素用
 * （如「重新生成」行——内容画完操作行才出，不空耗计划位）；reveal 为 null 或空计划时原样返回。
 */
fun Modifier.revealLastUnit(reveal: RevealController?): Modifier {
    if (reveal == null) return this
    val key = reveal.lastKey ?: return this
    return this.then(reveal.unit(key))
}

/**
 * 按消息类型生成渐显计划（key 表与渲染侧一一对应）：
 * - ANALYSIS 且 [UiMappers.parseCoachCard] 解析成功 → 军师卡计划 [coachCardRevealPlan]；
 * - FREETEXT → 话术段 "reply" + 正文 "body"（无话术段则整条按 "body"）；
 * - 其余（TEXT/IMAGE/TRANSCRIPTION/解析失败兜底）→ 整条 "body"。
 */
fun buildRevealPlan(message: ChatMessageUi): RevealPlan = when (message.type) {
    MessageType.ANALYSIS -> UiMappers.parseCoachCard(message.content)
        ?.let { coachCardRevealPlan(it) }
        ?: RevealPlan(listOf("body"))

    MessageType.FREETEXT -> {
        val split = FreetextSplitter.split(message.content)
        if (split.reply.isNotBlank()) {
            RevealPlan(listOf("reply", "body"))
        } else {
            RevealPlan(listOf("body"))
        }
    }

    else -> RevealPlan(listOf("body"))
}

/**
 * 军师卡渐显计划：按下表【渲染顺序】给实际存在的文本登记 key（顺序不可变、缺失项跳过），
 * 同一张表也是渲染侧的 key 表（CoachCard 各文本位点按此 key 挂淡入上浮层）：
 *
 * brand → time → core → kickerEmpathyCn/En → empathy → kickerFactsCn/En →
 * factsKnownLabel → factsKnown[i] → factsAssumedLabel → factsAssumed[i] → factsUnknownLabel →
 * factsUnknown[i] → kickerAdviceCn/En → adviceTag → reasonNo[i] → reason[i] → styleTab[i] →
 * scriptLabel → scriptText → scriptCopy → replyTiming → kickerActionsCn/En → actionNo[i] →
 * action[i] → citations → memoryLabel → memory[i] → tokenEstimate
 *
 * 「实际存在」= CoachCard 渲染条件：段头 kicker 随所属段存在（如 empathy 空白则整段
 * ①含 kicker 不入表），组标签随所属列表非空入表——计划与渲染严格同源，避免为不渲染的
 * 文本空耗时长。
 */
fun coachCardRevealPlan(card: CoachCard): RevealPlan {
    val keys = mutableListOf<String>()

    /** 按文本非空登记（入表条件与渲染条件同源：空文本不渲染 → 不入表） */
    fun add(key: String, text: String) {
        if (text.isNotEmpty()) keys.add(key)
    }

    // ── 刊头：短规则线 + 温言·回信 + 时间（始终渲染）──
    keys.add("brand")
    keys.add("time")

    // ── 衬线大标题（adviceCore，非空才渲染）──
    if (card.adviceCore.isNotBlank()) add("core", card.adviceCore)

    // ── ① 接住你（整段以 empathy 非空为存在条件，kicker 随段）──
    if (card.empathy.isNotBlank()) {
        add("kickerEmpathyCn", "接住你")
        add("kickerEmpathyEn", "EMPATHY")
        add("empathy", card.empathy)
    }

    // ── ② 先分清事实（任一事实列表非空 → 段存在；组标签随各自列表）──
    val hasFacts = card.factsKnown.isNotEmpty() || card.factsAssumed.isNotEmpty() || card.factsUnknown.isNotEmpty()
    if (hasFacts) {
        add("kickerFactsCn", "先分清事实")
        add("kickerFactsEn", "FACT CHECK")
        if (card.factsKnown.isNotEmpty()) {
            add("factsKnownLabel", "已知")
            card.factsKnown.forEachIndexed { i, item -> add("factsKnown[$i]", item) }
        }
        if (card.factsAssumed.isNotEmpty()) {
            add("factsAssumedLabel", "推测")
            card.factsAssumed.forEachIndexed { i, item -> add("factsAssumed[$i]", item) }
        }
        if (card.factsUnknown.isNotEmpty()) {
            add("factsUnknownLabel", "未知")
            card.factsUnknown.forEachIndexed { i, item -> add("factsUnknown[$i]", item) }
        }
    }

    // ── ③ 军师建议（段存在条件与 CoachCard.hasAdvice 同源：tag/理由/风格/发送时机任一非空）──
    val hasAdvice = card.adviceTag.isNotBlank() || card.reasons.isNotEmpty() ||
        card.styles.isNotEmpty() || card.replyTiming.isNotBlank()
    if (hasAdvice) {
        add("kickerAdviceCn", "军师建议")
        add("kickerAdviceEn", "COLUMN")
        if (card.adviceTag.isNotBlank()) add("adviceTag", card.adviceTag)
        card.reasons.forEachIndexed { i, reason ->
            add("reasonNo[$i]", "${i + 1}.")
            add("reason[$i]", reason)
        }
        card.styles.forEachIndexed { i, style ->
            add("styleTab[$i]", style.label.ifBlank { style.key })
        }
        // 话术框（scriptLabel/scriptText/scriptCopy 同进同出：任一风格话术非空即渲染，
        // 渲染侧选中恒为首个非空风格——选择随风格签名重置，见 CoachCard 注释，两者同源）
        if (card.styles.any { it.text.isNotBlank() }) {
            add("scriptLabel", if (card.isClarification) "先确认一下" else "可以直接发")
            // 淡入版按段渲染全文，无字符预算，也无「最长风格被截断」问题——按渲染条件入表即可
            keys.add("scriptText")
            // UNCERTAIN 反问轮 ScriptBox 不渲染「复制话术」链接 → 不入表
            if (!card.isClarification) add("scriptCopy", "复制话术")
        }
        if (card.replyTiming.isNotBlank()) add("replyTiming", "发送时机：${card.replyTiming}")
    }

    // ── ④ 行动清单（动作列表非空 → 段存在）──
    if (card.actions.isNotEmpty()) {
        add("kickerActionsCn", "行动清单")
        add("kickerActionsEn", "TAKEAWAYS")
        card.actions.forEachIndexed { i, action ->
            add("actionNo[$i]", (i + 1).toString().padStart(2, '0') + ".")
            add("action[$i]", action.text)
        }
    }

    // ── 尾部：知识库引用 / 记忆依据 / token 估算（各以自身非空为条件）──
    if (card.citations.isNotEmpty()) add("citations", "参考：" + card.citations.joinToString(" · "))
    if (card.memoryCitations.isNotEmpty()) {
        add("memoryLabel", "记忆依据")
        card.memoryCitations.take(3).forEachIndexed { i, citation -> add("memory[$i]", "「$citation」") }
    }
    if (card.tokenEstimate > 0) add("tokenEstimate", "本次消耗估算：~${card.tokenEstimate} token")

    return RevealPlan(keys)
}

/**
 * 消息级渐显触发器（纯逻辑，不依赖 Compose）：
 * 每轮对比消息快照，返回本轮应渐显的 ASSISTANT 消息 id（多个符合取列表最后一个），无则 null。
 *
 * 规则：
 * - 空列表直接返回 null 且不碰快照：冷启动/退出重开首帧拿到的是 Room 尚未回填的空列表，
 *   若按「首次调用=全量登记」处理，随后回填的整段历史会被判成新消息、末条历史被误开渐显
 *   （违反「历史直出」）；全删清空同理跳过——已登记快照保留，新消息到达时正常 diff 触发。
 *   （不用 registry.isEmpty() 做「未初始化」判断：那会把「全删清空后」误判成首次登记，
 *   导致清空后的第一条真实新消息漏触发；初始化状态用独立 [initialized] 标志。）
 * - 未初始化或 sessionId 与上次不同 → 全量登记 id→content.hashCode() 并返回 null；
 * - 此后每轮先 diff（O(1) map 查找）筛出【新 id】或【内容 hash 变化】的条目，
 *   只对变化条目做可渐显判定（判定含 JSON 解析，避免每次增删消息都全量重解析）；
 *   可渐显 = ASSISTANT + type ∈ {ANALYSIS, FREETEXT, TEXT} + 非危机卡
 *   （仅 ANALYSIS 解析 safetyOverride，TEXT/FREETEXT 不可能是危机卡、不白跑解析）；
 * - USER / IMAGE / TRANSCRIPTION 永不渐显。
 */
class MessageRevealTracker {

    /** 上一次登记的消息快照（id → content hash） */
    private var registry: Map<Long, Int> = emptyMap()

    /** 上一次处理的会话 id（null = 尚未初始化） */
    private var sessionId: Long? = null

    /** 是否已做过首次登记（独立于快照是否为空，见上） */
    private var initialized = false

    /**
     * 输入本轮消息列表，返回应渐显的消息 id。
     * @param sessionId 当前会话 id；切换会话即重新初始化快照（不触发渐显）。
     */
    fun onMessages(sessionId: Long, messages: List<ChatMessageUi>): Long? {
        // 空列表：Room 未回填的首帧 / 全删清空——不登记不触发，历史回填到达时按首次登记直出
        if (messages.isEmpty()) return null
        if (!initialized || this.sessionId != sessionId) {
            // 首次登记 / 切会话：全量快照，不触发——历史/冷启动一律直出
            this.sessionId = sessionId
            registry = snapshot(messages)
            initialized = true
            return null
        }
        var candidate: Long? = null
        for (msg in messages) {
            // 先 diff：无变化的条目（长会话的绝大多数）直接跳过，不做解析判定
            if (registry[msg.id] == msg.content.hashCode()) continue
            if (!shouldReveal(msg)) continue
            // 新 id 或内容 hash 变化且可渐显 → 候选；多个取最后一个
            candidate = msg.id
        }
        registry = snapshot(messages)
        return candidate
    }

    private fun snapshot(messages: List<ChatMessageUi>): Map<Long, Int> =
        messages.associate { it.id to it.content.hashCode() }

    /** 可渐显判定：ASSISTANT + 指定类型 + 非危机卡（危机走 CrisisCard，不逐字铺开）；只对 diff 变化条目调用 */
    private fun shouldReveal(msg: ChatMessageUi): Boolean =
        msg.role == ChatRole.ASSISTANT &&
            msg.type in REVEALABLE_TYPES &&
            // 危机卡只经 ANALYSIS 通道渲染（CrisisCard 分支），TEXT/FREETEXT 无需解析即非危机
            (msg.type != MessageType.ANALYSIS || UiMappers.parseCoachCard(msg.content)?.safetyOverride != true)

    private companion object {
        val REVEALABLE_TYPES = setOf(MessageType.ANALYSIS, MessageType.FREETEXT, MessageType.TEXT)
    }
}
