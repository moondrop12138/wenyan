package com.wenyan.app.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.components.ChoiceChips
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.MbtiPicker
import com.wenyan.app.ui.components.PrimaryButton
import com.wenyan.app.ui.components.SliderField
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.components.glass.GlowBackground
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/** 关系状态分段选项（PRD R2，与 onboarding 语义对齐） */
private val RELATION_OPTIONS = com.wenyan.app.domain.RELATION_STATUS_OPTIONS  // L31: 与引导页共用

/**
 * v1.7.3 档案详情页（/settings/memory/:id，仿 ProviderEditScreen 二级页范式）：
 * 名称 / MBTI（MbtiPicker 复用）/ 吸引力分（SliderField 复用）/ 关系状态（分段选择）/
 * 关键事件（时间+事件列表，可新增删除）/ 已记住的事实（单条编辑、删除不二次确认）。
 * 入口 = 设置页记忆分组档案行「编辑」图标。
 */
@Composable
fun MemoryEditScreen(
    container: AppContainer,
    targetId: Long,
    onBack: () -> Unit,
) {
    val vm: MemoryEditViewModel = rememberViewModel("MemoryEdit_$targetId") {
        MemoryEditViewModel(container.settingsRepository, targetId)
    }
    val p = LocalGtjColors.current

    // v1.8.1 B4：移除 glowState 光斑共享——dead path 且每帧重组开销大

    Box(Modifier.fillMaxSize().background(p.bg)) {
        GlowBackground()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .liquidGlass(shape = GtjShape.inputBar)
                            .clip(GtjShape.inputBar),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        ) {
                            GtjIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", onClick = onBack)
                            Text("档案详情", style = GtjType.Title, color = p.fg)
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!vm.loading && vm.target == null) {
                    Text(
                        "档案不存在或已删除",
                        style = GtjType.BodySm,
                        color = p.muted,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = vm.name,
                            onValueChange = { vm.name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("名称", style = GtjType.Label) },
                            textStyle = GtjType.Body,
                            singleLine = true,
                            shape = GtjShape.md,
                            colors = editFieldColors(),
                        )
                        Column {
                            Text("MBTI", style = GtjType.Label, color = p.muted)
                            Spacer(Modifier.height(8.dp))
                            MbtiPicker(value = vm.mbti, onChange = { vm.mbti = it }, showUnknown = true)
                        }
                        SliderField(
                            value = vm.score,
                            range = 0..100,
                            label = "吸引力分",
                            onValueChange = { vm.score = it },
                            step = 5,
                        )
                        Column {
                            Text("关系状态", style = GtjType.Label, color = p.muted)
                            Spacer(Modifier.height(8.dp))
                            ChoiceChips(
                                options = RELATION_OPTIONS,
                                selected = vm.relationStatus,
                                onSelect = { vm.relationStatus = it },
                            )
                        }
                        TimelineEditor(vm)
                    }

                    // 已记住的事实
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = GtjShape.md,
                        color = p.surfaceElevated,
                        border = BorderStroke(1.dp, p.border),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("已记住的事实", style = GtjType.Label, color = p.muted, modifier = Modifier.weight(1f))
                                GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加事实", onClick = vm::openCreateFact, tint = p.accent, iconSize = 20.dp)
                            }
                            if (vm.conflictPairs.isNotEmpty()) {
                                Text(
                                    "发现 ${vm.conflictPairs.size} 组疑似冲突，点击裁决",
                                    style = GtjType.Caption,
                                    color = p.danger,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                                vm.conflictPairs.forEach { pair ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(GtjShape.sm)
                                            .background(p.dangerSoft)
                                            .clickable { vm.openConflict(pair) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(pair.a.text, style = GtjType.Caption, color = p.danger, maxLines = 2)
                                            Text(pair.b.text, style = GtjType.Caption, color = p.danger, maxLines = 2)
                                        }
                                        Text("裁决", style = GtjType.Caption, color = p.danger)
                                    }
                                }
                            }
                            if (vm.facts.isEmpty()) {
                                Text("还没有事实，回复后会自动提炼", style = GtjType.BodySm, color = p.meta)
                            } else {
                                vm.facts.forEach { fact ->
                                    MemoryFactRow(
                                        fact = fact,
                                        conflicted = fact.id in vm.conflictFactIds,
                                        onEdit = { vm.openEditFact(fact) },
                                        onDelete = { vm.deleteFact(fact) },
                                        onMakePermanent = { vm.makePermanent(fact) },
                                    )
                                }
                            }
                        }
                    }

                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        PrimaryButton(text = "保存", onClick = { vm.save(onBack) }, loading = vm.saving, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (vm.showFactDialog) {
        FactEditDialog(
            initial = vm.factDraft,
            onDismiss = vm::dismissFactDialog,
            onConfirm = { text -> vm.confirmFact(text) },
        )
    }
    vm.selectedConflict?.let { pair ->
        ConflictResolutionDialog(
            pair = pair,
            onDismiss = vm::dismissConflict,
            onKeepA = vm::resolveConflictKeepA,
            onKeepB = vm::resolveConflictKeepB,
        )
    }
}

