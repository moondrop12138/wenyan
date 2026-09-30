package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O2: 时间线解析测试 */
class TimelineParserTest {

    @Test
    fun `sorted by time`() {
        val json = "[{\"time\":\"2026-08\",\"event\":\"约会\"},{\"time\":\"2026-07\",\"event\":\"认识\"}]"
        val events = TimelineParser.sorted(json)
        assertEquals(listOf("认识", "约会"), events.map { it.event })
    }

    @Test
    fun `invalid json returns empty`() {
        assertTrue(TimelineParser.sorted("not-json").isEmpty())
    }

    @Test
    fun `blank returns empty`() {
        assertTrue(TimelineParser.sorted("").isEmpty())
    }

    @Test
    fun `numeric order - 2026-7 sorts before 2026-10`() {
        // F62：修复前的纯字典序实现下本用例同样通过（"2026-07" < "2026-08" 字典序与数值序同向）；
        // 此用例钉住 L4 数值序行为——「2026-7」必须排在「2026-10」之前（字典序会反过来）
        val json = "[{\"time\":\"2026-10\",\"event\":\"十月\"},{\"time\":\"2026-7\",\"event\":\"七月\"}]"
        val events = TimelineParser.sorted(json)
        assertEquals(listOf("七月", "十月"), events.map { it.event })
    }

    @Test
    fun `non numeric time sorts last`() {
        // F62：L4 修复的「非法串排尾」行为——无数字段时间（如 "abc"）不参与数值比较、稳定排最后
        val json = "[{\"time\":\"abc\",\"event\":\"无日期\"},{\"time\":\"2025-01\",\"event\":\"一月\"}]"
        val events = TimelineParser.sorted(json)
        assertEquals(listOf("一月", "无日期"), events.map { it.event })
    }

    @Test
    fun `parse drops blank time or event and non object elements`() {
        // F62：parse 的过滤语义——空 time/空 event 条目丢弃、非对象元素跳过
        val json = "[" +
            "{\"time\":\"\",\"event\":\"无时间\"}," +
            "{\"time\":\"2025-01\",\"event\":\"\"}," +
            "{\"time\":\"2025-02\",\"event\":\"二月\"}," +
            "\"junk\"]"
        val events = TimelineParser.sorted(json)
        assertEquals(listOf("二月"), events.map { it.event })
    }
}
