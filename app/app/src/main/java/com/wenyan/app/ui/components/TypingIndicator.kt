package com.wenyan.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.rememberReducedMotion

/**
 * 等待回复气泡（v1.9.2 对齐桌面端 .think-bubble）：玻璃容器 + 三档文案 + 三点呼吸动画。
 * 桌面端基准（styles.css:204-211）：圆角 16px / padding 9-13 / 11.5px 字 muted 色；
 * 3px 圆点 muted 色，breathe 1.2s（0%/100%: opacity .45 + scale .85；50%: opacity 1 + scale 1.15），
 * delay 0/.2s/.4s 错峰。纯装饰，无业务；停止后由父级移除。
 * 系统"移除动画"时渲染静态三点（reducedMotion）。
 */

/** 等待文案三档判定（confirming > transcribing > 普通），纯函数便于单测 */
fun resolveWaitingLabel(transcribing: Boolean, confirming: Boolean): String = when {
    confirming -> "军师分析中…"
    transcribing -> "视觉模型正在提取截图文字…"
    else -> "正在翻知识库，梳理你的处境…"
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    label: String = resolveWaitingLabel(transcribing = false, confirming = false),
) {
    val p = LocalGtjColors.current
    val reducedMotion = rememberReducedMotion()
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = GtjShape.lg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = GtjType.Caption.copy(fontSize = 11.5.sp),
                color = p.muted,
            )
            Spacer(Modifier.width(7.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
                BreatheDot(p.muted, 0, reducedMotion)
                BreatheDot(p.muted, 200, reducedMotion)
                BreatheDot(p.muted, 400, reducedMotion)
            }
        }
    }
}

@Composable
private fun BreatheDot(color: Color, phaseMs: Int, reducedMotion: Boolean) {
    val progress = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "breathe$phaseMs")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, delayMillis = phaseMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "progress",
        ).value
    }
    // 映射桌面 breathe keyframes：t∈[0,.5] → alpha .45→1 / scale .85→1.15；t∈(.5,1] 反向
    val t = if (progress <= 0.5f) progress / 0.5f else (1f - progress) / 0.5f
    val alpha = 0.45f + (1f - 0.45f) * t
    val scale = 0.85f + (1.15f - 0.85f) * t
    Box(
        modifier = Modifier
            .size(3.dp)
            .alpha(alpha)
            .scale(scale)
            .background(color = color, shape = CircleShape),
    )
}