/** 关键事件编辑（时间 + 事件两字段，每行可删，➕新增） */
@Composable
private fun TimelineEditor(vm: MemoryEditViewModel) {
    val p = LocalGtjColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = GtjShape.md,
        color = p.surfaceElevated,
        border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关键事件", style = GtjType.Label, color = p.muted, modifier = Modifier.weight(1f))
                GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加关键事件", onClick = vm::addTimelineItem, tint = p.accent, iconSize = 20.dp)
            }
            if (vm.timeline.isEmpty()) {
                Text("还没有关键事件，点击 ➕ 添加（时间 + 事件）", style = GtjType.BodySm, color = p.meta)
            } else {
                // M25 修复：编辑态按原始顺序渲染（forEachIndexed）——原按时间实时重排，
                // 输入「时间」时行跳动、焦点错位，捕获的 index 变为另一项 → 字符写进
                // 另一条事件。顺序固定后捕获索引恒指向本行；初始顺序由 loadFields 排好。
                vm.timeline.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        // 时间轴节点 + 连接线
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (item.time.isNotBlank()) p.accent else p.borderSoft)
                            )
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(52.dp)
                                    .background(p.borderSoft)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = item.time,
                            onValueChange = { vm.updateTimelineItem(index, it, item.event) },
                            modifier = Modifier.weight(0.32f),
                            placeholder = { Text("时间", style = GtjType.BodySm, color = p.meta) },
                            textStyle = GtjType.BodySm,
                            singleLine = true,
                            shape = GtjShape.sm,
                            colors = editFieldColors(),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = item.event,
                            onValueChange = { vm.updateTimelineItem(index, item.time, it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("事件", style = GtjType.BodySm, color = p.meta) },
                            textStyle = GtjType.BodySm,
                            singleLine = true,
                            shape = GtjShape.sm,
                            colors = editFieldColors(),
                        )
                        GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除关键事件", onClick = { vm.deleteTimelineItem(index) }, tint = p.danger, iconSize = 20.dp)
                    }
                }
            }
        }
    }
}

