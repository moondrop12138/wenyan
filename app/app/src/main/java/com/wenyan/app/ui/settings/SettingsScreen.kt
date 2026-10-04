package com.wenyan.app.ui.settings

import com.wenyan.app.BuildConfig
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wenyan.app.data.update.UpdateInfo
import com.wenyan.app.knowledge.KnowledgeRouting
import com.wenyan.app.ui.components.GtjIconButton
import com.wenyan.app.ui.components.ModelSheet
import com.wenyan.app.ui.components.SliderField
import com.wenyan.app.ui.components.Tag
import com.wenyan.app.ui.components.TagKind
import com.wenyan.app.ui.components.ThickDivider
import com.wenyan.app.ui.components.glass.FluidBackground
import com.wenyan.app.ui.components.glass.GlassBlurCapabilityProbe
import com.wenyan.app.ui.components.glass.GlassFill
import com.wenyan.app.ui.components.glass.GlassRenderMode
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.components.glass.GtjWindowTheme
import com.wenyan.app.ui.components.glass.LocalGlassBackdrop
import com.wenyan.app.ui.components.glass.glassBackdropBackground
import com.wenyan.app.ui.components.glass.glassBackdropContent
import com.wenyan.app.ui.components.glass.glassProbeStatusText
import com.wenyan.app.ui.components.glass.glassRenderMode
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.components.glass.rememberGlassBackdrop
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MAX
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_MIN
import com.wenyan.app.ui.theme.FLUID_HUE_MAX
import com.wenyan.app.ui.theme.FLUID_HUE_MIN
import com.wenyan.app.ui.theme.GLASS_FROST_MAX
import com.wenyan.app.ui.theme.GLASS_FROST_MIN
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors

/** v1.9.4 三改 色相滑条步进（5° = 73 个落点，宽度足够时手感连续，且避免 1° 级的无意义抖动） */
private const val FLUID_HUE_STEP = 5

/** v1.9.4 三改 亮度滑条步进（1% = 101 个落点，veil alpha 随之为 0.02 级，肉眼平滑） */
private const val BG_BRIGHTNESS_STEP = 1

/** v1.9.4 玻璃可调 磨砂度滑条步进（1% = 101 个落点，与 web 滑条 step 1 一致，app.js:1070） */
private const val GLASS_FROST_STEP = 1

private enum class PickerTarget { MAIN, VISION }

