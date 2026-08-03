package com.goutoujunshi.app.knowledge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA 独立补充：危机检测边界用例（AC-13 安全边界）
 *
 * 覆盖三类风险：
 * 1. 危机关键词命中（家暴/跟踪/胁迫/自伤）
 * 2. 单字不误伤（"家"不应触发）
 * 3. 自伤类间接措辞（"我有点想不开"类）—— 期望按 AC-13 语义触发转介
 *
 * 本文件由 QA 独立编写，不依赖实现内部假设。
 */
class CrisisDetectorBoundaryTest {

    // ---- 命中：危机关键词必须触发 ----

    @Test
    fun `domestic violence phrases hit`() {
        assertTrue("家暴应命中", CrisisDetector.isCrisis("他经常家暴我"))
        assertTrue("被家暴应命中", CrisisDetector.isCrisis("我最近被家暴了"))
    }

    @Test
    fun `stalking and coercion phrases hit`() {
        assertTrue("跟踪应命中", CrisisDetector.isCrisis("我感觉被人跟踪"))
        assertTrue("尾随应命中", CrisisDetector.isCrisis("他天天尾随我"))
        assertTrue("胁迫应命中", CrisisDetector.isCrisis("他胁迫我必须复合"))
    }

    @Test
    fun `self harm phrases hit`() {
        assertTrue("自伤应命中", CrisisDetector.isCrisis("我控制不住想自伤"))
        assertTrue("自杀应命中", CrisisDetector.isCrisis("我最近总想自杀"))
        assertTrue("活不下去应命中", CrisisDetector.isCrisis("真的活不下去了"))
    }

    // ---- 不误伤：单字"家"不得触发（防过度拦截） ----

    @Test
    fun `single han zi jia must not be flagged`() {
        assertFalse("单字家不应命中", CrisisDetector.isCrisis("他家在附近"))
        assertFalse("想回家不应命中", CrisisDetector.isCrisis("我想回家了"))
        assertFalse("回家不应命中", CrisisDetector.isCrisis("周末打算回家"))
    }

    @Test
    fun `normal relationship talk not flagged`() {
        assertFalse(CrisisDetector.isCrisis("她对我有点冷淡，我想主动一点"))
        assertFalse(CrisisDetector.isCrisis("我们聊得挺好的"))
        assertFalse(CrisisDetector.isCrisis("他工作压力大，最近话少了"))
    }

    // ---- 自伤类间接措辞（AC-13 语义：自伤等危机必须转介） ----

    @Test
    fun `indirect self harm phrasing should be flagged`() {
        // "想不开" 属于自伤意念的常见表述，按 AC-13"自伤等"语义应触发转介
        assertTrue("我有点想不开应命中", CrisisDetector.isCrisis("我有点想不开"))
        assertTrue("撑不下去了应命中", CrisisDetector.isCrisis("我真的撑不下去了"))
    }

    // ---- 裸词变体：无"了"后缀也必须命中（QA 第二轮 advisory） ----

    @Test
    fun `bare crisis phrases without le suffix hit`() {
        assertTrue("坚持不下去应命中", CrisisDetector.isCrisis("我快坚持不下去"))
        assertTrue("撑不下去应命中", CrisisDetector.isCrisis("我真的撑不下去"))
        assertTrue("活不下去应命中", CrisisDetector.isCrisis("活不下去"))
    }
}
