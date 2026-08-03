package com.goutoujunshi.app.knowledge

import com.goutoujunshi.app.log.AppLogger

/**
 * 危机关键词本地预检（SPEC §5.2 / AC-13 / SKILL.md 安全边界）
 *
 * 命中 → UI 触发安全转介（安全计划 + 当地紧急服务），不输出恋爱话术。
 * 这是 LLM 输出 safety_override 之外的本地第一道防线：用户输入即先拦。
 * 纯 JVM 可测。
 */
object CrisisDetector {

    private val keywords = listOf(
        "家暴", "家暴了", "被打", "打我",
        "跟踪", "被跟踪", "尾随",
        "胁迫", "强迫我", "逼我",
        "威胁", "威胁我", "恐吓",
        "自伤", "自杀", "不想活", "活不下去", "想死",
        "想不开", "撑不下去", "坚持不下去", "坚持不下去了", "没意思",
        "伤害自己", "割腕", "轻生",
        "杀", "杀人", "伤害他", "报复",
        "财务控制", "控制我", "软禁", "囚禁",
        "强奸", "性侵", "被下药",
        "勒索", "偷拍", "裸照威胁",
    )

    /**
     * 检测文本是否命中危机关键词
     * @return 命中的关键词列表（空 = 未命中）
     */
    fun detect(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val hits = keywords.filter { text.contains(it) }
        // 隐私优先：只记录固定词典命中的关键词类型与数量，绝不记录用户输入原文
        if (hits.isNotEmpty()) {
            AppLogger.w("crisis_detected", "hit" to hits.first(), "hit_count" to hits.size)
        }
        return hits
    }

    fun isCrisis(text: String): Boolean = detect(text).isNotEmpty()
}
