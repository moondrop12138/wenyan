package com.wenyan.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.ExampleChip
import com.wenyan.app.ui.components.GtjCard
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

private val EXAMPLE_QUESTIONS = listOf(
    "他三天没回消息，怎么开口",
    "这句话怎么回比较好",
    "我们是不是该结束了",
    "第一次约会聊什么不冷场",
)

/**
 * 首启空状态引导（design-pages 页面1）：问候 + 示例 chips（点击填入输入框）+ 两个入口卡。
 * 绝不使用空洞欢迎语。
 */
@Composable
fun ChatEmptyState(
    onExampleClick: (String) -> Unit,
    onPasteText: (String) -> Unit,
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val clipboard = LocalClipboardManager.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onImagePicked) },
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        // UI 定稿：去掉顶部大标题 slogan，只保留一行说明 + 示例 chips + 入口卡
        Text(
            "把聊天记录粘进来，或直接说你的处境。数据只发往你配置的模型服务。",
            style = GtjType.BodySm,
            color = p.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EXAMPLE_QUESTIONS) { q ->
                ExampleChip(text = q, onClick = { onExampleClick(q) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            GtjCard(
                onClick = {
                    clipboard.getText()?.text?.toString()?.let(onPasteText)
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(24.dp), tint = p.accent)
                    Spacer(Modifier.height(8.dp))
                    Text("粘贴聊天记录", style = GtjType.Label, color = p.fg, textAlign = TextAlign.Center)
                }
            }
            GtjCard(
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(24.dp), tint = p.accent)
                    Spacer(Modifier.height(8.dp))
                    Text("选择截图分析", style = GtjType.Label, color = p.fg, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
