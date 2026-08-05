package com.wenyan.app.ui.chat

import android.widget.Toast
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.geometry.Offset
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
import com.wenyan.app.ui.components.ThinkingPanel
import com.wenyan.app.ui.components.TranscriptionCard
import com.wenyan.app.ui.components.TypingIndicator
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ChatMessageUi
import com.wenyan.app.ui.contract.MessageType
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
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
    val streamingText by vm.streamingText.collectAsState()
    val streamingThinking by vm.streamingThinking.collectAsState()
    val input by vm.input.collectAsState()
    val pendingImages by vm.pendingImages.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val transcription by vm.transcription.collectAsState()
    val modelName by vm.currentModelName.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.bg,
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                modelName = modelName,
                thinking = streaming,
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
                ChatEmptyState(
                    onExampleClick = vm::onInputChange,
                    onPasteText = vm::onInputChange,
                    // v1.3.1 统一走待发送预览流程（v1.6.1 多图，选完照片停到输入框上方，不直接发）
                    onImagesPicked = vm::addPendingImages,
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
                                    // v1.6.1 选择模式：整条原文（含话术段）可拖选部分复制
                                    SelectableMessageContent(msg)
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
                                    // v1.6.1 选择模式：长按文字拖选手柄部分复制
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
                        // 深度思考模型的 reasoning_content：折叠面板，用户可选展开
                        if (streamingThinking.isNotBlank()) {
                            item(key = "thinking") {
                                ThinkingPanel(thinking = streamingThinking, streaming = true)
                            }
                        }
                        item(key = "streaming") {
                            // v1.6 统一 structured：模型输出四段 JSON，流式期间只抽取有意义的字段预览，
                            // 避免把 {"empathy":"...","facts":{... 这种原始 JSON 糊在气泡里。
                            // 判据：累积文本一旦呈现 JSON 起始形态就按 structured 处理。
                            val trimmed = streamingText.trimStart()
                            val looksStructured = trimmed.startsWith("{") || trimmed.startsWith("```")
                            when {
                                looksStructured -> {
                                    // 优先成品话术 reply；未出现时退回共情段 empathy（字段顺序靠前，预览尽早出现）
                                    val replyPreview = StreamingPreview.extractReplyPreview(streamingText)
                                        ?: StreamingPreview.extractEmpathyPreview(streamingText)
                                    when {
                                        replyPreview != null -> StreamingBubble(text = replyPreview)
                                        streamingThinking.isNotBlank() -> StreamingPlaceholderBubble()
                                        else -> TypingIndicator()
                                    }
                                }
                                streamingText.isNotBlank() -> StreamingBubble(text = streamingText)
                                streamingThinking.isNotBlank() -> StreamingPlaceholderBubble()
                                else -> TypingIndicator()
                            }
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
                LaunchedEffect(messages.size, streamingText, streamingThinking, transcription, isAtBottom) {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0 && isAtBottom && !listState.isScrollInProgress) {
                        listState.scrollToItem(count - 1)
                    }
                }
            }
        }
    }
    }

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
                // v1.6.1 部分选取复制：进入文本选择模式，长按文字拖动手柄选取部分内容后复制
                if (msg.type == MessageType.TEXT ||
                    msg.type == MessageType.TRANSCRIPTION ||
                    msg.type == MessageType.FREETEXT
                ) {
                    DropdownMenuItem(
                        text = { Text("选择文字") },
                        leadingIcon = {
                            Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            textSelectForId = msg.id
                            menuFor = null
                            Toast.makeText(context, "长按消息文字拖动选取，点空白处完成", Toast.LENGTH_SHORT).show()
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

@Composable
private fun ChatTopBar(
    modelName: String,
    thinking: Boolean,
    onModelClick: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
) {
    val p = LocalGtjColors.current
    Surface(color = p.bg) {
        // edge-to-edge：顶栏整体下移状态栏高度（insets 自适应，不同机型高度不同）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
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
                Surface(
                    onClick = onModelClick,
                    shape = GtjShape.pill,
                    color = p.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.borderSoft),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        // v1.5：状态点——思考中赭石，空闲陶土棕
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (thinking) p.warm else p.accent,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(modelName, style = GtjType.Label, color = p.fgSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ExpandMore, contentDescription = "切换模型", modifier = Modifier.size(16.dp), tint = p.meta)
                    }
                }
                Spacer(Modifier.width(4.dp))
                GtjIconButton(icon = Icons.Outlined.Settings, contentDescription = "设置", onClick = onSettings, tint = p.fgSecondary)
            }
        }
    }
}
