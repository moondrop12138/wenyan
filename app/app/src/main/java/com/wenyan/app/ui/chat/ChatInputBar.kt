package com.wenyan.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 底部输入栏（design-tokens component.inputBar，design-pages 页面1）：
 * 回形针（粘贴文本/选择截图）+ TextField + 发送/停止。流式时右侧替换为 stop。
 * v1.3.1 待发送图片：选图后先显示在输入框上方的预览区（缩略图 + 移除），点发送才真正发出；
 * v1.6.1 多图：最多 [ChatViewModel.MAX_PENDING_IMAGES] 张，横向缩略图流 + 右上角删除角标 + 计数；
 * 有图即可发送（可与文字同发），发送键高亮条件随之扩展。
 */
@Composable
fun ChatInputBar(
    input: String,
    streaming: Boolean,
    pendingImages: List<Uri>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPasteText: (String) -> Unit,
    onPendingImagesPicked: (List<Uri>) -> Unit,
    onRemovePendingImage: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    // v1.3.1 全屏输入弹层（输入大量文字时展开编辑）
    var showFullScreen by remember { mutableStateOf(false) }
    // v1.6.1 多图选择器：剩余名额 = 上限 - 已选（已选 0 张时至少允许选 1）
    val remainingSlots = (ChatViewModel.MAX_PENDING_IMAGES - pendingImages.size).coerceAtLeast(1)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = remainingSlots),
        onResult = { uris -> if (uris.isNotEmpty()) onPendingImagesPicked(uris) },
    )
    val canSend = input.isNotBlank() || pendingImages.isNotEmpty()

    // edge-to-edge：bottomBar 不自动处理 insets，手动下移导航栏高度（手势条/三键自适应）
    // v1.5：悬浮胶囊形态——外层无底，内层 r28 圆角 + 投影 + surfaceElevated 底（设计稿 WY-01 输入栏）
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = Color.Transparent,
    ) {
        Column {
            // v1.3.1 待发送图片预览区（v1.6.1 多图横向流）：选完照片点确认后图片停在这里，不直接发出
            if (pendingImages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(pendingImages, key = { it.toString() }) { uri ->
                            val thumb = rememberPendingImageThumbnail(uri)
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.surfaceElevated),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "待发送图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("加载中…", style = GtjType.BodySm, color = p.meta)
                                }
                                // v1.6.1 右上角删除角标：半透明底 + 小叉
                                Surface(
                                    onClick = { onRemovePendingImage(uri) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp),
                                    shape = CircleShape,
                                    color = p.bg.copy(alpha = 0.85f),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "移除图片",
                                            modifier = Modifier.size(12.dp),
                                            tint = p.fg,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Text("图片待发送，可继续添加", style = GtjType.BodySm, color = p.muted)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${pendingImages.size}/${ChatViewModel.MAX_PENDING_IMAGES}",
                            style = GtjType.Caption,
                            color = p.meta,
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = p.border,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = p.surfaceElevated,
                border = BorderStroke(1.dp, p.borderSoft),
                shadowElevation = 8.dp,
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
            Box {
                GtjIconButton(
                    icon = Icons.Outlined.AttachFile,
                    contentDescription = "添加聊天记录",
                    onClick = { menuExpanded = true },
                    tint = p.muted,
                    iconSize = 20.dp,
                )
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("粘贴文本", style = GtjType.BodySm) },
                        onClick = {
                            menuExpanded = false
                            clipboard.getText()?.text?.toString()?.let(onPasteText)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("选择截图", style = GtjType.BodySm) },
                        onClick = {
                            menuExpanded = false
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            // v1.5：内凹输入框——浅沙色 #EFEAE1 内底（surface token），圆角 20 胶囊
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                placeholder = { Text("说点什么，或粘贴聊天记录…", style = GtjType.BodySm, color = p.meta) },
                textStyle = GtjType.BodySm,
                // v1.3.1 固定圆角（20dp）：多行变高时圆角不再随高度膨胀成胶囊
                shape = GtjShape.input,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = p.surface,
                    unfocusedContainerColor = p.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = p.accent,
                ),
                maxLines = 4,
                trailingIcon = {
                    // v1.3.1 全屏输入入口（展开到全屏编辑，适合长文本）
                    GtjIconButton(
                        icon = Icons.Outlined.OpenInFull,
                        contentDescription = "全屏输入",
                        onClick = { showFullScreen = true },
                        tint = p.meta,
                        iconSize = 18.dp,
                    )
                },
            )
            Spacer(Modifier.width(6.dp))
            if (streaming) {
                Surface(
                    onClick = onStop,
                    modifier = Modifier.size(40.dp),
                    shape = GtjShape.pill,
                    color = p.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Stop, contentDescription = "停止生成", modifier = Modifier.size(20.dp), tint = p.fgSecondary)
                    }
                }
            } else {
                // v1.5：陶土棕圆形发送键 40dp（设计稿 WY-01/02 发送按钮）
                Surface(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(40.dp),
                    shape = GtjShape.pill,
                    color = if (canSend) p.accent else p.borderSoft,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) p.accentOn else p.meta,
                        )
                    }
                }
            }
            }
            }
        }
    }

    // v1.3.1 全屏输入弹层：编辑内容与输入框实时同步（同一 input state），点完成/关闭即收起
    if (showFullScreen) {
        FullScreenInputDialog(
            input = input,
            onInputChange = onInputChange,
            onDismiss = { showFullScreen = false },
        )
    }
}

/**
 * v1.3.1 待发送图片缩略图：两次解码（先读尺寸算采样率，再采样解码），
 * 目标边长 ≤ 128dp 像素，后台线程执行防 OOM。
 */
@Composable
private fun rememberPendingImageThumbnail(uri: Uri): Bitmap? {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { 128.dp.toPx() }.toInt()
    return produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                // 第一遍：只读边界
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                // 第二遍：按采样率解码
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()
        }
    }.value
}

private fun computeSampleSize(width: Int, height: Int, targetPx: Int): Int {
    if (width <= 0 || height <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return sample
}

/**
 * v1.3.1 全屏输入弹层：无行数上限的大编辑区，适合粘贴/编写长文本（参考参考图场景）。
 * 内容与底部输入框实时同步（绑定同一 input state，编辑即生效），点完成/关闭即收起。
 */
@Composable
private fun FullScreenInputDialog(
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalGtjColors.current
    val focusRequester = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(p.bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            ) {
                GtjIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = "关闭全屏输入",
                    onClick = onDismiss,
                    tint = p.fgSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text("全屏输入", style = GtjType.Label, color = p.fg)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("完成", style = GtjType.Body, color = p.accent)
                }
            }
            androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = p.border)
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                placeholder = { Text("输入内容…", style = GtjType.Body, color = p.meta) },
                textStyle = GtjType.Body,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = p.accent,
                ),
                maxLines = Int.MAX_VALUE,
            )
        }
    }
    // 打开即聚焦唤起键盘
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
