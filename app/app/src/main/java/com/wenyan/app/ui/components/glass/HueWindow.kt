package com.wenyan.app.ui.components.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wenyan.app.ui.theme.GtjPalette
import com.wenyan.app.ui.theme.LocalFluidHue
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.LocalGtjIsDark
import com.wenyan.app.ui.theme.darkColorScheme
import com.wenyan.app.ui.theme.lightColorScheme

/**
 * v1.9.4 独立窗口色相跟随（HueWindow）· 主窗口全局色相层的窗口外补齐
 *
 * 背景（根因）：色相跟随 = MainActivity 在**主窗口内容根部**挂的全局 hue-rotate 图层
 * （drawWithContent + saveLayer(paint.colorFilter)，矩阵为 [hueRotateColorMatrix] 的 W3C
 * 亮度保持算子，与桌面 styles.css:493 body 级 `filter: hue-rotate` 同语义）。该层只罩
 * **主窗口**子树——Dialog / AlertDialog / DropdownMenu（Popup）等是独立 Android 窗口，
 * 不在层内，取色拿到的是**未旋转**的暖色调色板（米色 #EFE6D8 / 棕褐 #2B221A / 橙
 * #A4551C），色相 ≠ 0 时与主界面色相不符。ModelSheet（ModalBottomSheet）此前已用
 * 「内容自套同一矩阵」修复（ModelSheet.kt:212-229，勿动）；本文件把该能力提炼成两个
 * 通用 API，供其余独立窗口（AlertDialog / DropdownMenu 等）低成本跟随。
 *
 * 依赖方向保持既有 glass → theme（本包 FluidAppearance.kt 同向）；色值一律经
 * [com.wenyan.app.ui.theme.GtjPalette] token 旋转，本文件不引入任何硬编码色值。
 */

/**
 * 独立窗口取色的色相跟随变换：**独立窗口不在主窗口全局色相层内**（MainActivity 内容根部的
 * saveLayer + W3C hue-rotate 图层只罩主窗口子树，Dialog/AlertDialog/DropdownMenu 等
 * 独立窗口取不到该层），直接读 [com.wenyan.app.ui.theme.LocalGtjColors] 会拿到未旋转的
 * 暖色调色板、与主界面色相不符——**取色前先经本函数逐字段旋转**。
 *
 * 逐字段 copy()：全部 47 个 Color 字段（含 glass*、glow*、fluid*、dot* 扩展槽位）各自经本包
 * 纯函数 [hueRotated]（Color, Float）旋转——与主窗口全局层（hueRotateColorMatrix）、
 * ModelSheet 自套矩阵同一条 W3C 亮度保持算子，两端同一个色相数字 = 同一种色相语义。
 * 函数体内 `hueRotated(field, degrees)` 按参数类型解析到 (Color, Float) 纯函数重载，
 * 与本扩展（GtjPalette 接收者）互不遮蔽。矩阵只转 RGB 行：alpha 全程不动（玻璃半透明
 * 填充/描边浓度不漂移）、纯灰/纯白原样返回（accentOn 白等中性色不偏移）。
 *
 * **0 度逐字段恒等**：[hueRotated]（Color, Float）在 degrees 为 0/±360 整数倍时对每色
 * 原样返回，故 hue == 0 时本函数返回与入参逐字段相等的调色板——默认色相下取色结果与
 * 不旋转完全一致（LocalFluidHue 语义同源，见 Color.kt FLUID_HUE_* 量程注释）。
 *
 * 消费方式：整包主题跟随优先用 [GtjWindowTheme]（CompositionLocal 与 MaterialTheme 一起
 * 换）；仅个别手绘色（如 ModelSheet 拖拽条）才逐色调用 [hueRotated]（Color, Float）。
 */
