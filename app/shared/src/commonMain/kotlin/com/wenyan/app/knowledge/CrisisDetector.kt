package com.wenyan.app.knowledge

import com.wenyan.app.log.AppLogger

/**
 * 危机关键词本地预检（SPEC §5.2 / AC-13 / SKILL.md 安全边界）
 *
 * 命中 → UI 触发安全转介（安全计划 + 当地紧急服务），不输出恋爱话术。
 * 这是 LLM 输出 safety_override 之外的本地第一道防线：用户输入即先拦。
 * 纯 JVM 可测。
 */
object CrisisDetector {

    // 多字短语：语境明确，直接 contains 命中。
    // M14 修复：「被打」「报复」「受不了」三个高频歧义词从 phrases 移入 compoundKeywords——
    // 原无语境 contains 硬匹配把「我不想被打扰」「报复性熬夜」「他太吵了我受不了」等
    // 日常用语也拦进安全卡（硬短路：不落库、不调 LLM），直接阻断正常对话。
    private val phrases = listOf(
        "家暴", "打我",
        "跟踪", "被跟踪", "尾随",
        "胁迫", "强迫我", "逼我", "恐吓",
        "自伤", "自杀", "不想活", "活不下去", "想死",
        "想不开", "撑不下去", "坚持不下去", "坚持不下去了",
        "伤害自己", "割腕", "轻生",
        "杀人", "伤害他",
        "财务控制", "控制我", "软禁", "囚禁",
        "强奸", "性侵", "被下药",
        "勒索", "偷拍", "裸照威胁",
        // M5 漏词扩充（「受不了」移入白名单表）
        "跳楼", "遗书", "安眠药", "结束自己", "撑不住",
    )

    // M5/M14: 短词/歧义词白名单——仅在命中组合词时判定，避免「抹杀/秒杀/威胁论/电影没意思/
    // 我不想被打扰/报复性熬夜」误报
    private val compoundKeywords = mapOf(
        "杀" to listOf("杀害", "想杀", "杀了我", "杀了他", "杀死", "杀掉"),
        "威胁" to listOf("威胁我", "威胁你", "威胁分手", "威胁自杀"),
        "没意思" to listOf("活着没意思", "人生没意思", "生活没意思"),
        // M14: 高频歧义词收窄为真实暴力/危机组合
        "被打" to listOf("被打哭", "被打伤", "被打得", "天天被打", "被打进医院", "回家被打"),
        "报复" to listOf("报复我", "报复他", "报复她", "报复你", "说要报复"),
        "受不了" to listOf("受不了了想死", "受不了想结束", "真的受不了想", "受不了要崩溃想"),
    )

    /**
     * 检测文本是否命中危机关键词
     * @return 命中的关键词列表（空 = 未命中）
     */
    fun detect(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val hits = mutableListOf<String>()
        for (p in phrases) if (text.contains(p)) hits.add(p)
        for ((word, whitelist) in compoundKeywords) {
            if (whitelist.any { text.contains(it) }) hits.add(word)
        }
        val result = hits.distinct()
        // 隐私优先：只记录固定词典命中的关键词类型与数量，绝不记录用户输入原文
        if (result.isNotEmpty()) {
            AppLogger.w("crisis_detected", "hit" to result.first(), "hit_count" to result.size)
        }
        return result
    }

    fun isCrisis(text: String): Boolean = detect(text).isNotEmpty()
}
