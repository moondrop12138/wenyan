package com.wenyan.app.ui.components.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模糊能力探测判定逻辑（[GlassBlurCapabilityProbe.isBlurRendered]/[GlassBlurCapabilityProbe.decide]）
 * 的纯 JVM 自检：合成像素帧进、布尔出。判定失败一律 fail-closed。
 * v1.9.4 探测降级为观测 + 加固：另钉 [GlassBlurCapabilityProbe.isSyncSuccess]（syncAndDraw
 * 返回值判定）、[GlassBlurCapabilityProbe.shouldRetryReadBack]（空读回重试边界）、
 * [GlassBlurCapabilityProbe.PROBE_TIMEOUT_MS]（超时兜底预算）与 [GlassBlurCapabilityProbe.decideDetail]
 * （观测日志的能量/比率明细）。
 */
class GlassBlurCapabilityProbeTest {

    private val size = 32

    /** 黑白竖条纹（周期 8px）——与真实探测源层同构的高对比高频图案。 */
    private fun stripes(): IntArray {
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            px[y * size + x] = if ((x / 4) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return px
    }

    /** 均匀灰场（无对比度）——源层没画出来的替身。 */
    private fun flat(): IntArray = IntArray(size * size) { 0xFF808080.toInt() }

    /** 简易盒式模糊（分离两趟）——「模糊真的渲染了」的替身。 */
    private fun boxBlur(px: IntArray, radius: Int): IntArray {
        fun channel(argb: Int, shift: Int): Int = (argb shr shift) and 0xFF
        val tmp = IntArray(px.size)
        val out = IntArray(px.size)
        for (y in 0 until size) for (x in 0 until size) {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (dx in -radius..radius) {
                val xx = (x + dx).coerceIn(0, size - 1)
                val c = px[y * size + xx]
                r += channel(c, 16); g += channel(c, 8); b += channel(c, 0); n++
            }
            tmp[y * size + x] = 0xFF000000.toInt() or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        for (y in 0 until size) for (x in 0 until size) {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (dy in -radius..radius) {
                val yy = (y + dy).coerceIn(0, size - 1)
                val c = tmp[yy * size + x]
                r += channel(c, 16); g += channel(c, 8); b += channel(c, 0); n++
            }
            out[y * size + x] = 0xFF000000.toInt() or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
        return out
    }

    /** 真模糊：条纹经模糊后边缘能量显著下降 → 判 true（真实模糊可用）。 */
    @Test
    fun `rendered blur is detected`() {
        val sharp = stripes()
        val blurred = boxBlur(sharp, radius = 3)
        assertTrue(GlassBlurCapabilityProbe.decide(sharp to blurred, size, size))
    }

    /** ROM 无视 RenderEffect：两帧逐位相同（能量比 = 1）→ 判 false（落雾化兜底）。 */
    @Test
    fun `identical frames mean effect ignored`() {
        val sharp = stripes()
        assertFalse(GlassBlurCapabilityProbe.decide(sharp to sharp.copyOf(), size, size))
    }

    /** 源层没画出来（渲染管线异常/null）→ fail-closed。 */
    @Test
    fun `null frames fail closed`() {
        assertFalse(GlassBlurCapabilityProbe.decide(null, size, size))
    }

    /** 源层无对比度 → 探测不可信 → fail-closed。 */
    @Test
    fun `flat source is untrusted`() {
        val flat = flat()
        assertFalse(GlassBlurCapabilityProbe.decide(flat to flat.copyOf(), size, size))
    }

    /** 帧尺寸/数量不一致 → fail-closed（防探测管线错误输出被当真）。 */
    @Test
    fun `mismatched frames fail closed`() {
        val sharp = stripes()
        assertFalse(GlassBlurCapabilityProbe.isBlurRendered(sharp, IntArray(size * size), size, size))
        assertFalse(GlassBlurCapabilityProbe.isBlurRendered(sharp, sharp.copyOf(), size + 1, size - 1))
    }

    // ── v1.9.4 探测加固（观测语义）可测部分 ──

    /** syncAndDraw 返回值判定（AOSP SyncAndDrawResult：SYNC_OK=0 才是干净出帧）。 */
    @Test
    fun `sync result zero is success and any flag is failure`() {
        assertTrue(GlassBlurCapabilityProbe.isSyncSuccess(0))
        // 1<<0 重绘请求 / 1<<1 surface 失效 / 1<<2 未出帧 / 1<<3 帧丢弃——非 0 一律失败
        assertFalse(GlassBlurCapabilityProbe.isSyncSuccess(1))
        assertFalse(GlassBlurCapabilityProbe.isSyncSuccess(2))
        assertFalse(GlassBlurCapabilityProbe.isSyncSuccess(4))
        assertFalse(GlassBlurCapabilityProbe.isSyncSuccess(8))
        assertFalse(GlassBlurCapabilityProbe.isSyncSuccess(-1))
    }

    /** 空读回重试边界：共 3 次尝试（0/1/2 次空后仍重试，第 3 次空后放弃）、间隔 20ms。 */
    @Test
    fun `read back retries exactly three attempts`() {
        assertEquals(3, GlassBlurCapabilityProbe.MAX_READ_BACK_ATTEMPTS)
        assertEquals(20L, GlassBlurCapabilityProbe.READ_BACK_RETRY_DELAY_MS)
        assertTrue(GlassBlurCapabilityProbe.shouldRetryReadBack(0))
        assertTrue(GlassBlurCapabilityProbe.shouldRetryReadBack(1))
        assertTrue(GlassBlurCapabilityProbe.shouldRetryReadBack(2))
        assertFalse(GlassBlurCapabilityProbe.shouldRetryReadBack(3))
    }

    /** 超时兜底预算 = 2s（超时也翻转出 FAILED/TIMEOUT，绝不停在 PROBING）。 */
    @Test
    fun `probe timeout budget is two seconds`() {
        assertEquals(2_000L, GlassBlurCapabilityProbe.PROBE_TIMEOUT_MS)
    }

    /** 观测日志明细（decideDetail）：真模糊带能量对（比率 <1），无视 RenderEffect 比率 ≈1。 */
    @Test
    fun `detail exposes energies for verdict logging`() {
        val sharp = stripes()
        val blurred = boxBlur(sharp, radius = 3)
        val real = GlassBlurCapabilityProbe.decideDetail(sharp to blurred, size, size)
        assertTrue(real.capable)
        assertTrue("eSharp 应为正", real.energySharp > 0.0)
        assertTrue("模糊帧能量应显著低于源（${real.energyBlurred} vs ${real.energySharp}）", real.energyBlurred < real.energySharp)
        val ignored = GlassBlurCapabilityProbe.decideDetail(sharp to sharp.copyOf(), size, size)
        assertFalse(ignored.capable)
        assertTrue("无视效果时比率应为 1（${ignored.energyBlurred / ignored.energySharp}）", ignored.energyBlurred / ignored.energySharp > 0.8)
    }

    /** fail-closed 路径（帧缺失/无对比度）能量记 0，日志可据此与真失败区分。 */
    @Test
    fun `detail on fail closed paths reports zero energies`() {
        val zero = GlassBlurCapabilityProbe.decideDetail(null, size, size)
        assertFalse(zero.capable)
        assertEquals(0.0, zero.energySharp, 0.0)
        assertEquals(0.0, zero.energyBlurred, 0.0)
        val flat = flat()
        val flatDetail = GlassBlurCapabilityProbe.decideDetail(flat to flat.copyOf(), size, size)
        assertFalse(flatDetail.capable)
        assertEquals("无对比度源能量恒 0", 0.0, flatDetail.energySharp, 0.0)
    }
}
