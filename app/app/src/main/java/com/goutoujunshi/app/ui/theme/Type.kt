package com.goutoujunshi.app.ui.theme

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
    val Display = TextStyle(
        fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 40.sp, letterSpacing = (-0.5).sp,
    )
    val Headline = TextStyle(
        fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp, letterSpacing = 0.sp,
    )
    val Title = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
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
