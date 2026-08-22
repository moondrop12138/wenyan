package com.wenyan.app.domain
import com.wenyan.app.json.Json

/**
 * O2: 档案时间线解析（target.timeline 为 JSON 数组 [{"time":"2026-07","event":"..."}]）。
 * 按 time 排序渲染纵向时间轴；空/非法数据返回空（UI 降级为现状）。
 */
object TimelineParser {

    data class Event(val time: String, val event: String)

    fun parse(timelineJson: String): List<Event> {
        if (timelineJson.isBlank()) return emptyList()
        return runCatching {
            val arr = Json.arr(timelineJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val time = obj.optString("time", "").trim()
                    val event = obj.optString("event", "").trim()
                    if (time.isNotEmpty() && event.isNotEmpty()) add(Event(time, event))
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 按时间升序。L4 修复：原纯字典序——「2026-7」排在「2026-10」之后、
     * 「1999年」「abc」等非格式串按首字符乱排；注释承诺的「非法时间排后」未实现。
     * 现解析为可比较数值（年*10000+月*100+日，缺省段补 1/01），非法串排到最后。
     */
    fun sorted(timelineJson: String): List<Event> =
        parse(timelineJson).sortedWith(compareBy { sortKey(it.time) })

    /** 时间 → 可比较数值 key：提取年/月/日数字，非法串返回 Long.MAX_VALUE 排尾 */
    private fun sortKey(time: String): Long {
        val nums = Regex("\\d+").findAll(time).map { it.value.toLongOrNull() ?: Long.MAX_VALUE }.toList()
        if (nums.isEmpty() || nums.any { it == Long.MAX_VALUE }) return Long.MAX_VALUE
        val year = nums.getOrNull(0) ?: 0L
        val month = (nums.getOrNull(1) ?: 1L)
        val day = (nums.getOrNull(2) ?: 1L)
        return year * 10000 + month * 100 + day
    }
}
