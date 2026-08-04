package com.wenyan.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenyan.app.ui.contract.ChatMessageUi
import kotlin.math.max

/**
 * v1.3.1 全屏图片预览覆盖层：
 * - 黑底铺满（decorFitsSystemWindows=false 沉浸，系统栏区域同为黑底）
 * - 双指捏合缩放（1x–4x）+ 拖动查看，按 Fit 实际绘制尺寸双向 clamp 防越界
 * - 单击任意处或右上角关闭按钮退出（拖动/缩放会消费移动事件，不会误触单击关闭）
 * - 复用 MessageBubble 的 data URL 解码缓存管线（rememberDataUrlBitmap）
 */
@Composable
fun ImagePreviewOverlay(
    message: ChatMessageUi,
    onDismiss: () -> Unit,
) {
    val bitmap = rememberDataUrlBitmap(message.content)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 单击关闭：图片层的 transform 手势消费移动后，此 tap 自动取消，拖动不误关
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            var viewport by remember { mutableStateOf(IntSize.Zero) }
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val bmp = bitmap.value
            if (bmp != null) {
                val drawn = fitSize(bmp, viewport)
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewport = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                val k = newScale / scale
                                // 保持手势中心锚定：offset 为图片中心相对视口中心的偏移
                                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                                var newOffset = offset * k + (centroid - center) * (1 - k) + pan
                                if (newScale <= 1f) {
                                    newOffset = Offset.Zero
                                } else {
                                    val maxX = max(0f, (drawn.width * newScale - viewport.width) / 2f)
                                    val maxY = max(0f, (drawn.height * newScale - viewport.height) / 2f)
                                    newOffset = Offset(
                                        newOffset.x.coerceIn(-maxX, maxX),
                                        newOffset.y.coerceIn(-maxY, maxY),
                                    )
                                }
                                offset = newOffset
                                scale = newScale
                            }
                        }
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                            scaleX = scale
                            scaleY = scale
                        },
                )
                // 视口变化（如旋转）后重新收敛偏移，避免越界残留
                LaunchedEffect(viewport) {
                    if (scale <= 1f) {
                        offset = Offset.Zero
                    } else {
                        val maxX = max(0f, (drawn.width * scale - viewport.width) / 2f)
                        val maxY = max(0f, (drawn.height * scale - viewport.height) / 2f)
                        offset = Offset(
                            offset.x.coerceIn(-maxX, maxX),
                            offset.y.coerceIn(-maxY, maxY),
                        )
                    }
                }
            } else {
                Text(
                    text = "图片加载失败",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = Color(0x66000000),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭预览",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** ContentScale.Fit 下图片的实际绘制尺寸（视口未就绪时按原图尺寸兜底） */
private fun fitSize(bmp: android.graphics.Bitmap, viewport: IntSize): IntSize {
    if (viewport.width <= 0 || viewport.height <= 0) {
        return IntSize(bmp.width, bmp.height)
    }
    val scale = minOf(
        viewport.width.toFloat() / bmp.width,
        viewport.height.toFloat() / bmp.height,
    )
    return IntSize(
        (bmp.width * scale).toInt().coerceAtLeast(1),
        (bmp.height * scale).toInt().coerceAtLeast(1),
    )
}
