package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 危机关键词检测测试（AC-13 安全边界）
 */
class CrisisDetectorTest {

    @Test
    fun `detects domestic violence keyword`() {
        assertTrue(CrisisDetector.isCrisis("他经常家暴我"))
        assertTrue(CrisisDetector.detect("被家暴了").contains("家暴"))
    }

    @Test
    fun `detects stalking keyword`() {
        assertTrue(CrisisDetector.isCrisis("我好像被人跟踪了"))
    }

    @Test
    fun `detects self harm keyword`() {
        assertTrue(CrisisDetector.isCrisis("我最近总想自杀"))
        assertTrue(CrisisDetector.isCrisis("活不下去了"))
    }

    @Test
    fun `detects threat and coercion`() {
        assertTrue(CrisisDetector.isCrisis("他威胁我要发我的照片"))
        assertTrue(CrisisDetector.isCrisis("他强迫我做那种事"))
    }

    @Test
    fun `normal love talk not flagged`() {
        assertFalse(CrisisDetector.isCrisis("他最近对我很冷淡，要不要主动一点"))
        assertFalse(CrisisDetector.isCrisis("我们约会很开心"))
    }

    @Test
    fun `blank input not flagged`() {
        assertFalse(CrisisDetector.isCrisis(""))
        assertFalse(CrisisDetector.isCrisis("   "))
    }

    @Test
    fun `keywords are pure text no emoji`() {
        val all = CrisisDetector.detect("家暴跟踪胁迫自伤自杀威胁控制强奸勒索偷拍")
        assertTrue(all.isNotEmpty())
    }
}
