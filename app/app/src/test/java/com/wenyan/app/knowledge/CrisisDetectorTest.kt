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
        // F65 修复：原断言 only isNotEmpty——即使词表混入 emoji 关键词也照样通过，且未校验
        // 命中集合本身。现按 phrases 表顺序固定整条输入的命中集（「威胁」走 compoundKeywords
        // 白名单不命中；单独的「控制」不命中「控制我/财务控制」），词表被改动/污染时即红
        val all = CrisisDetector.detect("家暴跟踪胁迫自伤自杀威胁控制强奸勒索偷拍")
        assertEquals(
            listOf("家暴", "跟踪", "胁迫", "自伤", "自杀", "强奸", "勒索", "偷拍"),
            all,
        )
    }
}
