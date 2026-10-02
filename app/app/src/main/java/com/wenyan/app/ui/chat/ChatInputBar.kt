package com.wenyan.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.glass.GlassBackdropLayer
import com.wenyan.app.ui.components.glass.GlassFill
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.rememberSendGradient
import com.wenyan.app.ui.theme.rememberSendHighlight
import com.wenyan.app.ui.theme.rememberSendIconColor
import com.wenyan.app.ui.theme.rememberSendShadow
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
    // v1.8.2-fix（审查 P3-10）：空状态索引点击填入后聚焦输入框（对齐桌面端行为）
    inputFocusRequester: FocusRequester? = null,
    // v1.9.4 根因④：真实背景模糊层（Mica 磨砂，API 31+；null=回退静态玻璃，向后兼容）
    backdropLayer: GlassBackdropLayer? = null,
) {
    val p = LocalGtjColors.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    // v1.3.1 全屏输入弹层（输入大量文字时展开编辑）
    var showFullScreen by remember { mutableStateOf(false) }
    // v1.6.1 多图选择器。
    // L34 修复：原 contract 的 maxItems 用动态 remainingSlots——rememberLauncherForActivityResult
    // 以 contract 为 key，remainingSlots 变化即销毁重建 launcher（已注册回调丢失风险）；
    // 且系统选择器按「当时上限」放行后回调无二次截断，仍可超选。改固定上限注册，
    // 回调内按剩余名额截断。
    val remainingSlots = (ChatViewModel.MAX_PENDING_IMAGES - pendingImages.size).coerceAtLeast(0)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(ChatViewModel.MAX_PENDING_IMAGES),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPendingImagesPicked(uris.take(remainingSlots))
            }
        },
    )
    val canSend = input.isNotBlank() || pendingImages.isNotEmpty()

    // v1.9.4 Mica 聚焦环：web .input-bar:focus-within 追加 0 0 0 2px var(--l2b)（亮 styles.css:542 /
    // 暗 styles.css:562），过渡 box-shadow .25s（styles.css:218）→ 聚焦态以 0.25s 淡入外圈 2dp 环（token glassFocusRing）
    var inputFocused by remember { mutableStateOf(false) }
    val focusRingAlpha by animateFloatAsState(
        targetValue = if (inputFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "inputFocusRing",
    )

    // edge-to-edge：bottomBar 不自动处理 insets，手动下移导航栏高度（手势条/三键自适应）。
    // v1.9.4 评审修复（IME 联动）：bottom inset 取 max(导航栏, IME)（safeDrawing 联合，
    // IME 弹出时其 bottom 覆盖导航条区域不双算）——键盘弹出时输入栏整体上移，Scaffold
    // body padding 随 bottomBar 测量高度同步增大，列表 contentPadding
    // （ChatScreen padding.calculateBottomPadding()）联动保证可滚动到最新消息；
    // 自动可见由 ChatScreen 滚动跟随 LaunchedEffect 保证（该 key 计入 calculateBottomPadding，
    // 位于底部时键盘弹出/多行长高即重滚到底）——本注释 v1.9.4 评审时曾与实现不符，已闭环。
    // v1.9.4 IME 定位修复配套：MainActivity 已在 Manifest 显式声明 windowSoftInputMode=
    // adjustResize——未声明时默认 adjustUnspecified，系统对聚焦输入框走 adjustPan 把整个
    // 窗口内容上移出键盘高度，再叠加本 inset 就是双重位移（顶栏被 pan 出屏幕、输入栏悬空
    // 在键盘上方一大截，模拟器 API 35 已复现）；声明后窗口不动，IME 只经本 inset 单点消费。
    // v1.5：悬浮胶囊形态——外层无底，内层玻璃圆角（v1.9.4 对齐 Mica 为 r26）+ 投影（设计稿 WY-01 输入栏）
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        color = Color.Transparent,
    ) {
        Column(
            // v1.9.4 Mica：外层 = web .input-zone `padding:10px 16px 16px`（styles.css:215）——
            // 栏距屏幕左右 16dp、距底 16dp（替换旧 12/8），顶部 10dp 让待发送缩略图区同处一个内衬带
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        ) {
            // v1.3.1 待发送图片预览区（v1.6.1 多图横向流）：选完照片点确认后图片停在这里，不直接发出
            if (pendingImages.isNotEmpty()) {
                Column(
                    // web .pending-thumbs：区内再内缩 4px、距栏 8px（`padding:0 4px;margin:0 auto 8px`）
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
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
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }
            // v1.8.0 液态玻璃 2.0：果冻按压 + 边缘透镜（光斑透出）
            // v1.9.4 + F41 修复：全量磨砂（背景+消息穿透）改由 liquidGlass 经 backdropLayer
            // 参数在内部消费——投影之后、填充之前绘制。原先经独立的 glassBackdropLayer modifier
            // 垫在链最底层，不透明磨砂被其上的 α.40 栏级投影整体压暗（与卡片路径层级矛盾）；
            // backdrop=false：本面已显式全量磨砂，卡片级磨砂不叠加（v1.9.4 配套）；
            // v1.9.4：fill = Card——输入栏属 web --wy-card-* 渐变组（styles.css:538，与顶栏共用，
            // 亮 .150/.105、暗 .150/.150），不走 Frost 默认组
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // v1.9.4 Mica 聚焦环：整环画在玻璃面之下（链上靠前先画），外扩 2dp 描边
                    // 全部落在元素边缘之外的留白区（对齐 web 0 0 0 2px 外圈环，不被内层裁剪）。
                    // 绘制走 outline→Path→drawPath 描边（同 LiquidGlass 的既有模式）——本版本
                    // ui-graphics 1.8.3 的 DrawScope 无 drawOutline（成员/扩展均无，已在依赖类里核实）
                    .drawBehind {
                        if (focusRingAlpha > 0f) {
                            val ringWidth = 2.dp.toPx()
                            val ringOutline = GtjShape.inputBar.createOutline(
                                Size(size.width + ringWidth, size.height + ringWidth),
                                layoutDirection,
                                this,
                            )
                            val ringPath = when (ringOutline) {
                                is Outline.Rectangle -> Path().apply { addRect(ringOutline.rect) }
                                is Outline.Rounded -> Path().apply { addRoundRect(ringOutline.roundRect) }
                                is Outline.Generic -> ringOutline.path
                            }
                            // 描边中心在外扩路径上 → 覆盖原边缘外 0..2dp 区间，语义同 box-shadow spread
                            translate(-ringWidth / 2f, -ringWidth / 2f) {
                                drawPath(
                                    path = ringPath,
                                    color = p.glassFocusRing.copy(alpha = p.glassFocusRing.alpha * focusRingAlpha),
                                    style = Stroke(width = ringWidth),
                                )
                            }
                        }
                    }
                    // v1.7.1 二改：clip 在 liquidGlass 之后，软投影不被裁（此前投影丢失→纯色平台感）
                    // v1.9.4 Mica：栏阴影对齐 web（亮 0 8px 32px rgba(110,70,30,.12) /
                    // 暗 0 8px 32px rgba(0,0,0,.4)，styles.css:539/559）；CSS blur 半径 ≈ 2σ → 羽化 16dp。
                    // 近影传 Transparent：web 对 Mica 栏的 box-shadow 是**整条覆盖**（styles.css:539
                    // 只剩这一条栏级阴影），不再叠卡片近影 `0 2px 6px`
                    .liquidGlass(
                        shape = GtjShape.inputBar,
                        fill = GlassFill.Card,
                        enablePressAnimation = true,
                        // backdrop=true（v1.9.4 玻璃可调由 false 改回，同 ChatTopBar）：31+ 上
                        // backdropLayer 优先、卡片级磨砂结构性不叠加；underlay 由内容层宿主绘制
                        // （design J，见 ChatTopBar 注释）；API<31/探测失败时 backdropLayer
                        // 恒 null，true 让雾化降级覆盖本栏（legacyFog，见 liquidGlass）
                        backdrop = true,
                        backdropLayer = backdropLayer,
                        shadowColor = p.glassShadowInputBar,
                        shadowFeather = 16.dp,
                        shadowLift = 8.dp,
                        shadowNearColor = Color.Transparent,
                    )
                    .clip(GtjShape.inputBar),
            ) {
            Row(
                // v1.9.4 Mica：栏内 padding 10/12 = web .input-bar `padding:10px 12px`；
                // 元素间距 10dp = web `gap:10px`；底部对齐 = web `align-items:flex-end`
                // （多行输入时输入框向上长、按钮贴底）
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
            Box {
                // 附件键（web .input-attach，styles.css:221）：38dp 圆 + chip 半透明 accent 底 + accent 图标
                // （web 的 chip 底只在 :hover 出现，触屏无 hover → 取常驻态；下拉菜单功能保留）
                BarCircleButton(
                    size = 38.dp,
                    background = p.glassChip,
                    icon = Icons.Outlined.AttachFile,
                    contentDescription = "添加聊天记录",
                    tint = p.accent,
                    iconSize = 17.dp, // web svg width/height=17（index.html:100）
                    onClick = { menuExpanded = true },
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
            // v1.9.4 Mica 内衬输入框（web .input-box，styles.css:223-229）：
            // 半透明白底（--inp：亮 .55 / 暗 .07）+ 顶部内凹阴影（inset 0 2px 8px --inset）
            // + 底部 1px 白高光（inset 0 -1px 0 rgba(255,255,255,.5)/暗 .08）；r20 不变。
            // 原 M3 TextField 换 BasicTextField：M3 TextField 内含 56dp 最小高度（TextFieldDefaults.MinHeight）
            // 且无 contentPadding 入口，达不到 web 的 38px 下界/10px 15px 内边距。
            Box(
                modifier = Modifier
                    .weight(1f)
                    // min-height 38 / max-height 160 = web（实际单行高由 10dp 上下内边距 + 20sp 行高 ≈ 40dp 决定）
                    .heightIn(min = 38.dp, max = 160.dp)
                    .clip(GtjShape.input)
                    .drawWithCache {
                        val inputFill = p.glassInputFill
                        val insetShade = p.glassInsetShade
                        val insetHighlight = p.glassInsetHighlight
                        val corner = CornerRadius(GtjShape.inputRadius.toPx())
                        // 顶部内凹阴影：上沿起 8dp 内渐隐（web inset 0 2px 8px）
                        val insetBrush = Brush.verticalGradient(
                            colorStops = arrayOf(0f to insetShade, 1f to Color.Transparent),
                            startY = 0f,
                            endY = 8.dp.toPx(),
                        )
                        val highlightHeight = 1.dp.toPx()
                        onDrawBehind {
                            drawRoundRect(color = inputFill, cornerRadius = corner)
                            drawRect(brush = insetBrush)
                            drawRect(
                                color = insetHighlight,
                                topLeft = Offset(0f, size.height - highlightHeight),
                                size = Size(size.width, highlightHeight),
                            )
                        }
                    },
            ) {
                // 全屏输入入口（Android 功能件保留，web 无）：叠在输入框右端，36dp 触点（不把输入框撑高）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { showFullScreen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.OpenInFull,
                        contentDescription = "全屏输入",
                        modifier = Modifier.size(18.dp),
                        tint = p.meta,
                    )
                }
                if (input.isEmpty()) {
                    Text(
                        "说点什么，或粘贴聊天记录…",
                        style = GtjType.BodySm,
                        color = p.meta, // web .input-box::placeholder{color:var(--meta)}
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 15.dp, end = 46.dp),
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        // web .input-box `padding:10px 15px`；右侧 46dp 让出全屏入口触点
                        .padding(start = 15.dp, end = 46.dp, top = 10.dp, bottom = 10.dp)
                        .onFocusChanged { inputFocused = it.isFocused }
                        .then(if (inputFocusRequester != null) Modifier.focusRequester(inputFocusRequester) else Modifier),
                    // 字号：BodySm 14sp/20sp ↔ web 13.5px/行高 1.6（≈21.6px）同量级，见 summary
                    textStyle = GtjType.BodySm.copy(color = p.fg),
                    cursorBrush = SolidColor(p.accent),
                    // web max-height:160px：7 行 × 20sp + 上下 10dp 内边距 = 160dp
                    maxLines = 7,
                )
            }
            if (streaming) {
                // v1.9.4：流式停止键（Android 功能件，web 无）——视觉并入新配方：与附件键同款
                // chip 半透明 accent 底 + 40dp 圆（占发送键位）+ 按压 scale .92
                BarCircleButton(
                    size = 40.dp,
                    background = p.glassChip,
                    icon = Icons.Outlined.Stop,
                    contentDescription = "停止生成",
                    tint = p.accent,
                    iconSize = 20.dp,
                    onClick = onStop,
                )
            } else {
                SendButton(canSend = canSend, onSend = onSend)
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
 * v1.9.4 Mica 栏内圆形按钮（附件键 / 流式停止键共用）：chip 半透明 accent 底 + accent 图标 +
 * 按压 scale .92（与发送键同款触点手感——web 各可点元素统一走 `:active` scale 语言，
 * 附件键在 web 是 hover 上色，触屏取常驻 chip 底）。
 */
@Composable
private fun BarCircleButton(
    size: Dp,
    background: Color,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    iconSize: Dp = 20.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120), // web transition .12s
        label = "barCircleButtonPressScale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize), tint = tint)
    }
}

/**
 * v1.9.4 Mica 发送键（web .send-btn，styles.css:231-238）：40dp 圆 + 150° 渐变
 * （--send1 → --send2）+ 顶部内高光 `inset 0 1px 0 rgba(255,255,255,.35)` + 投影
 * `0 6px 14px rgba(164,85,28,.35)`（CSS blur 14px ≈ 2σ → 羽化 7dp、位移 6dp）。
 * 按压 scale .92（web `:active`）；禁用态 opacity .45（web `:disabled`）——渐变保留、
 * 整体降透明度（旧实现禁用时换灰底，与 web 不一致，已按 web 收敛）。
 * 投影在 clip 之前绘制：软影溢出面外，随 alpha 一起淡出。
 */
@Composable
private fun SendButton(canSend: Boolean, onSend: () -> Unit) {
    val sendGradient = rememberSendGradient()
    val sendIconColor = rememberSendIconColor()
    val sendHighlight = rememberSendHighlight()
    val sendShadow = rememberSendShadow()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && canSend) 0.92f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "sendButtonPressScale",
    )
    // 150° 渐变：40dp 方块对角（web `linear-gradient(150deg,…)`）
    val sendEnd = with(LocalDensity.current) { Offset(40.dp.toPx(), 40.dp.toPx()) }
    val shadowSigma = with(LocalDensity.current) { 7.dp.toPx() }
    val shadowDy = with(LocalDensity.current) { 6.dp.toPx() }
    val sendBrush = remember(sendGradient, sendEnd) {
        Brush.linearGradient(
            colorStops = sendGradient.map { it.first to it.second }.toTypedArray(),
            start = Offset.Zero,
            end = sendEnd,
        )
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (canSend) 1f else 0.45f
            }
            .drawWithCache {
                val shadowPaint = Paint().apply {
                    color = sendShadow
                    asFrameworkPaint().maskFilter =
                        android.graphics.BlurMaskFilter(shadowSigma, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                onDrawBehind {
                    translate(top = shadowDy) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawCircle(
                                size.width / 2f,
                                size.height / 2f,
                                size.minDimension / 2f,
                                shadowPaint.asFrameworkPaint(),
                            )
                        }
                    }
                }
            }
            .clip(CircleShape)
            .drawWithCache {
                // 顶部内高光（web inset 0 1px 0；圆形近似：上缘白线渐隐到 35% 高度）
                val highlightBrush = Brush.verticalGradient(
                    colorStops = arrayOf(0f to sendHighlight, 1f to Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.35f,
                )
                onDrawBehind {
                    drawCircle(brush = sendBrush)
                    drawCircle(brush = highlightBrush)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current, // 涟漪保留（随 clip 裁进圆内）
                enabled = canSend,
                onClick = onSend,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Send,
            contentDescription = "发送",
            modifier = Modifier.size(18.dp),
            tint = sendIconColor,
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
