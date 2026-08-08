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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
private val RELATION_OPTIONS = listOf("暧昧中", "约会", "确定关系", "前任", "同事", "单恋", "其他")

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
                            if (vm.facts.isEmpty()) {
                                Text("还没有事实，回复后会自动提炼", style = GtjType.BodySm, color = p.meta)
                            } else {
                                vm.facts.forEach { fact ->
                                    MemoryFactRow(
                                        fact = fact,
                                        onEdit = { vm.openEditFact(fact) },
                                        onDelete = { vm.deleteFact(fact) },
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
                vm.timeline.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = item.time,
                            onValueChange = { vm.updateTimelineItem(index, it, item.event) },
                            modifier = Modifier.weight(0.35f),
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

/** 单条事实行：文本 + 编辑/删除（删除不二次确认） */
@Composable
private fun MemoryFactRow(
    fact: MemoryFactUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalGtjColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(fact.text, style = GtjType.BodySm, color = p.fg, modifier = Modifier.weight(1f))
        GtjIconButton(icon = Icons.Outlined.Edit, contentDescription = "编辑事实", onClick = onEdit, iconSize = 18.dp)
        GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除事实", onClick = onDelete, tint = p.danger, iconSize = 18.dp)
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

/** 编辑页输入框配色（对齐 ProviderEditScreen） */
@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalGtjColors.current.accent,
    unfocusedBorderColor = LocalGtjColors.current.border,
    focusedContainerColor = LocalGtjColors.current.surface,
    unfocusedContainerColor = LocalGtjColors.current.surface,
    cursorColor = LocalGtjColors.current.accent,
)
