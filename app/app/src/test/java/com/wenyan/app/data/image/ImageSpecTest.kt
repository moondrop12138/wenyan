package com.wenyan.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片规格纯逻辑测试（SPEC §5.3：最长边 ≤1568px / 20MB / token 估算）
 */
class ImageSpecTest {

    @Test
    fun `small image keeps original size`() {
        val plan = ImageSpec.planResize(800, 600)
        assertEquals(1, plan.inSampleSize)
        assertEquals(800, plan.targetWidth)
        assertEquals(600, plan.targetHeight)
    }

    @Test
    fun `wide image scaled to max edge 1568`() {
        val plan = ImageSpec.planResize(3200, 1800)
        assertTrue(plan.targetWidth <= 1568)
        assertTrue(plan.targetHeight <= 1568)
        // 宽高比保持：3200:1800 = 16:9
        assertEquals(1568, plan.targetWidth)
        assertEquals(882, plan.targetHeight)
    }

    @Test
    fun `tall image scaled to max edge 1568`() {
        val plan = ImageSpec.planResize(1080, 2400)
        assertTrue(plan.targetHeight <= 1568)
        assertEquals(706, plan.targetWidth)
        assertEquals(1568, plan.targetHeight)
    }

    @Test
    fun `inSampleSize never exceeds power of two`() {
        val plan = ImageSpec.planResize(10000, 10000)
        assertTrue(plan.inSampleSize == 1 || plan.inSampleSize == 2 ||
            plan.inSampleSize == 4 || plan.inSampleSize == 8 ||
            plan.inSampleSize == 16)
    }

    @Test
    fun `too large check 20MB boundary`() {
        assertFalse(ImageSpec.isTooLarge(20L * 1024 * 1024))
        assertTrue(ImageSpec.isTooLarge(20L * 1024 * 1024 + 1))
    }

    @Test
    fun `token estimate formula`() {
        // 1568x1568 = 2458624/750 = 3278.16 → ceil = 3279
        assertEquals(3279, ImageSpec.estimateTokens(1568, 1568))
        // 800x600 = 480000/750 = 640
        assertEquals(640, ImageSpec.estimateTokens(800, 600))
    }

    @Test
    fun `invalid dimensions throw`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ImageSpec.planResize(0, 100)
        }
    }
}
