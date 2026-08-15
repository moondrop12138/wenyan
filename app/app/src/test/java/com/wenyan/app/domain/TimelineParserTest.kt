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
}
