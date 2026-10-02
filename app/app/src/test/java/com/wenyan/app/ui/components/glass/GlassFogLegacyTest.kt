package com.wenyan.app.ui.components.glass

import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GLASS_BLUR_DEFAULT
import com.wenyan.app.ui.theme.GLASS_BLUR_MAX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 雾化兜底 alpha（[glassFogAlpha]）的纯 JVM 钉值自检（同
 * [GlassTransparencyReadabilityTest] 惯例：断言生产函数输出，不在测试内复刻公式）。
 *
 * 背景：API<31（RenderEffect 门槛，判定唯一来源 [glassRenderMode]）设备上无 backdrop 模糊
 * 消费方，锁死的玻璃模糊半径（GLASS_BLUR_DEFAULT 100dp，v1.9.4 收尾移除滑条）在这些场景
 * 改以雾化浓度近似响应（v1.9.4 探测降级为观测后 31+ 恒真实模糊、不再落雾化）。护栏意图：雾 alpha
 * 必须严格随半径单调、0dp 恒无雾（拖到 0 = 不模糊
 * 的语义钉死）、满档不超过 [GlassBackdropParams.FOG_ALPHA_MAX]=0.92（保留 8% 透底，与
 * 「磨实」区分）、与磨砂度滑条零耦合（函数无 palette 输入，雾 alpha 不随磨砂度漂移）。
 * v1.9.4 修复加浓：线性改 √ 曲线——旧默认档线性值 0.17 几乎不可见，兜底失效；√ 曲线下
 * 默认/满档 = 12/60 = 20/100 = 100/500 = 0.2（三改量程同比例），默认档钉值 0.41 不随量程漂移
 * （肉眼明显）。
 */
class GlassFogLegacyTest {

    /** 默认/最低档 100dp（GLASS_BLUR_DEFAULT）钉值：0.92 × √(100/500) ≈ 0.4114（明显可见）。 */
    @Test
    fun `default sand grade fog alpha is pinned`() {
        assertEquals(0.4114f, glassFogAlpha(GLASS_BLUR_DEFAULT.dp, GLASS_BLUR_MAX.dp), 1e-3f)
    }

    /** 滑条拖到 0 = 完全无雾（模糊度 0 的语义：不模糊也不加雾；纯函数域含低于量程的值）。 */
    @Test
    fun `zero blur means no fog`() {
        assertEquals(0f, glassFogAlpha(0.dp, GLASS_BLUR_MAX.dp), 0f)
    }

    /** 满档 500dp 钉在 FOG_ALPHA_MAX=0.92：接近磨实但保留 8% 透底。 */
    @Test
    fun `full scale saturates at FOG_ALPHA_MAX`() {
        assertEquals(0.92f, glassFogAlpha(500.dp, GLASS_BLUR_MAX.dp), 1e-6f)
        assertEquals(GlassBackdropParams.FOG_ALPHA_MAX, glassFogAlpha(500.dp, GLASS_BLUR_MAX.dp), 0f)
    }

    /** √ 曲线在量程低端比线性浓（最低档 100dp 加浓到 ≈0.41，兜底肉眼可见）。 */
    @Test
    fun `sqrt curve is denser than old linear at low radius`() {
        val linear = 0.92f * (100f / GLASS_BLUR_MAX)
        val sqrtCurve = glassFogAlpha(100.dp, GLASS_BLUR_MAX.dp)
        assertEquals("100dp 钉值（√0.2×0.92）", 0.4114f, sqrtCurve, 1e-3f)
        assertTrue("100dp 雾化应比线性（$linear）明显加浓", sqrtCurve > linear * 2f)
    }

    /** 越界值夹取到 [0, max]（滑条已受限，此处兜底防越界输入放大雾层）。 */
    @Test
    fun `out of range blur is clamped`() {
        assertEquals(0f, glassFogAlpha((-5).dp, GLASS_BLUR_MAX.dp), 0f)
        assertEquals(0.92f, glassFogAlpha(600.dp, GLASS_BLUR_MAX.dp), 1e-6f)
        assertEquals(0.92f, glassFogAlpha(1000.dp, GLASS_BLUR_MAX.dp), 1e-6f)
    }

    /** 上限为 0 的防御路径：无滑条量程即无雾（防除零）。 */
    @Test
    fun `zero max scale yields no fog`() {
        assertEquals(0f, glassFogAlpha(12.dp, 0.dp), 0f)
    }

    /** 单调性全量程扫描：任意 1dp 步进都不得出现回退或跳变（拖动反馈连续）。 */
    @Test
    fun `fog alpha is monotonic across full scale`() {
        var prev = -1f
        for (blur in 0..GLASS_BLUR_MAX) {
            val a = glassFogAlpha(blur.dp, GLASS_BLUR_MAX.dp)
            assertTrue("blur=$blur 回退：$a < $prev", a >= prev)
            prev = a
        }
    }
}
