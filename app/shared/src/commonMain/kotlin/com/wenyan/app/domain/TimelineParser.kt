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

    /** 按时间升序（时间格式为 "yyyy-MM" 之类可比字符串；非法时间排后） */
    fun sorted(timelineJson: String): List<Event> =
        parse(timelineJson).sortedWith(compareBy { it.time })
}