/**
 * 设置页（/settings，SPEC §7 页面3）：模型服务 / 记忆 / 外观 / 隐私与安全 分组。
 * v1.7.3：档案行编辑 → 跳 MemoryEdit 页；新增「导出诊断日志」「检查更新」行。
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onEditProvider: (Long) -> Unit,
    onEditTarget: (Long) -> Unit,
) {
    val vm: SettingsViewModel = rememberViewModel("SettingsViewModel") {
        SettingsViewModel(container.settingsRepository)
    }
    val providers by vm.providers.collectAsState()
    val providersLoaded by vm.providersLoaded.collectAsState()
    val targetsLoaded by vm.targetsLoaded.collectAsState()
    val pendingCrash by vm.pendingCrash.collectAsState()
    val showCrashDialog by vm.showCrashDialog.collectAsState()
    val downloadProgress by vm.downloadProgress.collectAsState()
    val models by vm.models.collectAsState()
    val currentId by vm.currentModelId.collectAsState()
    val visionId by vm.visionModelId.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val showPrivacy by vm.showPrivacyDialog.collectAsState()
    val showWipe by vm.showWipeDialog.collectAsState()
    val showImport by vm.showImportDialog.collectAsState()
    val showUsage by vm.showUsageDialog.collectAsState()
    val usage by vm.usage.collectAsState()
    val route by vm.routeDiagnostics.collectAsState()
    val targets by vm.targets.collectAsState()
    val activeTargetId by vm.activeTargetId.collectAsState()
    val memoryAutoEnabled by vm.memoryAutoEnabled.collectAsState()
    // 知识路由模式（"llm" 默认 | "offline" 显式关闭；开关行 checked = mode == llm）
    val knowledgeRouting by vm.knowledgeRouting.collectAsState()
    // v1.9.4 流光背景开关状态
    val fluidBackground by vm.fluidBackgroundEnabled.collectAsState()
    // v1.9.4 三改 流光可调：色相 / 背景亮度（滑条当前值 = 落盘值的实时回流）
    val fluidHue by vm.fluidHue.collectAsState()
    val bgBrightness by vm.bgBrightness.collectAsState()
    // v1.9.4 玻璃可调：磨砂度（%）（滑条当前值 = 落盘值的实时回流）；模糊半径已锁死 100dp
    val glassFrost by vm.glassFrost.collectAsState()
    val toastMessage by vm.toastMessage.collectAsState()
    val showNameDialog by vm.showNameDialog.collectAsState()
    val deleteTarget by vm.deleteTarget.collectAsState()
    val p = LocalGtjColors.current
    val context = LocalContext.current
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    // v1.9.4 记忆导入待确认文件（选中后弹合并导入确认弹窗，确认才入库；null = 无待导入）
    var pendingMemoryImportUri by remember { mutableStateOf<Uri?>(null) }

    // O1: 备份文件选择器（JSON / 文本 / 任意二进制，系统按 MIME 过滤）
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.confirmImport(it) }
    }

    // v1.9.4 记忆导出保存位置选择器（CreateDocument，固定 JSON 类型；uri 为 null = 用户取消，静默）
    val exportMemoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { vm.exportMemoryTo(it) }
    }

    // v1.9.4 记忆导入文件选择器（MIME 过滤对齐备份导入；选中后不直接入库，先弹合并确认弹窗）
    val memoryImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingMemoryImportUri = uri
    }

    // v1.7.2 切换激活档案 Toast（一次性事件，消费后清空）
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    // v1.8.1 B4：移除 glowState 光斑共享——dead path 且每帧重组开销大

    // v1.9.4 卡片透光磨砂：record 本页「流光背景层」并 provide——本页全部玻璃卡片
    // （设置行/开关行/顶栏胶囊等 GlassSurface/liquidGlass）内部自动消费，垫卡片级
    // 真实高斯模糊（API 31+；未接入或低版本自动回退现状半透明，无设置开关）
    val glassBackdrop = rememberGlassBackdrop()

    // v1.7.1：根 Box 加主题背景（防系统深色下 windowBackground 透出导致浅色模式变暗底）
    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
    Box(Modifier.fillMaxSize().background(p.bg)) {
        // v1.9.4 流光背景（AGSL 流体 shader；LocalFluidBackground=false 时不绘制露出底色）。
        // v1.9.4 卡片透光磨砂：背景独立 record 进背景层，卡片磨砂取样后模糊区域背后
        // 始终有流光可磨（此前背景画在 Scaffold 之外，直接取样会漏掉它）
        Box(Modifier.fillMaxSize().glassBackdropBackground(glassBackdrop)) {
            FluidBackground()
        }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // v1.8.0 液态玻璃 2.0：顶栏悬浮胶囊（v1.8.1 B4 移除光斑 dead path）
            // v1.9.4 Mica：与聊天顶栏同一配方——r20 + --wy-card-* 渐变填充 + 栏级
            // 0 8px 28px 投影（web `[data-wy-glass=on] .main .topbar`，styles.css:533-536）；
            // 近影关掉（web 对 Mica 栏的 box-shadow 是整条覆盖）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .liquidGlass(
                            shape = GtjShape.topBar,
                            fill = GlassFill.Card,
                            shadowColor = p.glassShadowTopBar,
                            shadowFeather = 14.dp,
                            shadowLift = 8.dp,
                            shadowNearColor = Color.Transparent,
                        )
                        .clip(GtjShape.topBar),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    ) {
                        GtjIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", onClick = onBack)
                        Text("设置", style = GtjType.Title, color = p.fg)
                    }
                } // 玻璃胶囊
            }
        },
    ) { padding ->
        LazyColumn(
            // v1.9.4 卡片透光磨砂：滚动内容 record 进内容层（供未来悬浮面全量磨砂取样；
            // 卡片级磨砂只取背景层，滚动内容无需参与，挂此保持与 Chat 页同构）
            modifier = Modifier.padding(padding).fillMaxWidth().glassBackdropContent(glassBackdrop),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            item { SettingsSectionHeader("模型服务") }
            item {
                // v1.3.1 主模型条目去掉类型标签（"纯文本"/"支持视觉"），label 垂直居中与右侧模型名对齐
                SettingsRow(
                    label = "主模型",
                    value = models.firstOrNull { it.id == currentId }?.name ?: "未选择",
                    // v1.7.1-4：与视觉模型行等高（补 caption 成两行结构）
                    caption = "对话与截图分析使用的默认模型",
                    onClick = { pickerTarget = PickerTarget.MAIN },
                )
            }
            item {
                SettingsRow(
                    label = "视觉模型",
                    value = models.firstOrNull { it.id == visionId }?.name ?: "未选择",
                    caption = "用于非多模态主模型的截图分析",
                    onClick = { pickerTarget = PickerTarget.VISION },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("提供商", style = GtjType.Label, color = p.muted, modifier = Modifier.weight(1f))
                    GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加提供商", onClick = { onEditProvider(-1L) }, tint = p.accent, iconSize = 20.dp)
                }
            }
            if (!providersLoaded) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("加载中…", style = GtjType.BodySm, color = p.muted)
                    }
                }
            } else if (providers.isEmpty()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有模型服务，添加一个开始使用", style = GtjType.BodySm, color = p.muted)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "添加模型服务",
                            style = GtjType.Label,
                            color = p.accent,
                            modifier = Modifier.clickable { onEditProvider(-1L) },
                        )
                    }
                }
            } else {
                // key 加 "provider_" 前缀：与下方「记忆」分组 items 的 key 空间隔离。
                // provider/target 是两张独立自增表，id 均从 1 开始——同一 LazyColumn 内若都用裸 id 作 key，
                // provider id=1 与 target id=1 会撞 key，滚动到记忆分组时 Compose 抛
                // "Key was already used"（LayoutNodeSubcompositionsState）→ 100% 闪退。
                items(providers, key = { "provider_${it.id}" }) { provider ->
                    ProviderRow(provider = provider, onClick = { onEditProvider(provider.id) })
                }
            }
            // ===== v1.7.2 「记忆」分组（模型服务之后、外观之前） =====
            item { ThickDivider() }
            item { SettingsSectionHeader("记忆") }
            if (!targetsLoaded) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("加载中…", style = GtjType.BodySm, color = p.muted)
                    }
                }
            } else if (targets.isEmpty()) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有记忆档案，添加一个开始使用", style = GtjType.BodySm, color = p.muted)
                    }
                }
            } else {
                // key 加 "target_" 前缀：与「模型服务」分组 providers items 的 key 空间隔离（防撞 key 闪退，见上）
                items(targets, key = { "target_${it.id}" }) { target ->
                    MemoryTargetRow(
                        target = target,
                        onClick = { vm.setActiveTarget(target) },
                        // v1.7.3 编辑图标 → 跳档案详情页（替代改名弹窗）
                        onEdit = { onEditTarget(target.id) },
                        onDelete = { vm.requestDeleteTarget(target) },
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("添加记忆", style = GtjType.Label, color = p.accent, modifier = Modifier.weight(1f))
                    GtjIconButton(icon = Icons.Outlined.Add, contentDescription = "添加记忆", onClick = vm::requestCreateTarget, tint = p.accent, iconSize = 20.dp)
                }
            }
            // 新用户激活链路：问卷画像行（"添加记忆"之后、自动记忆开关之前；门控只看 activeTargetId）
            item {
                // 收敛到普通局部 val：delegated property 不能 smart cast，直接传参会报 Long?→Long 类型错
                val activeId = activeTargetId
                val activeName = targets.firstOrNull { it.id == activeId }?.name
                GlassSurface(
                    onClick = if (activeId != null) {
                        { onEditTarget(activeId) }
                    } else {
                        {}
                    },
                    enabled = activeId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (activeId != null && activeName != null) "问卷画像 · $activeName" else "问卷画像",
                                style = GtjType.Body,
                                color = if (activeId != null) p.fg else p.muted,
                            )
                            Text(
                                if (activeId != null) "查看问卷画像" else "跳过问卷或未建档时不可用",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = p.meta,
                        )
                    }
                }
            }
            item {
                // v1.7.2 自动记忆开关行（玻璃行 + Switch，默认开）
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动记忆", style = GtjType.Body, color = p.fg)
                            Text("回复后自动提炼新事实写入当前档案", style = GtjType.Caption, color = p.muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = memoryAutoEnabled,
                            onCheckedChange = vm::setMemoryAutoEnabled,
                            // 无障碍：Switch 显式关联 label
                            modifier = Modifier.semantics { contentDescription = "自动记忆" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = p.accentOn,
                                checkedTrackColor = p.accent,
                                uncheckedTrackColor = p.borderSoft,
                            ),
                        )
                    }
                }
            }
            item {
                // 知识路由开关行（照「自动记忆」行模板：玻璃行 + Switch，默认开 = llm；
                // 关闭（offline）后聊天链路完全不发起 LLM 路由请求，仅本地关键词路由）
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("智能知识路由", style = GtjType.Body, color = p.fg)
                            Text("开启后由对话模型挑选知识文档；关闭仅本地关键词路由", style = GtjType.Caption, color = p.muted)
                            // 路由成本透明：开关行成本小字（200token/10s超时/失败回退/关闭零请求）
                            Text(
                                "开启后每次至多 200 token、10 秒超时；失败自动回退本地路由；关闭后零路由请求",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = knowledgeRouting == KnowledgeRouting.LLM,
                            onCheckedChange = { checked ->
                                vm.setKnowledgeRouting(if (checked) KnowledgeRouting.LLM else KnowledgeRouting.OFFLINE)
                            },
                            // 无障碍：Switch 显式关联 label
                            modifier = Modifier.semantics { contentDescription = "智能知识路由" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = p.accentOn,
                                checkedTrackColor = p.accent,
                                uncheckedTrackColor = p.borderSoft,
                            ),
                        )
                    }
                }
            }
            item {
                // v1.9.0 撤销最近一次自动记忆（写日志在提炼链路内自动记录，仅删除对应事实）
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { vm.undoLastMemoryWrite() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("撤销最近一次自动记忆", style = GtjType.Body, color = p.fg)
                            Text("删除最近一轮自动提炼写入的事实", style = GtjType.Caption, color = p.muted)
                        }
                        Icon(
                            imageVector = Icons.Outlined.Undo,
                            contentDescription = "撤销最近一次自动记忆",
                            tint = p.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    "选择本次咨询对象的记忆，不同对象互不干扰",
                    style = GtjType.Caption,
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { ThickDivider() }
            item { SettingsSectionHeader("外观") }
            item {
                ThemePicker(current = themeMode, onSelect = vm::setTheme, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                // v1.9.4 流光背景开关行（照「自动记忆」行模板：玻璃行 + Switch，默认开；
                // Android 13 以下组件层自动降级为简化光斑，开关语义不变）
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("流光背景", style = GtjType.Body, color = p.fg)
                            Text("流动渐变背景；Android 13 以下显示简化光斑", style = GtjType.Caption, color = p.muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = fluidBackground,
                            onCheckedChange = vm::setFluidBackgroundEnabled,
                            // 无障碍：Switch 显式关联 label
                            modifier = Modifier.semantics { contentDescription = "流光背景" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = p.accentOn,
                                checkedTrackColor = p.accent,
                                uncheckedTrackColor = p.borderSoft,
                            ),
                        )
                    }
                }
            }
            item {
                // v1.9.4 三改 流光可调（流光开关正下方的玻璃卡，两个滑条）：
                // 色相 0-360°（W3C hue-rotate 矩阵旋转 fluidA/B/C，= 桌面 filter: hue-rotate 同算子；
                // 保 luma，滑条全量程卡片正文对比度不跌破 AA）、背景亮度 0-100（50 = 中点 = 不叠 veil，
                // 与桌面 app.js --wy-brightness-white/black 同语义）。范围/默认值唯一来源
                // design-tokens.json component.fluidAppearance（Compose 侧常量在 ui/theme/Color.kt）。
                // 拖拽中滑条本地零延迟，落盘按 SettingsViewModel 的 60ms 合并写，背景随回流实时变化
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        SliderField(
                            value = fluidHue,
                            range = FLUID_HUE_MIN..FLUID_HUE_MAX,
                            label = "流光色相（°）",
                            onValueChange = vm::setFluidHue,
                            step = FLUID_HUE_STEP,
                        )
                        SliderField(
                            value = bgBrightness,
                            range = BG_BRIGHTNESS_MIN..BG_BRIGHTNESS_MAX,
                            label = "背景亮度（%）",
                            onValueChange = vm::setBgBrightness,
                            step = BG_BRIGHTNESS_STEP,
                        )
                    }
                }
            }
            item {
                // v1.9.4 收尾：玻璃卡只剩磨砂度滑条（0-100 按 web 公式运行时派生玻璃填充 alpha，
                // = web --wy-glass-frost，app.js:1070）；模糊半径锁死 GLASS_BLUR_DEFAULT 100dp
                //（设置页滑条已移除，= web --wy-glass-blur 的安卓对应值）。范围/默认值唯一
                // 来源 design-tokens.json component.glassAppearance（Compose 侧常量在
                // ui/theme/Color.kt；磨砂默认 60 = 磨砂档）。拖拽中滑条本地零延迟，
                // 落盘按 SettingsViewModel 的 60ms 合并写，玻璃随回流实时变化
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("玻璃", style = GtjType.Body, color = p.fg)
                        // v1.9.4 探测降级为观测：状态行由 SDK 版本 + GlassBlurCapabilityProbe.state/failure
                        // 驱动——31+ 恒「真实高斯模糊：已启用」（探测只观测、不改行为，结论翻转经
                        // 快照态自动重组刷新文案）；<31 雾化兜底
                        if (glassRenderMode(Build.VERSION.SDK_INT) == GlassRenderMode.REAL_BLUR) {
                            Text(
                                "真实高斯模糊：已启用 · 探测" +
                                    glassProbeStatusText(GlassBlurCapabilityProbe.state, GlassBlurCapabilityProbe.failure),
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        } else {
                            Text(
                                "雾化兜底（系统低于 Android 12）",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        }
                        SliderField(
                            value = glassFrost,
                            range = GLASS_FROST_MIN..GLASS_FROST_MAX,
                            label = "磨砂度（%）",
                            onValueChange = vm::setGlassFrost,
                            step = GLASS_FROST_STEP,
                        )
                    }
                }
            }
            item { ThickDivider() }
            item { SettingsSectionHeader("隐私与安全") }
            pendingCrash?.let { crash ->
                item {
                    // CrashCare 常驻内联卡：禁包 GtjWindowTheme（内联卡非独立窗口）
                    CrashCareCard(
                        timeText = crash.timeText,
                        firstLine = crash.firstLine,
                        onView = vm::requestCrashDialog,
                        onExport = {
                            vm.exportCrashLog { uri ->
                                if (uri == null) {
                                    Toast.makeText(context, "暂无崩溃日志可导出", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, "导出诊断日志")) }
                                }
                            }
                        },
                        onClear = vm::clearCrashLog,
                    )
                }
            }
            item {
                SettingsRow(
                    label = "隐私声明",
                    value = "数据将发送至你配置的第三方模型服务",
                    icon = Icons.Outlined.Info,
                    onClick = vm::requestPrivacy,
                )
            }
            item {
                // v1.7.3 T3 导出诊断日志（ShareIntent + FileProvider 发送 last_crash.txt，无用户内容）
                SettingsRow(
                    label = "导出诊断日志",
                    value = "崩溃日志本地文件",
                    icon = null,
                    onClick = {
                        vm.exportCrashLog { uri ->
                            if (uri == null) {
                                Toast.makeText(context, "暂无崩溃日志可导出", Toast.LENGTH_SHORT).show()
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "导出诊断日志")) }
                            }
                        }
                    },
                )
            }
            item {
                // O1: 从备份恢复（选择温言导出的 JSON 备份文件，清空重建；二次确认见导入弹窗）
                SettingsRow(
                    label = "从备份恢复",
                    value = if (vm.importing) "导入中…" else "选择备份文件",
                    icon = null,
                    onClick = vm::requestImport,
                )
            }
            item {
                // v1.9.4 记忆导出（换机迁移）：CreateDocument 选保存位置；文件名在点击回调里生成（不随重组重建）
                SettingsRow(
                    label = "导出记忆",
                    value = if (vm.exportingMemory) "导出中…" else "导出 JSON 文件",
                    icon = null,
                    onClick = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        exportMemoryPicker.launch("wenyan-memory-$stamp.json")
                    },
                )
            }
            item {
                // v1.9.4 记忆合并导入（换机迁移）：选文件 → 确认弹窗（合并语义）→ 导入
                SettingsRow(
                    label = "导入记忆",
                    value = if (vm.importingMemory) "导入中…" else "合并导入 JSON 文件",
                    icon = null,
                    onClick = {
                        memoryImportPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    },
                )
            }
            item {
                // O6: 用量/诊断面板（TTFT/token/失败分类，本地存储，不含消息原文）
                SettingsRow(
                    label = "用量 / 诊断",
                    value = "查看",
                    icon = null,
                    onClick = vm::requestUsage,
                )
            }
            item {
                // v1.8.0：清除全部档案也统一为玻璃卡片样式（与提供商/设置行一致）
                GlassSurface(
                    onClick = vm::requestWipe,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = GtjShape.md,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.danger)
                        Spacer(Modifier.width(12.dp))
                        Text("清除全部档案", style = GtjType.Body, color = p.danger, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.danger)
                    }
                }
            }
            item {
                Text(
                    // 版本号读 BuildConfig，随 build.gradle.kts 单一来源，不再硬编码
                    "温言 v${BuildConfig.VERSION_NAME}",
                    style = GtjType.Caption,
                    // 对比度：版本号升到 muted 4.8:1
                    color = p.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item {
                // v1.7.3 T4 手动检查更新（GitHub Releases 直连；失败静默/Toast 由 VM 处理）
                SettingsRow(
                    label = "检查更新",
                    value = if (vm.checkingUpdate) "检查中…" else "v${BuildConfig.VERSION_NAME}",
                    icon = null,
                    onClick = vm::checkUpdate,
                )
            }
        }
    }
    } // Box（FluidBackground + Scaffold）
    } // CompositionLocalProvider（LocalGlassBackdrop）

    pickerTarget?.let { target ->
        val list = if (target == PickerTarget.VISION) models.filter { it.supportsVision } else models
        ModelSheet(
            models = list,
            currentModelId = if (target == PickerTarget.MAIN) currentId else visionId,
            onSelect = { id ->
                if (target == PickerTarget.MAIN) vm.setMainModel(id) else vm.setVisionModel(id)
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
            onManageProviders = { pickerTarget = null },
        )
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = vm::dismissPrivacy, onAccept = vm::acceptPrivacy)
    }
    if (showWipe) {
        WipeDialog(onDismiss = vm::dismissWipe, onConfirm = { vm.confirmWipe(onBack) })
    }
    if (showImport) {
        ImportBackupDialog(
            importing = vm.importing,
            onDismiss = vm::dismissImport,
            onConfirm = {
                vm.dismissImport()
                importPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            },
        )
    }
    // v1.9.4 记忆合并导入确认弹窗（选中文件后弹出；取消 = 丢弃待导入 uri，静默返回）
    pendingMemoryImportUri?.let { uri ->
        ImportMemoryDialog(
            importing = vm.importingMemory,
            onDismiss = { pendingMemoryImportUri = null },
            onConfirm = {
                pendingMemoryImportUri = null
                vm.confirmMemoryImport(uri)
            },
        )
    }
    if (showUsage) {
        UsageMetricsDialog(usage = usage, route = route, onDismiss = vm::dismissUsage)
    }
    // ===== v1.7.2 记忆弹窗 =====
    if (showNameDialog) {
        MemoryNameDialog(onDismiss = vm::dismissCreateTarget, onConfirm = vm::createTarget)
    }
    // v1.7.3 编辑弹窗已移除：档案行「编辑」→ 跳 MemoryEdit 页（MemoryEditDialog 废弃）
    deleteTarget?.let { t ->
        MemoryDeleteDialog(
            targetName = t.name,
            onDismiss = vm::dismissDeleteTarget,
            onConfirm = { vm.deleteTarget(t.id) },
        )
    }
    // v1.7.3 T4 更新确认弹窗
    vm.updateAvailable?.let { info ->
        UpdateDialog(
            info = info,
            downloading = vm.downloading,
            onDownload = { vm.downloadAndInstall(info) },
            onDismiss = vm::dismissUpdateDialog,
            progress = downloadProgress,
        )
    }
    if (showCrashDialog) {
        pendingCrash?.let { crash ->
            CrashDialog(
                timeText = crash.timeText,
                fullText = crash.fullText,
                onExport = {
                    vm.exportCrashLog { uri ->
                        if (uri == null) {
                            Toast.makeText(context, "暂无崩溃日志可导出", Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "导出诊断日志")) }
                        }
                    }
                },
                onClear = vm::clearCrashLog,
                onDismiss = vm::dismissCrashDialog,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    val p = LocalGtjColors.current
    Text(
        text,
        style = GtjType.Label,
        color = p.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    caption: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalGtjColors.current
    // v1.7.0：设置行 = 玻璃材质
    GlassSurface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (icon != null) {
                // v1.5：图标带 8% 底色圆角容器（设计稿 WY-06，温暖质感细节）
                Surface(
                    shape = com.wenyan.app.ui.theme.GtjShape.sm,
                    color = p.accent.copy(alpha = 0.08f),
                    modifier = Modifier.size(36.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = p.accent)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = GtjType.Body, color = p.fg)
                if (caption != null) {
                    // 对比度：说明 caption 升到 muted（surface 底 4.5:1 达标）
                    Text(caption, style = GtjType.Caption, color = p.muted)
                }
            }
            // 对比度：值文字在 surface(#F7F8FA) 底上 muted 仅 4.48:1，升到 fgSecondary 9.7:1
            Text(value, style = GtjType.BodySm, color = p.fgSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.meta)
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderInfo,
    onClick: () -> Unit,
) {
    val p = LocalGtjColors.current
    // v1.7.0：提供商行 = 玻璃卡片
    GlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            // v1.6.3 连接状态红绿灯（最左）：ok=绿灯，未测/失败=红灯（保存提供商后自动测试）
            val connected = provider.connectionStatus == "ok"
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (connected) p.success else p.danger, CircleShape)
                    .semantics { contentDescription = if (connected) "${provider.name} 已连接" else "${provider.name} 未连接" },
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = GtjType.Body, color = p.fg)
                // 对比度：baseUrl 升到 muted 4.8:1
                Text(provider.baseUrl, style = GtjType.Caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val status = when {
                provider.isPreset -> "预设"
                provider.apiKeyConfigured -> "已配置"
                else -> "未配置"
            }
            Tag(text = status, kind = TagKind.NEUTRAL)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = p.meta)
        }
    }
}

/**
 * v1.7.2 记忆档案行（玻璃行）：
 * 左侧激活标识 = accent 实心对勾 / 未激活空心圆；中部名称 + caption；右侧编辑/删除 20dp 图标按钮。
 * 点行主体 = 切换激活档案（Toast 在 VM 内触发）。
 */
