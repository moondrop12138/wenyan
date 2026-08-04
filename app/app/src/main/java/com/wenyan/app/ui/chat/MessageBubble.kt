package com.wenyan.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRole
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息气泡（design-tokens component.bubbleUser/bubbleAi，design-pages 页面1）：
 * 用户右对齐 accent 底 + accentOn 字（右下小圆角，maxWidth 82%）；
 * AI 左对齐 surface 底 + fg 字（左下小圆角，maxWidth 92%，borderSoft 边）。
 * 长按气泡触发操作菜单（复制/删除），由上层 ChatScreen 处理。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isUser) 300.dp else 340.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                ),
            shape = bubbleShape(isUser),
            color = if (isUser) p.accent else p.surfaceElevated,
            contentColor = if (isUser) p.accentOn else p.fg,
            border = if (isUser) null else BorderStroke(1.dp, p.borderSoft),
        ) {
            Text(
                text = message.content,
                style = GtjType.Body,
                // 无障碍：读屏按"角色+内容"播报——用户消息前置"你说"，AI 消息保持原文
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .semantics {
                        if (isUser) {
                            contentDescription = "你说：" + message.content
                        }
                    },
            )
        }
    }
}

/** 图片消息气泡：content 为 data:image/...;base64,...，解码后按缩略图渲染，长按删除 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMessageBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    val bitmap = rememberDataUrlBitmap(message.content)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = if (isUser) 300.dp else 340.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick,
                )
                // 无障碍：整卡合并播报"你说：图片"，避免读屏念整段 base64
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isUser) "你说：图片" else "图片"
                },
            shape = bubbleShape(isUser),
            color = if (isUser) p.accent else p.surfaceElevated,
            contentColor = if (isUser) p.accentOn else p.fg,
            border = if (isUser) null else BorderStroke(1.dp, p.borderSoft),
        ) {
            val bmp = bitmap.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .widthIn(max = 240.dp)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(CornerSize(GtjShape.lgRadius))),
                )
            } else {
                Text(
                    text = "图片加载失败",
                    style = GtjType.Body,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 流式中的 AI 气泡（打字机增量文本，design-pages 页面1） */
@Composable
fun StreamingBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = CornerSize(GtjShape.lgRadius),
                topEnd = CornerSize(GtjShape.lgRadius),
                bottomStart = CornerSize(GtjShape.bubbleTailSmRadius),
                bottomEnd = CornerSize(GtjShape.lgRadius),
            ),
            color = p.surfaceElevated,
            contentColor = p.fg,
            border = BorderStroke(1.dp, p.borderSoft),
        ) {
            Text(
                text = text,
                style = GtjType.Body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 流式期间占位气泡（reply 还没产出，模型还在写 JSON 的 steps 部分）：
 * 避免把原始 JSON 当正文展示——这是"思考代码"问题的另一半。
 */
@Composable
fun StreamingPlaceholderBubble(
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = RoundedCornerShape(
                topStart = CornerSize(GtjShape.lgRadius),
                topEnd = CornerSize(GtjShape.lgRadius),
                bottomStart = CornerSize(GtjShape.bubbleTailSmRadius),
                bottomEnd = CornerSize(GtjShape.lgRadius),
            ),
            color = p.surfaceElevated,
            contentColor = p.muted,
            border = BorderStroke(1.dp, p.borderSoft),
        ) {
            Text(
                text = "正在组织语言…",
                style = GtjType.Body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

private fun bubbleShape(isUser: Boolean): RoundedCornerShape =
    if (isUser) {
        RoundedCornerShape(
            topStart = CornerSize(GtjShape.lgRadius),
            topEnd = CornerSize(GtjShape.lgRadius),
            bottomStart = CornerSize(GtjShape.lgRadius),
            bottomEnd = CornerSize(GtjShape.bubbleTailSmRadius),
        )
    } else {
        RoundedCornerShape(
            topStart = CornerSize(GtjShape.lgRadius),
            topEnd = CornerSize(GtjShape.lgRadius),
            bottomStart = CornerSize(GtjShape.bubbleTailSmRadius),
            bottomEnd = CornerSize(GtjShape.lgRadius),
        )
    }

/** data URL 解码缓存（按 byteCount 计量，32MB 上限），避免 LazyColumn 滚动重组时重复解码 */
private object DataUrlBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)

    fun keyOf(dataUrl: String): String = "${dataUrl.length}:${dataUrl.hashCode()}"
}

/** 将 data:image/...;base64,... 解码为 Bitmap；后台线程解码 + LruCache 缓存 */
@Composable
private fun rememberDataUrlBitmap(dataUrl: String) =
    produceState<Bitmap?>(initialValue = DataUrlBitmapCache.get(DataUrlBitmapCache.keyOf(dataUrl)), dataUrl) {
        val key = DataUrlBitmapCache.keyOf(dataUrl)
        DataUrlBitmapCache.get(key)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val base64 = dataUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()?.also { DataUrlBitmapCache.put(key, it) }
        }
    }
