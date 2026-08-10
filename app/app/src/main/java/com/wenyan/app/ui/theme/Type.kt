package com.wenyan.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号体系唯一来源：docs/design-tokens.json typography.typeScale。
 * 槽位映射：display→displaySmall / headline→headlineLarge / title→titleLarge /
 * subtitle→titleMedium / body→bodyLarge / bodySm→bodyMedium / label→labelLarge /
 * caption→labelMedium。mono 供 API Key / Base URL 等固定宽度场景。
 */
object GtjType {
    /** v1.4：标题字重 600→500，高级感来自克制而非粗重；Display 字距收紧至 -0.8 */
    val Display = TextStyle(
        fontSize = 32.sp, fontWeight = FontWeight.Medium,
        lineHeight = 40.sp, letterSpacing = (-0.8).sp,
    )
    val Headline = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.Medium,
        lineHeight = 32.sp, letterSpacing = 0.sp,
    )
    val Title = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.Medium,
        lineHeight = 28.sp, letterSpacing = 0.sp,
    )
    val Subtitle = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Medium,
        lineHeight = 24.sp, letterSpacing = 0.sp,
    )
    val Body = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Normal,
        lineHeight = 24.sp, letterSpacing = 0.sp,
    )
    val BodySm = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp, letterSpacing = 0.sp,
    )
    val Label = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight.Medium,
        lineHeight = 18.sp, letterSpacing = 0.3.sp,
    )
    val Caption = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Normal,
        lineHeight = 16.sp, letterSpacing = 0.2.sp,
    )
    val Mono = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal,
        lineHeight = 20.sp, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace,
    )
}

/**
 * v1.8.2 editorial 衬线字体系（编辑排版风）：
 * 仅用于「空状态大标题 / 回答文章标题与引文 / 序号数字」，正文保持 sans。
 * 零资源方案：FontFamily.Serif 走系统衬线（Android 通常回退思源宋体/Noto Serif），
 * 桌面共享层不涉及（桌面版前端 CSS 自带 serif 栈）。
 */
object EditorialType {
    /** 刊头大标题：空状态「今天想聊点什么？」/ 回答文章主标题（30sp Bold，两行内） */
    val Display = TextStyle(
        fontSize = 30.sp, fontWeight = FontWeight.Bold,
        lineHeight = 41.sp, letterSpacing = (-0.5).sp, fontFamily = FontFamily.Serif,
    )
    /** 军师建议引文级强调（大字衬线，可作 pullquote） */
    val Quote = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 29.sp, letterSpacing = 0.sp, fontFamily = FontFamily.Serif,
    )
    /** 衬线序号：索引壹/贰/叁、行动清单 01/02、理由编号 1.2.3 */
    val No = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight.Bold,
        lineHeight = 23.sp, letterSpacing = 0.sp, fontFamily = FontFamily.Serif,
    )
}

/** Material3 Typography 映射（供 M3 组件使用，如 Button/TextField）。 */
val GtjTypography = Typography(
    displaySmall = GtjType.Display,
    headlineLarge = GtjType.Headline,
    titleLarge = GtjType.Title,
    titleMedium = GtjType.Subtitle,
    bodyLarge = GtjType.Body,
    bodyMedium = GtjType.BodySm,
    labelLarge = GtjType.Label,
    labelMedium = GtjType.Caption,
)