@Composable
private fun MemoryTargetRow(
    target: TargetUi,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalGtjColors.current
    GlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (target.isActive) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "使用中",
                    modifier = Modifier.size(20.dp),
                    tint = p.accent,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, p.muted, CircleShape),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(target.name, style = GtjType.Body, color = p.fg)
                // caption（v1.7.3-fix）：激活 =「使用中」/「使用中 · 已记住 N 条」（N=事实条数，替代已废弃的 note.length）；未激活 =「未使用」
                val caption = when {
                    target.isActive && target.factCount > 0 -> "使用中 · 已记住 ${target.factCount} 条"
                    target.isActive -> "使用中"
                    else -> "未使用"
                }
                Text(caption, style = GtjType.Caption, color = p.muted)
            }
            GtjIconButton(icon = Icons.Outlined.Edit, contentDescription = "编辑记忆", onClick = onEdit, iconSize = 20.dp)
            GtjIconButton(icon = Icons.Outlined.Delete, contentDescription = "删除记忆", onClick = onDelete, tint = p.danger, iconSize = 20.dp)
        }
    }
}

/** v1.7.3 T2 @Preview：档案行（激活态，含事实数 caption） */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
@Composable
private fun MemoryTargetRowPreview() {
    com.wenyan.app.ui.theme.GtjTheme {
        MemoryTargetRow(
            target = TargetUi(id = 1L, name = "小A", note = "", createdAt = 0L, isActive = true, factCount = 3),
            onClick = {},
            onEdit = {},
            onDelete = {},
        )
    }
}

