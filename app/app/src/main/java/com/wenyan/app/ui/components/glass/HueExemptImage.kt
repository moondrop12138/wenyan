package com.wenyan.app.ui.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import com.wenyan.app.ui.theme.LocalFluidHue

/**
 * v1.9.5 用户位图色相豁免（HueExemptImage）· 主窗口全局色相层的局部逆操作
 *
 * 根因：色相跟随 = MainActivity 在**主窗口内容根部**挂的全局 hue-rotate 图层
 * （drawWithContent + saveLayer(paint.colorFilter)，矩阵为 [hueRotateColorMatrix] 的 W3C
 * 亮度保持算子）。该层罩住 AppRoot 整树——聊天图片气泡（MessageBubble.kt
 * ImageMessageBubble）与输入栏待发送缩略图（ChatInputBar.kt）在子树内，被连带转色，
 * 用户照片色相失真；全屏预览（ImagePreviewOverlay 走 Dialog 独立窗口）在层外，本就不受
 * 影响，故不动。
 *
 * 做法：同一算子取反角度即得逆矩阵（旋转矩阵的逆 = 反向旋转；
 * [hueRotateColorMatrix] 内部已 `mod 360`，传 `-hue` 即正确），在位图外再套一层
 * drawWithContent + saveLayer——绘制先经逆矩阵、再经全局层正矩阵，净效果 = 原图。
 * 往返量化偏差 ≤1/255（与 [hueRotated] 文档既有记录同口径，用户照片不可见）。
 *
 * 开销：hue == 0（[FLUID_HUE_DEFAULT] 默认值）直接 content() 原样透传零开销；
 * 非 0 时 Paint 经 remember(hue) 缓存，与 MainActivity 全局层写法同形。
 *
 * 不碰 GtjWindowTheme 与 Palette 逻辑（独立窗口取色跟随 untouched），不碰 ViewModel 与按钮。
 */
@Composable
fun HueExemptImage(content: @Composable () -> Unit) {
    val hue = LocalFluidHue.current
    if (hue == FLUID_HUE_DEFAULT) {
        // 默认色相：原样透传零开销
        content()
    } else {
        val inversePaint = remember(hue) {
            android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    hueRotateColorMatrix(-hue.toFloat()).values,
                )
            }
        }
        Box(
            modifier = Modifier.drawWithContent {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val save = native.saveLayer(null, inversePaint)
                    drawContent()
                    native.restoreToCount(save)
                }
            },
        ) {
            content()
        }
    }
}
