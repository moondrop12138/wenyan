package com.wenyan.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.ChoiceChips
import com.wenyan.app.ui.components.MbtiPicker
import com.wenyan.app.ui.components.SliderField
import com.wenyan.app.ui.contract.OnboardingDraft
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val RELATION_OPTIONS = com.wenyan.app.domain.RELATION_STATUS_OPTIONS  // L31: 与记忆编辑页共用
private val MEET_OPTIONS = listOf("朋友介绍", "社交软件", "同学同事", "偶遇", "其他")
private val DURATION_OPTIONS = listOf("刚认识", "1-3 个月", "3-6 个月", "半年以上")
private val INVEST_OPTIONS = listOf("我主动多", "对方主动多", "差不多")
private val GOAL_OPTIONS = listOf("推进", "确认", "修复", "比较选择", "退出")

/** 屏1 本人："先让我认识你" */
@Composable
fun StepOneMe(draft: OnboardingDraft, onChange: (OnboardingDraft) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text("先让我认识你", style = GtjType.Title, color = LocalGtjColors.current.fg)
        MbtiPicker(value = draft.meMbti, onChange = { onChange(draft.copy(meMbti = it)) })
        SliderField(
            value = draft.meScore ?: 50,
            range = 0..100,
            label = "主观综合评分",
            onValueChange = { onChange(draft.copy(meScore = it)) },
        )
        DraftTextField(
            value = draft.strengths,
            placeholder = "主要优势（选填）",
            onChange = { onChange(draft.copy(strengths = it)) },
            label = "主要优势",
        )
        DraftTextField(
            value = draft.weaknesses,
            placeholder = "主要短板（选填）",
            onChange = { onChange(draft.copy(weaknesses = it)) },
            label = "主要短板",
        )
    }
}

/** 屏2 对象："TA 呢？给 TA 起个代号" */
@Composable
fun StepTwoTarget(draft: OnboardingDraft, onChange: (OnboardingDraft) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text("TA 呢？给 TA 起个代号", style = GtjType.Title, color = LocalGtjColors.current.fg)
        DraftTextField(
            value = draft.targetCodeName,
            placeholder = "如：阿岚",
            onChange = { onChange(draft.copy(targetCodeName = it)) },
            label = "代号",
        )
        MbtiPicker(value = draft.targetMbti, onChange = { onChange(draft.copy(targetMbti = it)) }, showUnknown = true)
        SliderField(
            value = draft.targetScore ?: 50,
            range = 0..100,
            label = "主观综合评分",
            onValueChange = { onChange(draft.copy(targetScore = it)) },
        )
        Text("当前关系", style = GtjType.Label, color = LocalGtjColors.current.muted)
        ChoiceChips(
            options = RELATION_OPTIONS,
            selected = draft.relationStatus,
            onSelect = { onChange(draft.copy(relationStatus = it)) },
        )
    }
}

/** 屏3 经过："你们是怎么走到现在的" */
@Composable
fun StepThreeHistory(draft: OnboardingDraft, onChange: (OnboardingDraft) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text("你们是怎么走到现在的", style = GtjType.Title, color = LocalGtjColors.current.fg)
        Text("认识方式", style = GtjType.Label, color = LocalGtjColors.current.muted)
        ChoiceChips(options = MEET_OPTIONS, selected = draft.meetWay, onSelect = { onChange(draft.copy(meetWay = it)) })
        Text("发展多久", style = GtjType.Label, color = LocalGtjColors.current.muted)
        ChoiceChips(options = DURATION_OPTIONS, selected = draft.duration, onSelect = { onChange(draft.copy(duration = it)) })
        DraftTextField(
            value = draft.keyEvents,
            placeholder = "最近三件关键事件（选填）",
            onChange = { onChange(draft.copy(keyEvents = it)) },
            label = "关键事件",
            minLines = 3,
        )
        Text("联系与投入", style = GtjType.Label, color = LocalGtjColors.current.muted)
        ChoiceChips(options = INVEST_OPTIONS, selected = draft.investment, onSelect = { onChange(draft.copy(investment = it)) })
    }
}

/** 屏4 目标+情绪："你想要什么，现在最难的是什么" */
@Composable
fun StepFourGoal(draft: OnboardingDraft, onChange: (OnboardingDraft) -> Unit) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text("你想要什么，现在最难的是什么", style = GtjType.Title, color = LocalGtjColors.current.fg)
        Text("目标", style = GtjType.Label, color = LocalGtjColors.current.muted)
        ChoiceChips(options = GOAL_OPTIONS, selected = draft.goal, onSelect = { onChange(draft.copy(goal = it)) })
        DraftTextField(
            value = draft.painPoint,
            placeholder = "最难受的点",
            onChange = { onChange(draft.copy(painPoint = it)) },
            label = "最难受的点",
            minLines = 3,
        )
        SliderField(
            value = draft.emotionIntensity,
            range = 0..10,
            label = "情绪强度",
            onValueChange = { onChange(draft.copy(emotionIntensity = it)) },
            step = 1,
        )
        if (draft.emotionIntensity > 7) {
            // 对比度：浅色 warn(#D97706) 白底仅 3.2:1，改用 warmOn(#B45309) 5.0:1 达标
            Text("会先安抚再给完整分析", style = GtjType.BodySm, color = LocalGtjColors.current.warmOn)
        }
        var urgent by remember { mutableStateOf(draft.urgentReply) }
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("眼下有没有必须马上回的话", style = GtjType.Label, color = LocalGtjColors.current.fg, modifier = Modifier.weight(1f))
            Switch(
                checked = urgent,
                onCheckedChange = {
                    urgent = it
                    onChange(draft.copy(urgentReply = it))
                },
                // 无障碍：Switch 无相邻文本语义，显式关联 label
                modifier = Modifier.semantics { contentDescription = "眼下有没有必须马上回的话" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LocalGtjColors.current.accentOn,
                    checkedTrackColor = LocalGtjColors.current.accent,
                    uncheckedTrackColor = LocalGtjColors.current.borderSoft,
                ),
            )
        }
        if (urgent) {
            DraftTextField(
                value = draft.urgentText,
                placeholder = "要回的那句话",
                onChange = { onChange(draft.copy(urgentText = it)) },
                label = "待回复消息",
            )
        }
    }
}

@Composable
private fun DraftTextField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
) {
    val p = LocalGtjColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, style = GtjType.Label) },
        placeholder = { Text(placeholder, style = GtjType.BodySm, color = p.meta) },
        minLines = minLines,
        shape = com.wenyan.app.ui.theme.GtjShape.sm,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent,
            unfocusedBorderColor = p.border,
            focusedContainerColor = p.surface,
            unfocusedContainerColor = p.surface,
            cursorColor = p.accent,
        ),
    )
}
