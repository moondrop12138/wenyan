package com.goutoujunshi.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.goutoujunshi.app.ui.components.GtjIconButton
import com.goutoujunshi.app.ui.theme.GtjShape
import com.goutoujunshi.app.ui.theme.GtjType
import com.goutoujunshi.app.ui.theme.LocalGtjColors

/**
 * 底部输入栏（design-tokens component.inputBar，design-pages 页面1）：
 * 回形针（粘贴文本/选择截图）+ TextField + 发送/停止。流式时右侧替换为 stop。
 */
@Composable
fun ChatInputBar(
    input: String,
    streaming: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPasteText: (String) -> Unit,
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onImagePicked) },
    )

    // edge-to-edge：bottomBar 不自动处理 insets，手动下移导航栏高度（手势条/三键自适应）
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = p.bg,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box {
                GtjIconButton(
                    icon = Icons.Outlined.AttachFile,
                    contentDescription = "添加聊天记录",
                    onClick = { menuExpanded = true },
                    tint = p.muted,
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
            Spacer(Modifier.width(8.dp))
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                placeholder = { Text("说点什么，或粘贴聊天记录…", style = GtjType.BodySm, color = p.meta) },
                textStyle = GtjType.Body,
                shape = GtjShape.pill,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = p.surface,
                    unfocusedContainerColor = p.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = p.accent,
                ),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                Surface(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp),
                    shape = GtjShape.pill,
                    color = p.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Stop, contentDescription = "停止生成", modifier = Modifier.size(22.dp), tint = p.fgSecondary)
                    }
                }
            } else {
                Surface(
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    shape = GtjShape.pill,
                    color = if (input.isNotBlank()) p.accent else p.borderSoft,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(20.dp),
                            tint = if (input.isNotBlank()) p.accentOn else p.meta,
                        )
                    }
                }
            }
        }
    }
}
