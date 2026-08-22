package com.wenyan.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 无障碍辅助（design-tokens motion.reducedMotion：系统"移除动画"时所有动效降为 0ms 或静态）。
 * 检测系统三项动画缩放（"移除动画"无障碍开关会把它们全部置 0），返回 true 表示应关闭动效。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    // L32 修复：原 remember(context) 进程内缓存永不刷新——用户开/关「移除动画」后
    // 不重启则 UI 永远沿用旧值。改监听三个缩放键的 ContentObserver，变化即重组刷新。
    var reduced by remember { mutableStateOf(context.isAnimationsRemoved()) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper()),
        ) {
            override fun onChange(selfChange: Boolean) {
                reduced = context.isAnimationsRemoved()
            }
        }
        val keys = listOf(
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
            android.provider.Settings.Global.WINDOW_ANIMATION_SCALE,
        )
        keys.forEach { key ->
            resolver.registerContentObserver(
                android.net.Uri.parse("content://settings/global/" + key),
                false,
                observer,
            )
        }
        reduced = context.isAnimationsRemoved()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
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
