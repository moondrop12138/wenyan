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

/**
 * v1.3.1 示例问题池扩充至 50 句（横向 LazyRow 可滚动，不再总是开头那几句）。
 * 覆盖：不回消息/冷淡/冷战/吵架/表白/约会/相亲/异地/见家长/分手复合/吃醋/前任/三观 等恋爱军师高频场景。
 */
private val EXAMPLE_QUESTIONS = listOf(
    "他三天没回消息，怎么开口",
    "这句话怎么回比较好",
    "我们是不是该结束了",
    "第一次约会聊什么不冷场",
    "她突然冷淡了，我该追问吗",
    "表白被婉拒，还能做朋友吗",
    "他总说忙，是借口还是真忙",
    "异地恋快撑不下去了怎么办",
    "相亲对象很优秀，我有点自卑",
    "冷战三天了，谁先低头",
    "他从不主动发消息正常吗",
    "对象和异性同事走太近怎么办",
    "她说我们只是朋友，怎么回",
    "约会迟到一小时，该生气吗",
    "第一次见家长要注意什么",
    "他总是忘记我们的纪念日",
    "吵架后他拉黑了我怎么办",
    "我想复合，该怎么开口",
    "他官宣了别人，我该祝福吗",
    "刚加的微信，第一句说什么",
    "她总在我面前提别的男生",
    "父母反对我们在一起怎么办",
    "他不告诉我工资，正常吗",
    "情人节他什么都没准备",
    "他说需要冷静一段时间",
    "感情变淡了，还有救吗",
    "她生气了，但我不知道错哪",
    "他总拿我和前任比较",
    "恋爱半年想同居，该答应吗",
    "她约我看电影，该怎么表现",
    "他深夜给女同事点赞，我吃醋了",
    "三观不合还能走下去吗",
    "他说暂时不想结婚，要等吗",
    "被劈腿了，怎么走出来",
    "她爸妈想见我，好紧张",
    "他总说随便，很敷衍怎么办",
    "在一起很累，要不要分手",
    "他喝醉说还爱着前任",
    "她工作比我重要，该理解吗",
    "网恋奔现要注意什么",
    "他从不夸我，是我不够好吗",
    "她突然不回消息两天了",
    "认识三个月，该确定关系吗",
    "他买礼物总不合我心意",
    "她说先忙事业，不考虑恋爱",
    "我该主动约他第二次吗",
    "他朋友圈没有我的痕迹",
    "她闺蜜不喜欢我，怎么办",
    "我们总为小事吵架",
    "他承诺的事总做不到，还信吗",
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
