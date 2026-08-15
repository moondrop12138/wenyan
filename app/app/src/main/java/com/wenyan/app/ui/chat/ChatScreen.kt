package com.wenyan.app.ui.chat

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyan.app.container.UiMappers
import com.wenyan.app.ui.components.CoachCard
import com.wenyan.app.ui.components.CrisisCard
import com.wenyan.app.ui.components.ErrorCard
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.ModelSheet
import com.wenyan.app.ui.components.TranscriptionCard
import com.wenyan.app.ui.components.TypingIndicator
import com.wenyan.app.ui.components.resolveWaitingLabel
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.MessageType
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 对话首页（/chat，SPEC §7 页面1）：消息流 + 空状态 + 输入栏 + 模型切换弹层。
 * 纯组装无业务逻辑；状态全部来自 ChatViewModel。
 */
@Composable
fun ChatScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
) {
    val vm: ChatViewModel = rememberViewModel("ChatViewModel") {
        ChatViewModel(container.chatRepository)
    }
    val messages by vm.messages.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val input by vm.input.collectAsState()
    val pendingImages by vm.pendingImages.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val transcription by vm.transcription.collectAsState()
    val transcribing by vm.transcribing.collectAsState()
    val confirming by vm.confirming.collectAsState()
    val modelName by vm.currentModelName.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // v1.9.0 自动记忆写入回执 → 一次性 toast（消费后清空，避免重复弹）
    val memoryReceipt by vm.memoryReceipt.collectAsState()
    LaunchedEffect(memoryReceipt) {
        memoryReceipt?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMemoryReceipt()
        }
    }
    // H3: 解析失败兜底提示 → 一次性 toast
    val notice by vm.notice.collectAsState()
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }
    // v1.8.2-fix（审查 P3-10）：输入框焦点，空状态索引点击填入后聚焦（对齐桌面端）
    val inputFocusRequester = remember { FocusRequester() }
    var showModelSheet by remember { mutableStateOf(false) }
    // 长按消息菜单 / 删除确认（局部 UI 态，与 showModelSheet 同模式）
    var menuFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    // v1.3.1 全屏图片预览目标（点击图片气泡打开，点按/关闭键退出）
    var previewFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    // v1.2.1 长按触点窗口坐标（Offset.Zero = 无障碍触发无坐标，用默认落点）→ DropdownMenu offset
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    var confirmDeleteFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var confirmDeleteSession by remember { mutableStateOf<SessionSummaryUi?>(null) }
    // v1.6.1 文本选择模式：长按菜单"选择文字"进入——气泡文字变为可选中（SelectionContainer），
    // 用户长按文字拖选部分复制；点空白处（Box tap）或滚动列表退出
    var textSelectForId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val models by container.settingsRepository.models.collectAsState(initial = emptyList())
    val currentId by container.settingsRepository.currentModelId.collectAsState(initial = null)
    val p = LocalGtjColors.current

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    /** 长按任意消息 → 打开菜单并记录触点位置（菜单弹出在手指处） */
    fun openMessageMenu(msg: ChatMessageUi, offset: Offset) {
        menuFor = msg
        menuOffset = offset
    }

    // v1.6.1 滚动列表时退出选择模式（用户已转移注意力）
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) textSelectForId = null }
    }

    // ── v1.7.0 顶栏状态点四态（原型 sdot：已连接绿 / 连接中杏棕呼吸 / 思考中赭石呼吸 / 失败灰）──
    // 最小侵入状态机（零改 ViewModel）：切模型 → Connecting 900ms → Idle；streaming 覆盖为 Thinking；
    // 有 lastError 且未在流式 → Failure（错误卡消失自动回 Idle）。
    var pendingSwitch by remember { mutableStateOf(false) }
    LaunchedEffect(pendingSwitch) {
        if (pendingSwitch) {
            delay(900)
            pendingSwitch = false
        }
    }
    val dotState = when {
        streaming -> DotState.Thinking
        pendingSwitch -> DotState.Connecting
        lastError != null -> DotState.Failure
        else -> DotState.Idle
    }

    // v1.7.1-5：侧栏液态玻璃 + 高斯模糊（整个背景含顶栏/输入栏）——
    // blur 半径由弱渐强（0→18f，250ms），关闭时渐弱；API 31+ 生效，低版本回退实底
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isDrawerOpen = drawerState.targetValue == DrawerValue.Open
    val blurRadius by animateFloatAsState(
        targetValue = if (canBlur && isDrawerOpen) 18f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "drawerBlur",
    )
    val contentBlur = remember(blurRadius) {
        if (blurRadius > 0.5f) {
            // ui.graphics 顶层工厂：BlurEffect(radiusX, radiusY, edgeTreatment)
            BlurEffect(radiusX = blurRadius, radiusY = blurRadius, edgeTreatment = TileMode.Decal)
        } else {
            null
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                // v1.7.1-4：API31+ 半透明玻璃（背后被模糊，可读性由模糊保证）；低版本实底
                drawerContainerColor = if (canBlur) p.glassFill else p.bg,
            ) {
                SessionDrawerContent(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onNewSession = {
                        vm.startNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { id ->
                        vm.switchSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onLongPressSession = { confirmDeleteSession = it },
                )
            }
        },
    ) {
    // v1.8.1 B4：移除 glowState 光斑共享——dead path（玻璃接收后从未使用）且每帧重组开销大

    // v1.7.1：根 Box 加主题背景（防 App 内主题与系统主题脱节时透明 Scaffold 露出暗色 windowBackground）；光斑画在背景之上
    // v1.7.1-5：blur 提升到整个背景层（含顶栏/输入栏/消息区），抽屉打开时全部渐强模糊
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .graphicsLayer {
                renderEffect = contentBlur
            },
    ) {
        GlowBackground()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ChatTopBar(
                modelName = modelName,
                dotState = dotState,
                onModelClick = { showModelSheet = true },
                onSettings = onOpenSettings,
                onMenu = { scope.launch { drawerState.open() } },
            )
        },
        bottomBar = {
            ChatInputBar(
                input = input,
                streaming = streaming,
                pendingImages = pendingImages,
                onInputChange = vm::onInputChange,
                // v1.3.1 统一发送入口：有图 → 图文同发/纯图，无图 → 纯文本
                onSend = vm::sendPending,
                onStop = vm::stop,
                onPasteText = { vm.onInputChange(it) },
                onPendingImagesPicked = vm::addPendingImages,
                onRemovePendingImage = vm::removePendingImage,
                inputFocusRequester = inputFocusRequester,
            )
        },
    ) { padding ->
        // v1.6.1 选择模式激活时，点列表任意空白处退出（tap 与长按拖选手势不冲突）
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(textSelectForId) {
                    if (textSelectForId != null) {
                        detectTapGestures(onTap = { textSelectForId = null })
                    }
                },
        ) {
            if (messages.isEmpty() && !streaming && transcription == null && lastError == null) {
                // v1.8.2 editorial 空状态：粘贴/截图入口由输入栏回形针承接
                // v1.8.2-fix（审查 P3-10）：点击索引填入输入栏并聚焦（对齐桌面端）
                ChatEmptyState(
                    onExampleClick = { text ->
                        vm.onInputChange(text)
                        inputFocusRequester.requestFocus()
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        when (msg.type) {
                            MessageType.ANALYSIS -> {
                                // v1.6 统一 CoachCard：新四段 schema 与老五步法 JSON 均经 parseCoachCard 兼容映射
                                val card = UiMappers.parseCoachCard(msg.content)
                                when {
                                    // v1.6.2 部分选择：分析卡整体切为可选中文本（四段拼接，解析失败兜底原文）
                                    textSelectForId == msg.id -> SelectableMessageContent(
                                        msg,
                                        text = card?.let { UiMappers.coachCardToSelectableText(it) } ?: msg.content,
                                    )
                                    card?.safetyOverride == true -> CrisisCard(
                                        onAcknowledge = {},
                                        safetyMessage = card.safetyMessage,
                                        onLongClick = { offset -> openMessageMenu(msg, offset) },
                                    )
                                    card != null -> CoachCard(
                                        card = card,
                                        messageId = msg.id,
                                        onCopy = ::copy,
                                        onLongClick = { offset -> openMessageMenu(msg, offset) },
                                        createdAt = msg.createdAt,
                                    )
                                    else -> MessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
                                }
                            }
                            MessageType.IMAGE ->
                                // v1.3.1 点击图片气泡 → 全屏预览
                                ImageMessageBubble(
                                    msg,
                                    onClick = { previewFor = msg },
                                    onLongClick = { offset -> openMessageMenu(msg, offset) },
                                )
                            MessageType.FREETEXT -> {
                                // v1.3.1 freetext 融合：话术段提升为上方可复制话术卡，下方正文气泡；
                                // 无话术段退化为纯文本气泡
                                if (textSelectForId == msg.id) {
                                    // v1.6.2 选择模式：整条原文可拖选，文本为话术+正文拼接（去掉引导词）
                                    val split = remember(msg.id) { FreetextSplitter.split(msg.content) }
                                    val selectableText = if (split.reply.isBlank()) {
                                        msg.content
                                    } else {
                                        split.reply + "\n\n" + split.body
                                    }
                                    SelectableMessageContent(msg, text = selectableText)
                                } else {
                                    val split = remember(msg.id) { FreetextSplitter.split(msg.content) }
                                    if (split.reply.isBlank()) {
                                        MessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
                                    } else {
                                        FreetextBubble(
                                            msg,
                                            split,
                                            onCopyReply = ::copy,
                                            onLongClick = { offset -> openMessageMenu(msg, offset) },
                                        )
                                    }
                                }
                            }
                            MessageType.TEXT, MessageType.TRANSCRIPTION ->
                                if (textSelectForId == msg.id) {
                                    // v1.6.2 选择模式：进入即全选，拖两端手柄部分复制
                                    SelectableMessageContent(msg)
                                } else {
                                    MessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
                                }
                        }
                    }
                    transcription?.let { t ->
                        item(key = "transcription") {
                            var edited by remember(t) { mutableStateOf(t) }
                            TranscriptionCard(
                                transcription = edited,
                                onTranscriptionChange = { edited = it },
                                onConfirm = { vm.confirmTranscription(edited) },
                                onReselect = { vm.stop() },
                            )
                        }
                    }
                    lastError?.let { e ->
                        item(key = "error") {
                            ErrorCard(
                                error = e,
                                onRetry = vm::retry,
                                onCancel = vm::stop,
                                onGoSettings = onOpenSettings,
                            )
                        }
                    }
                    if (streaming) {
                        // v1.9.2 对齐桌面端：流式期间仅显示等待气泡（玻璃容器+三点呼吸+三档文案），
                        // 不再边流边出预览；完整回复到达后走 messages 列表一次性整块渲染
                        item(key = "streaming") {
                            TypingIndicator(label = resolveWaitingLabel(transcribing, confirming))
                        }
                    }
                }
                // v1.2.1 滚动跟随修复：仅当用户位于底部时才自动滚到底。
                // 此前无条件 scrollToItem 导致流式每来一个 token 就把上滑看历史的用户拽回底部。
                // 动态项（thinking/streaming/transcription/error）都计入 totalItemsCount。
                val isAtBottom by remember(listState) {
                    derivedStateOf {
                        val layout = listState.layoutInfo
                        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                        layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 1
                    }
                }
                LaunchedEffect(messages.size, streaming, transcription, isAtBottom) {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0 && isAtBottom && !listState.isScrollInProgress) {
                        listState.scrollToItem(count - 1)
                    }
                }
            }
        }
    }
    } // Box（GlowBackground + Scaffold）
    } // ModalNavigationDrawer

    // 长按消息操作菜单：文本类可复制/删除，图片仅删除。
    // v1.2.1：offset 跟随长按触点（窗口坐标），菜单出现在手指处而非固定左下角。
    menuFor?.let { msg ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuFor = null },
            offset = with(LocalDensity.current) {
                // 无障碍触发（读屏长按）无坐标传 Zero，给默认落点避免菜单贴顶
                if (menuOffset == Offset.Zero) DpOffset(24.dp, 120.dp)
                else DpOffset(menuOffset.x.toDp(), menuOffset.y.toDp())
            },
        ) {
            if (msg.type != MessageType.IMAGE) {
                // v1.3.1 freetext 融合：话术与正文分开复制（有话术段时提供两项）
                if (msg.type == MessageType.FREETEXT) {
                    val freetextReply = remember(msg.id) { FreetextSplitter.split(msg.content).reply }
                    if (freetextReply.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("复制话术") },
                            onClick = {
                                copy(freetextReply)
                                menuFor = null
                            },
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(if (msg.type == MessageType.FREETEXT) "复制全文" else "复制") },
                    onClick = {
                        // v1.6：分析卡复制成品话术（reply，新老 JSON 均兼容）而非原始 JSON
                        val copyText = when (msg.type) {
                            MessageType.ANALYSIS -> UiMappers.parseCoachCard(msg.content)
                                ?.reply?.takeIf { it.isNotBlank() } ?: msg.content
                            else -> msg.content
                        }
                        copy(copyText)
                        menuFor = null
                    },
                )
                // v1.6.2 部分选取复制：进入文本选择模式——立即全选，拖动两端手柄选取部分内容后复制
                if (msg.type == MessageType.TEXT ||
                    msg.type == MessageType.TRANSCRIPTION ||
                    msg.type == MessageType.FREETEXT ||
                    msg.type == MessageType.ANALYSIS
                ) {
                    DropdownMenuItem(
                        text = { Text("部分选择") },
                        onClick = {
                            textSelectForId = msg.id
                            menuFor = null
                            Toast.makeText(context, "拖动两端手柄选取文字，点空白处完成", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    confirmDeleteFor = msg
                    menuFor = null
                },
            )
        }
    }

    // 删除二次确认
    confirmDeleteFor?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            shape = GtjShape.lg,
            title = { Text("删除这条消息？") },
            text = { Text(if (msg.type == MessageType.IMAGE) "这张图片消息将被删除，无法恢复。" else "这条消息将被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteMessage(msg.id)
                        confirmDeleteFor = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFor = null }) { Text("取消") }
            },
        )
    }

    // 长按会话条目 → 删除确认
    confirmDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSession = null },
            shape = GtjShape.lg,
            title = { Text("删除这个会话？") },
            text = { Text("「${session.title}」将被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSession(session.id)
                        confirmDeleteSession = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSession = null }) { Text("取消") }
            },
        )
    }

    if (showModelSheet) {
        ModelSheet(
            models = models,
            currentModelId = currentId,
            onSelect = { id ->
                scope.launch { container.settingsRepository.setCurrentModel(id) }
                // v1.7.0：切模型 → 状态点"连接中"杏棕呼吸 900ms → 已连接
                pendingSwitch = true
                showModelSheet = false
            },
            onDismiss = { showModelSheet = false },
            onManageProviders = {
                showModelSheet = false
                onOpenSettings()
            },
        )
    }

    // v1.3.1 全屏图片预览（点击图片气泡打开；复用 data URL 解码缓存）
    previewFor?.let { msg ->
        ImagePreviewOverlay(
            message = msg,
            onDismiss = { previewFor = null },
        )
    }
}

