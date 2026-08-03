package com.goutoujunshi.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors
import com.goutoujunshi.app.ui.theme.rememberReducedMotion
import androidx.compose.material3.Text

/**
 * 流式状态条（design-pages 页面1）：三点思考指示（typingDot 150ms 错峰）+ 光标（typingCursor 900ms）。
 * 纯装饰，无业务；停止后由父级移除。系统"移除动画"时渲染静态三点（motion.reducedMotion）。
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val p = LocalGtjColors.current
    val reducedMotion = rememberReducedMotion()
    val transition = if (reducedMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "typing")
    }
    val dotAlpha = if (reducedMotion) {
        1f
    } else {
        transition!!.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "dot",
        ).value
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Dot(p.accent, dotAlpha, 0, reducedMotion)
            Dot(p.accent, dotAlpha, 150, reducedMotion)
            Dot(p.accent, dotAlpha, 300, reducedMotion)
        }
        Spacer(Modifier.width(8.dp))
        Text("正在分析", style = GtjType.BodySm, color = p.muted)
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color, baseAlpha: Float, phaseMs: Int, reducedMotion: Boolean) {
    val phase = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "dotPhase")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450, delayMillis = phaseMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "phase",
        ).value
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(baseAlpha * (0.5f + 0.5f * phase))
            .background(color = color, shape = CircleShape),
    )
}
