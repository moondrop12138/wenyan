package com.wenyan.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 无障碍辅助（design-tokens motion.reducedMotion：系统"移除动画"时所有动效降为 0ms 或静态）。
 * 检测系统三项动画缩放（"移除动画"无障碍开关会把它们全部置 0），返回 true 表示应关闭动效。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.isAnimationsRemoved()
    }
}

/** 纯函数：读取系统动画缩放设置（供 Compose 组合与单元测试复用）。 */
fun Context.isAnimationsRemoved(): Boolean {
    val resolver = contentResolver
    fun scale(key: String): Float =
        try {
            Settings.Global.getFloat(resolver, key, 1f)
        } catch (_: SecurityException) {
            1f
        }
    val animator = scale(Settings.Global.ANIMATOR_DURATION_SCALE)
    val transition = scale(Settings.Global.TRANSITION_ANIMATION_SCALE)
    val window = scale(Settings.Global.WINDOW_ANIMATION_SCALE)
    return animator == 0f || transition == 0f || window == 0f
}
