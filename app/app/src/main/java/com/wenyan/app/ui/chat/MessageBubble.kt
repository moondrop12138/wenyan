package com.wenyan.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.ChatRole
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.rememberUserBubbleBorder
import com.wenyan.app.ui.theme.rememberUserBubbleTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息气泡（design-tokens component.bubbleUser/bubbleAi，design-pages 页面1）：
 * v1.7.0 液态玻璃：用户 = 玻璃 + 深棕 tint 渐变 150° + 棕描边（右下尾圆角 6，行距 1.7，fg 字）；
 * AI = 玻璃 + 白描边（左下尾圆角 6，fg 字）。文字统一 ink 色，合成对比度由 ContrastTest 断言。
 * 长按气泡触发操作菜单（复制/删除），由上层 ChatScreen 处理。
 * v1.2.1：onLongClick 上报触点窗口坐标（气泡窗口位置 + 局部偏移），菜单跟随点按处弹出。
 */
@Composable
fun MessageBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    onLongClick: ((Offset) -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        var windowPos by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .widthIn(max = if (isUser) 300.dp else 340.dp)
                .onGloballyPositioned { windowPos = it.positionInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {},
                        onLongPress = { offset -> onLongClick?.invoke(windowPos + offset) },
                    )
                }
                // v1.2.1 无障碍补偿：原 combinedClickable 提供 click/longClick 语义动作，
                // 换 pointerInput 后补齐（读屏双击长按触发菜单）；内容播报由下方 Text 承担
                .semantics {
                    onClick(label = "查看消息") { true }
                    onLongClick(label = "打开消息操作菜单") {
                        onLongClick?.invoke(Offset.Zero)
                        true
                    }
                }
                // v1.8.0 液态玻璃 2.0：果冻按压 + 边缘透镜（折射/辉光）
                // v1.7.1 二改：clip 置后（此前投影被 clip 裁掉→气泡无层次）
                .liquidGlass(
                    shape = if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi,
                    tint = if (isUser) rememberUserBubbleTint() else null,
                    borderColor = if (isUser) rememberUserBubbleBorder() else null,
                    enablePressAnimation = true,
                )
                .clip(if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi),
        ) {
            Text(
                text = message.content,
                // v1.7.0：用户气泡 14sp/行距 1.7（原型 13px/1.7），AI 保持正文 16/24
                style = if (isUser) UserBubbleTextStyle else GtjType.Body,
                color = p.fg,
                // 无障碍：读屏按"角色+内容"播报——用户消息前置"你说"，AI 消息保持原文
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics {
                        if (isUser) {
                            contentDescription = "你说：" + message.content
                        }
                    },
            )
        }
    }
}

/**
 * 图片消息气泡：content 为 data:image/...;base64,...，解码后按缩略图渲染，长按删除。
 * v1.3.1 去框融合：不再套 Surface 边框/底色，图片直接用气泡圆角裁剪浮在聊天背景上（微信风格）。
 * v1.3.1 点击打开全屏预览（onClick 由上层注入，读屏语义同步触发）。
 */
