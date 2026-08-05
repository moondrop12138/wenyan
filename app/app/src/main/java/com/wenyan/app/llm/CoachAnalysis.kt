package com.wenyan.app.llm

/**
 * v2 四段结构输出（prompt-architecture §4 JSON Schema v2）
 *
 * 接住你（empathy）→ 先分清事实（facts.known/assumed/unknown）→
 * 军师建议（advice.core + reasons + styles 三风格话术）→ 现在可以做什么（actions）。
 * 纯 JVM 可测；字段全部防御性默认值。
 */
data class CoachAnalysis(
    val inputKind: InputKind = InputKind.UNKNOWN,
    /** 共情段落（接住你） */
    val empathy: String = "",
    /** 首选风格成品话术 = advice.styles[0].text；uncertain 时为反问句 */
    val reply: String = "",
    val replyTiming: String = "",
    val facts: Facts = Facts(),
    val advice: Advice = Advice(),
    val actions: List<ActionItem> = emptyList(),
    val citations: List<String> = emptyList(),
    val safetyOverride: Boolean = false,
    val safetyMessage: String = "",
    val tokenEstimate: Int? = null,
) {
    data class Facts(
        val known: List<String> = emptyList(),
        val assumed: List<String> = emptyList(),
        val unknown: List<String> = emptyList(),
    )

    data class Advice(
        /** 策略标签（如 常规主动），可空 */
        val tag: String = "",
        /** 核心建议一句（必填） */
        val core: String = "",
        val reasons: List<String> = emptyList(),
        /** 三风格话术（稳健/会撩/强势），切换纯本地 */
        val styles: List<Style> = emptyList(),
    ) {
        data class Style(
            val key: String = "",
            val label: String = "",
            val text: String = "",
        )
    }

    data class ActionItem(
        /** 小动作 | 观察窗口 | 停止条件 */
        val label: String = "",
        val text: String = "",
    )
}

/**
 * 老五步法 → v2 结构（prompt-architecture §5 兼容映射）：
 * emotion.content→empathy；facts.items→known；advice.content→core、advice.items+interests.items→reasons；
 * reply→单条 styles（稳健）；action.items→actions（label=小动作）。
 */
fun FiveStepAnalysis.toCoachAnalysis(): CoachAnalysis {
    val byKey = steps.associateBy { it.key }
    val adviceStep = byKey["advice"]
    val interestsStep = byKey["interests"]
    val styles = if (reply.isNotBlank()) {
        listOf(CoachAnalysis.Advice.Style(key = "steady", label = "稳健", text = reply))
    } else {
        emptyList()
    }
    return CoachAnalysis(
        inputKind = inputKind,
        empathy = byKey["emotion"]?.content.orEmpty(),
        reply = reply,
        replyTiming = replyTiming,
        facts = CoachAnalysis.Facts(
            known = byKey["facts"]?.items.orEmpty(),
            assumed = emptyList(),
            unknown = emptyList(),
        ),
        advice = CoachAnalysis.Advice(
            tag = "",
            core = adviceStep?.content.orEmpty(),
            reasons = (adviceStep?.items.orEmpty() + interestsStep?.items.orEmpty()).distinct(),
            styles = styles,
        ),
        actions = byKey["action"]?.items.orEmpty().map { CoachAnalysis.ActionItem(label = "小动作", text = it) },
        citations = citations,
        safetyOverride = safetyOverride,
        safetyMessage = safetyMessage,
        tokenEstimate = tokenEstimate,
    )
}