/** v1.7.0 顶栏状态点四态（原型 sdot，色值跨主题恒定于 GtjPalette.dot*） */
enum class DotState { Idle, Connecting, Thinking, Failure }

@Composable
private fun ChatTopBar(
    modelName: String,
    dotState: DotState,
    onModelClick: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
) {
    val p = LocalGtjColors.current
    val reduced = rememberReducedMotion()
    // 呼吸动画：Connecting 0.8s / Thinking 1.2s（原型 breathe），reducedMotion 时静态全亮
    val pulse = if (!reduced) rememberInfiniteTransition(label = "dotPulse") else null
    val pulseAlpha by if (pulse != null) {
        pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (dotState == DotState.Connecting) 800 else 1200,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotPulse",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    val breathing = dotState == DotState.Connecting || dotState == DotState.Thinking
    val coreAlpha = if (breathing) pulseAlpha else 1f
    val coreColor = when (dotState) {
        DotState.Idle -> p.dotConnected
        DotState.Connecting -> p.dotConnecting
        DotState.Thinking -> p.dotThinking
        DotState.Failure -> p.dotFailure
    }

    // v1.7.1 二改续：顶栏改悬浮胶囊（与输入栏同款 r28 strong 玻璃 + 软投影），
    // 外层留 12/8 悬浮留白保证投影可见；edge-to-edge 状态栏内边距保留
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                // v1.8.0 液态玻璃 2.0：边缘透镜（v1.8.1 B4 移除光斑 dead path）
                .liquidGlass(shape = GtjShape.inputBar)
                .clip(GtjShape.inputBar),
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
        ) {
            GtjIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = "打开历史会话",
                onClick = onMenu,
                tint = p.fgSecondary,
            )
            // v1.5：顶栏标题"温言"（克制的中文字标，不喧宾夺主）
            Text(
                text = "温言",
                style = GtjType.Title.copy(fontSize = 17.sp, lineHeight = 24.sp),
                color = p.fg,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            // 模型 pill：玻璃胶囊（radius 99 / maxWidth 158 / 内 padding 5,11），内含微型玻璃状态点
            GlassSurface(
                onClick = onModelClick,
                shape = GtjShape.pill,
                modifier = Modifier.widthIn(max = 158.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                ) {
                    // 微型玻璃状态点：11dp 玻璃壳（同款 glass 材质）+ 5dp 状态色内芯（呼吸）
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(11.dp).liquidGlass(shape = CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(coreColor.copy(alpha = coreAlpha), CircleShape),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        modelName,
                        style = GtjType.Label,
                        color = p.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "切换模型", modifier = Modifier.size(16.dp), tint = p.meta)
                }
            }
            Spacer(Modifier.width(4.dp))
            GtjIconButton(icon = Icons.Outlined.Settings, contentDescription = "设置", onClick = onSettings, tint = p.fgSecondary)
        }
        } // 玻璃胶囊
    }
}