/** 单条事实行：文本 + 徽标（推测/临时/来源）+ 转永久 + 编辑/删除（删除不二次确认） */
@Composable
private fun MemoryFactRow(
    fact: MemoryFactUi,
    conflicted: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakePermanent: () -> Unit,
) {
    val p = LocalGtjColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fact.text, style = GtjType.BodySm, color = if (conflicted) p.danger else p.fg)
            if (conflicted) {
                Text("疑似冲突", style = GtjType.Caption, color = p.danger)
            }
            // v1.9.1 徽标行：临时（有过期时间）/ 来源
            if (fact.expiresAt != null || fact.source != "manual") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (fact.expiresAt != null) {
                        FactBadge(text = "临时", tint = p.accentSoft, content = p.accent)
                    }
                    sourceBadge(fact.source)?.let { (label, tint, content) ->
                        FactBadge(text = label, tint = tint, content = content)
                    }
                }
            }
        }
        // v1.9.0 推断类事实标注（模型推测，区别于用户明确陈述的事实）
        if (fact.kind == "hypothesis") {
            FactBadge(text = "推测", tint = p.borderSoft, content = p.muted)
        }
        // v1.9.1 临时事实可一键转永久（锁形图标）
        if (fact.expiresAt != null) {
            GtjIconButton(
                icon = Icons.Outlined.Lock,
                contentDescription = "转为永久记忆",
                onClick = onMakePermanent,
                iconSize = 18.dp,
            )
        }
        GtjIconButton(icon = Icons.Outlined.Edit, contentDescription = "编辑事实", onClick = onEdit, iconSize = 18.dp)
        GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除事实", onClick = onDelete, tint = p.danger, iconSize = 18.dp)
    }
}

/** v1.9.1 小徽标（临时/推测/来源共用样式） */
@Composable
private fun FactBadge(text: String, tint: Color, content: Color) {
    Text(
        text,
        style = GtjType.Caption,
        color = content,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(GtjShape.sm)
            .background(tint)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** v1.9.1 素材来源徽标（manual 不展示，返回 null；转述用 warn 文字色提示可能有误差） */
@Composable
private fun sourceBadge(source: String): Triple<String, Color, Color>? {
    val p = LocalGtjColors.current
    return when (source) {
        "paste" -> Triple("粘贴记录", p.accentSoft, p.accent)
        "transcription" -> Triple("截图转述", p.borderSoft, p.warn)
        "chat" -> Triple("口述", p.borderSoft, p.muted)
        else -> null
    }
}

/** 事实编辑弹窗（新增/编辑共用；空白忽略） */
@Composable
private fun FactEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val p = LocalGtjColors.current
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("编辑事实", style = GtjType.Title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("已记住的关于咨询对象的信息", style = GtjType.BodySm, color = p.meta) },
                textStyle = GtjType.BodySm,
                minLines = 2,
                maxLines = 4,
                shape = GtjShape.md,
                colors = editFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.trim().isNotEmpty()) {
                Text("保存", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = GtjType.Label, color = p.muted)
            }
        },
    )
}

/** O2: 冲突裁决弹窗——用户选择保留哪条；未选中的一条将被删除（落库后冲突自然消失） */
@Composable
private fun ConflictResolutionDialog(
    pair: ConflictPairUi,
    onDismiss: () -> Unit,
    onKeepA: () -> Unit,
    onKeepB: () -> Unit,
) {
    val p = LocalGtjColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GtjShape.lg,
        containerColor = p.surfaceElevated,
        titleContentColor = p.fg,
        textContentColor = p.fgSecondary,
        title = { Text("记忆冲突裁决", style = GtjType.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("以下两条事实疑似矛盾，请选择保留哪一条（另一条将被删除）：", style = GtjType.BodySm, color = p.fgSecondary)
                Surface(
                    shape = GtjShape.sm,
                    color = p.dangerSoft,
                    border = BorderStroke(1.dp, p.danger),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("A：${pair.a.text}", style = GtjType.BodySm, color = p.fg)
                        Text("B：${pair.b.text}", style = GtjType.BodySm, color = p.fg)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepA) {
                Text("保留 A", style = GtjType.Label, color = p.accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onKeepB) {
                    Text("保留 B", style = GtjType.Label, color = p.accent)
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", style = GtjType.Label, color = p.muted)
                }
            }
        },
    )
}

/** 编辑页输入框配色（对齐 ProviderEditScreen） */
@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGtjColors.current.accent,
    unfocusedBorderColor = LocalGtjColors.current.border,
    focusedContainerColor = LocalGtjColors.current.surface,
    unfocusedContainerColor = LocalGtjColors.current.surface,
    cursorColor = LocalGtjColors.current.accent,
)
