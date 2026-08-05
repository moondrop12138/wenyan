package com.wenyan.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.5 空状态重构（设计稿 WY-01，Arc/Things 温暖质感）：
 * - 日期行 + 箴言（24sp Medium 主视觉）+ 副文案
 * - 2×2 示例卡（173×56，r12，柔和投影感由 surfaceElevated + 边框承担）
 * - 双引导卡（图标带 8% 底色圆形容器，粘贴=陶土棕 / 截图=赭石，暖色点缀）
 * 示例问题池保持 50 句（v1.3.1 扩充），改为网格展示前 4 条。
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
 * 首启空状态引导（v1.5 WY-01）：日期 + 箴言 + 示例网格 + 双引导卡。
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
        Spacer(Modifier.height(64.dp))
        // v1.5：日期行（12sp Regular，muted）
        Text(
            text = formatToday(),
            style = GtjType.Caption,
            color = p.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        // 箴言：24sp Medium 主视觉（设计稿 WY-01 核心文案）
        Text(
            text = "先接住情绪，再分清事实",
            style = GtjType.Headline,
            color = p.fg,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "把聊天记录粘进来，或直接说你的处境。\n数据只发往你配置的模型服务。",
            style = GtjType.BodySm,
            color = p.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        // 2×2 示例卡网格（设计稿：173×56，r12）
        val grid = EXAMPLE_QUESTIONS.take(4)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grid.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { q ->
                        ExampleGridCard(
                            text = q,
                            onClick = { onExampleClick(q) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        // 双引导卡（v1.5：图标带 8% 底色圆角容器，粘贴=陶土棕 / 截图=赭石）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GuideEntryCard(
                icon = {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = p.accent,
                    )
                },
                iconBg = p.accent,
                title = "粘贴聊天记录",
                subtitle = "从剪贴板导入",
                onClick = {
                    clipboard.getText()?.text?.toString()?.let(onPasteText)
                },
                modifier = Modifier.weight(1f),
            )
            GuideEntryCard(
                icon = {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = p.warm,
                    )
                },
                iconBg = p.warm,
                title = "选择截图分析",
                subtitle = "自动识别图中对话",
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** v1.5 示例卡：173×56 圆角 12，surfaceElevated 底 + borderSoft 边（温暖卡片感） */
@Composable
private fun ExampleGridCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.borderSoft),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = text,
                style = GtjType.BodySm,
                color = p.fgSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/**
 * v1.5 引导入口卡：173×64 圆角 12，图标 32dp 圆角 8 容器（8% 底色暖点缀）。
 * iconBg 传 accent（陶土棕）或 warm（赭石），内部取 8% 透明度。
 */
@Composable
private fun GuideEntryCard(
    icon: @Composable () -> Unit,
    iconBg: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.borderSoft),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            // 图标容器：8% 底色圆角 8（温暖质感关键细节）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(iconBg.copy(alpha = 0.08f), GtjShape.sm),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = GtjType.Label, color = p.fg, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = GtjType.Caption, color = p.muted, maxLines = 1)
            }
        }
    }
}

private fun formatToday(): String =
    SimpleDateFormat("M月d日 · EEE", Locale.CHINA).format(Date())