fun GtjPalette.hueRotated(hue: Int): GtjPalette {
    val degrees = hue.toFloat()
    return copy(
        bg = hueRotated(bg, degrees),
        surface = hueRotated(surface, degrees),
        surfaceElevated = hueRotated(surfaceElevated, degrees),
        fg = hueRotated(fg, degrees),
        fgSecondary = hueRotated(fgSecondary, degrees),
        muted = hueRotated(muted, degrees),
        meta = hueRotated(meta, degrees),
        border = hueRotated(border, degrees),
        borderSoft = hueRotated(borderSoft, degrees),
        accent = hueRotated(accent, degrees),
        accentOn = hueRotated(accentOn, degrees),
        accentHover = hueRotated(accentHover, degrees),
        accentPressed = hueRotated(accentPressed, degrees),
        accentSoft = hueRotated(accentSoft, degrees),
        success = hueRotated(success, degrees),
        warn = hueRotated(warn, degrees),
        danger = hueRotated(danger, degrees),
        dangerSoft = hueRotated(dangerSoft, degrees),
        warm = hueRotated(warm, degrees),
        warmSoft = hueRotated(warmSoft, degrees),
        warmOn = hueRotated(warmOn, degrees),
        scrim = hueRotated(scrim, degrees),
        glassFill = hueRotated(glassFill, degrees),
        glassFillStrong = hueRotated(glassFillStrong, degrees),
        glassBorder = hueRotated(glassBorder, degrees),
        glassEdgeHighlight = hueRotated(glassEdgeHighlight, degrees),
        glassShadow = hueRotated(glassShadow, degrees),
        glassCardFillTop = hueRotated(glassCardFillTop, degrees),
        glassCardFillBottom = hueRotated(glassCardFillBottom, degrees),
        glassShadowTopBar = hueRotated(glassShadowTopBar, degrees),
        glassShadowInputBar = hueRotated(glassShadowInputBar, degrees),
        glassFocusRing = hueRotated(glassFocusRing, degrees),
        glassShadowNear = hueRotated(glassShadowNear, degrees),
        glassInputFill = hueRotated(glassInputFill, degrees),
        glassInsetShade = hueRotated(glassInsetShade, degrees),
        glassInsetHighlight = hueRotated(glassInsetHighlight, degrees),
        glassChip = hueRotated(glassChip, degrees),
        glowA = hueRotated(glowA, degrees),
        glowB = hueRotated(glowB, degrees),
        glowC = hueRotated(glowC, degrees),
        fluidA = hueRotated(fluidA, degrees),
        fluidB = hueRotated(fluidB, degrees),
        fluidC = hueRotated(fluidC, degrees),
        dotConnected = hueRotated(dotConnected, degrees),
        dotConnecting = hueRotated(dotConnecting, degrees),
        dotThinking = hueRotated(dotThinking, degrees),
        dotFailure = hueRotated(dotFailure, degrees),
    )
}

/**
 * 独立窗口的色相跟随主题包装：**把每个独立窗口（Dialog/AlertDialog/DropdownMenu 等）的
 * 内容包进本组合即可**——包内 [com.wenyan.app.ui.theme.LocalGtjColors] 与 MaterialTheme
 * 自动跟随全局色相，窗口内取色/取 M3 槽位与主界面同色相，无需逐处改色。
 *
 * 实现：
 * - hue == 0（[com.wenyan.app.ui.theme.LocalFluidHue] 默认值）：直接 content() 原样透传，
 *   零开销（不读调色板、不触碰 MaterialTheme，观感与不包裹逐位一致）；
 * - hue != 0：调色板经 [GtjPalette.hueRotated] 逐字段旋转得 p，同时下发两路取色——
 *   CompositionLocalProvider(LocalGtjColors provides p)（扩展槽位）+ MaterialTheme 的
 *   M3 ColorScheme（isDark 来自 [com.wenyan.app.ui.theme.LocalGtjIsDark]，映射用
 *   Color.kt 既有 darkColorScheme/lightColorScheme，映射关系不在此处重复）。
 *   LocalFluidHue 随 composition 传播进独立窗口（ModelSheet.kt:214-215 既有实证），
 *   故在窗口内容里读到的就是真实全局色相。
 *
 * **typography 与 shapes 必须显式透传**：独立窗口里重新装配 MaterialTheme 时若只传
 * colorScheme，排版/形状会退回 M3 库默认（弹窗字号字重与主界面不符），故先取
 * MaterialTheme.typography / MaterialTheme.shapes 再原样传入（与 GtjTheme 装配同形）。
 *
 * 用法示例（包住每个独立窗口的内容即可）：
 * ```
 * // AlertDialog：包住整个调用
 * GtjWindowTheme {
 *     AlertDialog(
 *         onDismissRequest = onDismiss,
 *         confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
 *         title = { Text("标题") },
 *         text = { Text("正文") },
 *     )
 * }
 *
 * // DropdownMenu：包住菜单项内容
 * DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
 *     GtjWindowTheme {
 *         DropdownMenuItem(text = { Text("菜单项") }, onClick = onPick)
 *     }
 * }
 * ```
 */
@Composable
fun GtjWindowTheme(content: @Composable () -> Unit) {
    val hue = LocalFluidHue.current
    if (hue == 0) {
        // 默认色相：原样透传（LocalFluidHue 默认 FLUID_HUE_DEFAULT = 0，零开销路径）
        content()
    } else {
        val p = LocalGtjColors.current.hueRotated(hue)
        val isDark = LocalGtjIsDark.current
        // MaterialTheme 调用必须同时透传现有 typography 与 shapes——先取再传，
        // 否则弹窗排版/形状退回 M3 库默认
        val typography = MaterialTheme.typography
        val shapes = MaterialTheme.shapes
        CompositionLocalProvider(LocalGtjColors provides p) {
            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme(p) else lightColorScheme(p),
                typography = typography,
                shapes = shapes,
                content = content,
            )
        }
    }
}