/**
 * CrashCare 常驻内联卡（隐私分组；禁包 GtjWindowTheme——内联卡非独立窗口）。
 * pendingCrash 非空渲染：timeText + firstLine + 查看/导出/清除。
 */
@Composable
private fun CrashCareCard(
    timeText: String,
    firstLine: String,
    onView: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    val p = LocalGtjColors.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = GtjShape.md,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("上次崩溃", style = GtjType.Body, color = p.fg)
            if (timeText.isNotBlank()) {
                Text(timeText, style = GtjType.Caption, color = p.muted)
            }
            Text(
                firstLine.ifBlank { "崩溃日志可用" },
                style = GtjType.BodySm,
                color = p.fgSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "主要含崩溃堆栈与事件名、可能含少量系统错误信息，分享前请确认",
                style = GtjType.Caption,
                color = p.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onView) {
                    Text("查看", style = GtjType.Label, color = p.accent)
                }
                TextButton(onClick = onExport) {
                    Text("导出", style = GtjType.Label, color = p.accent)
                }
                TextButton(onClick = onClear) {
                    Text("清除", style = GtjType.Label, color = p.danger)
                }
            }
        }
    }
}

/** 崩溃详情弹窗（全文可滚动 + 导出复用既有 ShareIntent + 清除 + 关闭；仅弹窗包 GtjWindowTheme） */
@Composable
private fun CrashDialog(
    timeText: String,
    fullText: String,
    onExport: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    GtjWindowTheme {
        val p = LocalGtjColors.current
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = com.wenyan.app.ui.theme.GtjShape.lg,
            containerColor = p.surfaceElevated,
            titleContentColor = p.fg,
            textContentColor = p.fgSecondary,
            title = {
                Text(
                    if (timeText.isNotBlank()) "崩溃日志 · $timeText" else "崩溃日志",
                    style = GtjType.Title,
                )
            },
            text = {
                Column {
                    Text(
                        "主要含崩溃堆栈与事件名、可能含少量系统错误信息，分享前请确认",
                        style = GtjType.Caption,
                        color = p.muted,
                    )
                    Text(
                        fullText,
                        style = GtjType.BodySm,
                        color = p.fgSecondary,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onExport) {
                    Text("导出", style = GtjType.Label, color = p.accent)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onClear) {
                        Text("清除", style = GtjType.Label, color = p.danger)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭", style = GtjType.Label, color = p.muted)
                    }
                }
            },
        )
    }
}

