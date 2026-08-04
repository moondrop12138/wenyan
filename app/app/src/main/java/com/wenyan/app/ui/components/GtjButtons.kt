package com.wenyan.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 按钮体系（design-tokens.json component.*Button）。
 * 统一：可点区域 >=48dp、按压 scale 0.98 + ripple、loading 内联 spinner（不整屏阻塞）。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    minHeight: Dp = 48.dp,
) {
    val p = LocalGtjColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer { scaleX = if (pressed) 0.98f else 1f; scaleY = if (pressed) 0.98f else 1f },
        shape = GtjShape.md,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.accentOn,
            disabledContainerColor = p.accent.copy(alpha = 0.4f),
            disabledContentColor = p.accentOn.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = p.accentOn, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = GtjType.Subtitle)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    minHeight: Dp = 48.dp,
) {
    val p = LocalGtjColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer { scaleX = if (pressed) 0.98f else 1f; scaleY = if (pressed) 0.98f else 1f },
        shape = GtjShape.md,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = p.accent,
            disabledContentColor = p.muted,
        ),
        border = BorderStroke(1.dp, if (enabled && !loading) p.border else p.borderSoft),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = p.accent, strokeWidth = 2.dp)
        } else {
            Text(text, style = GtjType.Subtitle)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
) {
    val p = LocalGtjColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer { scaleX = if (pressed) 0.98f else 1f; scaleY = if (pressed) 0.98f else 1f },
        shape = GtjShape.md,
        colors = ButtonDefaults.textButtonColors(contentColor = p.muted),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text, style = GtjType.Subtitle)
    }
}

/** 图标按钮：contentDescription 必填（无障碍基线），触区 48dp。 */
@Composable
fun GtjIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalGtjColors.current.fgSecondary,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = if (pressed) 0.96f else 1f; scaleY = if (pressed) 0.96f else 1f },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize).clip(CircleShape),
                tint = tint,
            )
        }
    }
}
