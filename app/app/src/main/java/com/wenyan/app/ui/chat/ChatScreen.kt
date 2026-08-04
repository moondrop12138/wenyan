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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.wenyan.app.container.UiMappers
import com.wenyan.app.ui.components.AnalysisCard
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
    // v1.2.1 长按触点窗口坐标（Offset.Zero = 无障碍触发无坐标，用默认落点）→ DropdownMenu offset
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    var confirmDeleteFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var confirmDeleteSession by remember { mutableStateOf<SessionSummaryUi?>(null) }
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
                onModelClick = { showModelSheet = true },
                onSettings = onOpenSettings,
                onMenu = { scope.launch { drawerState.open() } },
            )
        },
        bottomBar = {
            ChatInputBar(
                input = input,
                streaming = streaming,
                onInputChange = vm::onInputChange,
                onSend = { vm.sendText() },
                onStop = vm::stop,
                onPasteText = { vm.onInputChange(it) },
                onImagePicked = vm::analyzeImage,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (messages.isEmpty() && !streaming && transcription == null && lastError == null) {
                ChatEmptyState(
                    onExampleClick = vm::onInputChange,
                    onPasteText = vm::onInputChange,
                    onImagePicked = vm::analyzeImage,
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
                                val card = UiMappers.parseAnalysisCard(msg.content)
                                when {
                                    card?.safetyOverride == true -> CrisisCard(
                                        onAcknowledge = {},
                                        safetyMessage = card.safetyMessage,
                                        onLongClick = { offset -> openMessageMenu(msg, offset) },
                                    )
                                    card != null -> AnalysisCard(
                                        card = card,
                                        onCopy = ::copy,
                                        onLongClick = { offset -> openMessageMenu(msg, offset) },
                                    )
                                    else -> MessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
                                }
                            }
                            MessageType.IMAGE ->
                                ImageMessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
                            MessageType.TEXT, MessageType.TRANSCRIPTION, MessageType.FREETEXT ->
                                MessageBubble(msg, onLongClick = { offset -> openMessageMenu(msg, offset) })
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
                            // v1.3 混合渲染：
                            // - freetext 模式：模型直出自然中文，流式文本直接打字机上屏；
                            // - structured 模式：模型输出 JSON，流式期间只抽取 reply 字段预览，
                            //   避免把 {"steps":[{"key":"emotion",... 这种原始 JSON 糊在气泡里。
                            // 判据：累积文本一旦呈现 JSON 起始形态就按 structured 处理，否则按 freetext。
                            val trimmed = streamingText.trimStart()
                            val looksStructured = trimmed.startsWith("{") || trimmed.startsWith("```")
                            when {
                                looksStructured -> {
                                    val replyPreview = StreamingPreview.extractReplyPreview(streamingText)
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
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        // v1.2.1：分析卡复制成品话术（reply）而非原始 JSON
                        val copyText = when (msg.type) {
                            MessageType.ANALYSIS -> UiMappers.parseAnalysisCard(msg.content)
                                ?.reply?.takeIf { it.isNotBlank() } ?: msg.content
                            else -> msg.content
                        }
                        copy(copyText)
                        menuFor = null
                    },
                )
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
}

@Composable
private fun ChatTopBar(
    modelName: String,
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
                // 顶栏不显示产品名（UI 定稿：极简，少装饰性文案）
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onModelClick,
                    shape = GtjShape.pill,
                    color = p.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(modelName, style = GtjType.Label, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ExpandMore, contentDescription = "切换模型", modifier = Modifier.size(16.dp), tint = p.muted)
                    }
                }
                Spacer(Modifier.width(4.dp))
                GtjIconButton(icon = Icons.Outlined.Settings, contentDescription = "设置", onClick = onSettings, tint = p.fgSecondary)
            }
        }
    }
}