@Composable
fun ImageMessageBubble(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: ((Offset) -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    val bitmap = rememberDataUrlBitmap(message.content)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        var windowPos by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .widthIn(max = if (isUser) 300.dp else 340.dp)
                .onGloballyPositioned { windowPos = it.positionInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick?.invoke() },
                        onLongPress = { offset -> onLongClick?.invoke(windowPos + offset) },
                    )
                }
                .semantics {
                    onClick(label = "查看图片") {
                        onClick?.invoke()
                        true
                    }
                    onLongClick(label = "打开消息操作菜单") {
                        onLongClick?.invoke(Offset.Zero)
                        true
                    }
                }
                // 无障碍：整卡合并播报"你说：图片"，避免读屏念整段 base64
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isUser) "你说：图片" else "图片"
                },
        ) {
            val bmp = bitmap.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .heightIn(max = 240.dp)
                        // v1.7.0：图片裁剪沿用 20/20/6 气泡圆角
                        .clip(if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi),
                )
            } else {
                Text(
                    text = "图片加载失败",
                    style = GtjType.Body,
                    color = p.muted,
                    modifier = Modifier
                        .clip(if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * v1.6.2 文本选择模式气泡（长按菜单"部分选择"进入）：
 * readOnly BasicTextField 承载消息文本，进入即聚焦并全选——立即出现选区高亮 + 两端可拖手柄 + 系统工具栏，
 * 拖动两端手柄调整范围后由系统工具栏复制（微信"部分选中"同款交互）；点气泡外空白（上层 Box 手势）或滚动退出。
 * 渲染样式与 MessageBubble 一致，只是去掉长按菜单手势（拖选由文本字段接管）。
 * v1.6.1 曾用 SelectionContainer（点菜单后需二次长按文字才出手柄，反馈弱），v1.6.2 弃用；
 * Compose 1.8 无程序化选区 API（TextSelectionSession 已移除），此为唯一"进入即选中"的公开方案。
 * text 参数供 ANALYSIS/FREETEXT 传入拼好的可选文本（默认取消息原文）。
 */
@Composable
fun SelectableMessageContent(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    text: String = message.content,
) {
    val p = LocalGtjColors.current
    val isUser = message.role == ChatRole.USER
    // 受控 TextFieldValue：初始全选；onValueChange 必须回写，否则拖动手柄无响应
    var tfv by remember(message.id) { mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length))) }
    val focusRequester = remember { FocusRequester() }
    // 组合完成后自动聚焦 → 立即显示选区手柄与系统工具栏（readOnly 聚焦不弹 IME）
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // 选区品牌化：气泡底是陶土棕/米白，系统默认蓝色手柄不可见——手柄/高亮改用主题色
        val selectionColors = if (isUser) {
            TextSelectionColors(
                handleColor = p.accentOn,
                backgroundColor = p.accentOn.copy(alpha = 0.25f),
            )
        } else {
            TextSelectionColors(
                handleColor = p.accent,
                backgroundColor = p.accent.copy(alpha = 0.25f),
            )
        }
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            Box(
                modifier = Modifier
                    .widthIn(max = if (isUser) 300.dp else 340.dp)
                    // v1.8.0 液态玻璃 2.0：果冻按压 + 边缘透镜（折射/辉光）
                    // v1.7.1 二改：clip 置后，软投影不被裁
                    .liquidGlass(
                        shape = if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi,
                        tint = if (isUser) rememberUserBubbleTint() else null,
                        borderColor = if (isUser) rememberUserBubbleBorder() else null,
                        enablePressAnimation = true,
                    )
                    .clip(if (isUser) GtjShape.bubbleUser else GtjShape.bubbleAi),
            ) {
                BasicTextField(
                    value = tfv,
                    onValueChange = { tfv = it },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusRequester(focusRequester),
                    readOnly = true,
                    // Surface contentColor 不自动作用于 BasicTextField，需显式上色（v1.7.0 统一 ink 色）
                    textStyle = (if (isUser) UserBubbleTextStyle else GtjType.Body).copy(color = p.fg),
                    cursorBrush = SolidColor(Color.Transparent),
                )
            }
        }
    }
}

/** v1.8.2 流式 AI 文章（editorial 回信风格：刊头 + 打字机段落，替代玻璃气泡） */
@Composable
fun StreamingBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    // v1.8.2-fix（审查 P3-9）：流式每 token 重组会重算"当前时间"，跨分钟时刊头时间跳动；
    // remember 固定为首次组合时刻（流式预览本就是"现在"，无需跟随重组刷新）
    val headerTime = remember { formatNowTime() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .background(p.accent),
            )
            Spacer(Modifier.width(10.dp))
            Text("温言 · 回信", style = GtjType.Label.copy(fontSize = 12.sp), color = p.accent)
            Spacer(Modifier.weight(1f))
            Text(headerTime, style = GtjType.Caption, color = p.meta)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = GtjType.Body.copy(lineHeight = 26.sp),
            color = p.fgSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = p.accent,
                        topLeft = Offset(0f, 0f),
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
                .padding(start = 16.dp),
        )
    }
}

/**
 * v1.8.2 流式期间占位（reply 还没产出，模型还在写 JSON 的 steps 部分）：
 * editorial 化：规则线 + 温言·回信 + 组织语言省略号
 */
@Composable
fun StreamingPlaceholderBubble(
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .background(p.accent),
        )
        Spacer(Modifier.width(10.dp))
        Text("温言 · 回信", style = GtjType.Label.copy(fontSize = 12.sp), color = p.accent)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "正在组织语言…",
            style = GtjType.BodySm,
            color = p.muted,
        )
    }
}

/** v1.7.0 用户气泡正文：14sp / 行距 1.7（原型 buser 13px/1.7，略放大保可读性） */
internal val UserBubbleTextStyle: androidx.compose.ui.text.TextStyle =
    GtjType.Body.copy(fontSize = 14.sp, lineHeight = 24.sp)

/** 流式刊头时间戳（HH:mm） */
private fun formatNowTime(): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

/** data URL 解码缓存（按 byteCount 计量，32MB 上限），避免 LazyColumn 滚动重组时重复解码 */
private object DataUrlBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)

    fun keyOf(dataUrl: String): String = "${dataUrl.length}:${dataUrl.hashCode()}"
}

/**
 * 将 data:image/...;base64,... 解码为 Bitmap；后台线程解码 + LruCache 缓存。
 * v1.3.1 internal：供全屏预览组件（ImagePreviewOverlay）复用同一缓存管线。
 */
@Composable
internal fun rememberDataUrlBitmap(dataUrl: String) =
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