/**
 * v1.7.3 T4 更新确认弹窗：版本说明 + 「去下载」；下载中禁用按钮。
 * 失败静默/Toast 由 VM 处理（不阻塞主流程）。
 */
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    progress: Float? = null,
) {
    // v1.9.4 独立窗口色相跟随：AlertDialog 是独立 Android 窗口，主窗口全局 hue-rotate 层罩
    // 不到，包内取色/M3 槽位随全局色相旋转（hue=0 原样透传，观感与不包裹逐位一致）
    GtjWindowTheme {
        val p = LocalGtjColors.current
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = com.wenyan.app.ui.theme.GtjShape.lg,
            containerColor = p.surfaceElevated,
            titleContentColor = p.fg,
            textContentColor = p.fgSecondary,
            title = { Text("发现新版本 v${info.versionName}", style = GtjType.Title) },
            text = {
                Column {
                    Text(
                        info.notes.ifBlank { "修复与体验优化，建议升级。" },
                        style = GtjType.BodySm,
                        color = p.fgSecondary,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        if (progress == null) {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = p.accent,
                                trackColor = p.borderSoft,
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = p.accent,
                                trackColor = p.borderSoft,
                            )
                            Text(
                                "已下载 ${(progress * 100).toInt()}%",
                                style = GtjType.Caption,
                                color = p.muted,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDownload, enabled = !downloading) {
                    Text(if (downloading) "下载中…" else "去下载", style = GtjType.Label, color = p.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, enabled = !downloading) {
                    Text("取消", style = GtjType.Label, color = p.muted)
                }
            },
        )
    }
}
