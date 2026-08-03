package com.goutoujunshi.app.ui.chat

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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.container.UiMappers
import com.goutoujunshi.app.ui.components.AnalysisCard
import com.goutoujunshi.app.ui.components.CrisisCard
import com.goutoujunshi.app.ui.components.ErrorCard
import com.goutoujunshi.app.ui.components.GtjIconButton
import com.goutoujunshi.app.ui.components.ModelSheet
import com.goutoujunshi.app.ui.components.TranscriptionCard
import com.goutoujunshi.app.ui.components.TypingIndicator
import com.goutoujunshi.app.ui.contract.AppContainer
import com.goutoujunshi.app.ui.contract.MessageType
import com.goutoujunshi.app.ui.navigation.rememberViewModel
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors
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
    val input by vm.input.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val transcription by vm.transcription.collectAsState()
    val modelName by vm.currentModelName.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var showModelSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val models by container.settingsRepository.models.collectAsState(initial = emptyList())
    val currentId by container.settingsRepository.currentModelId.collectAsState(initial = null)
    val p = LocalGtjColors.current

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                modelName = modelName,
                onModelClick = { showModelSheet = true },
                onSettings = onOpenSettings,
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
                                    )
                                    card != null -> AnalysisCard(card = card, onCopy = ::copy)
                                    else -> MessageBubble(msg)
                                }
                            }
                            MessageType.TRANSCRIPTION -> MessageBubble(msg)
                            MessageType.TEXT, MessageType.IMAGE -> MessageBubble(msg)
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
                        item(key = "streaming") {
                            if (streamingText.isNotBlank()) {
                                StreamingBubble(text = streamingText)
                            } else {
                                TypingIndicator()
                            }
                        }
                    }
                }
                LaunchedEffect(messages.size, streamingText, transcription) {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0) listState.scrollToItem(count - 1)
                }
            }
        }
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
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            ) {
                Text("狗头军师", style = GtjType.Title, color = p.fg)
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
