package com.wenyan.app.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.wenyan.app.ui.components.glass.GlassSurface
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalGtjColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 弹层背后的窗口级模糊半径（dp）：= web 弹层 `backdrop-filter: blur(var(--wy-glass-blur))`
 * 的生效默认 4px（styles.css:515-518；--wy-glass-blur 默认 4，app.js:66/130）。
 * 与页内玻璃面同一换算：backdrop 引擎把同一个 web 值取作 4dp（GlassBackdropParams.blurRadius），
 * 窗口级这里也取 4dp，两端强度观感一致。
 */
private val SHEET_BLUR_RADIUS = 4.dp

/**
 * 找到本视图所在的对话框窗口（沿 parent 链上溯 [DialogWindowProvider]）。
 * M3 的 ModalBottomSheet 内容挂在 `ModalBottomSheetDialogLayout` 上，该类实现
 * DialogWindowProvider（javap material3 1.3.2 证实）→ 由它拿 sheet 自己的 Window；
 * 拿不到（实现变化/非对话框宿主）返回 null，调用方走降级路径。
 */
private fun View.findDialogWindow(): Window? {
    var v: View? = this
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent as? View
    }
    return null
}

/**
 * 给弹层窗口开窗口级高斯模糊（API 31+）。返回还原函数：ModalBottomSheet 的窗口实例在
 * 同一 composition 内复用，关闭弹层时须把 flag/半径还原，避免影响窗口后续状态。
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun enableBlurBehind(window: Window, radiusPx: Int): () -> Unit {
    val attrs = window.attributes
    val prevFlags = attrs.flags
    val prevRadius = attrs.blurBehindRadius
    attrs.flags = prevFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
    attrs.blurBehindRadius = radiusPx
    window.attributes = attrs
    return {
        val a = window.attributes
        a.flags = prevFlags
        a.blurBehindRadius = prevRadius
        window.attributes = a
    }
}

/**
 * 弹层背后真实高斯模糊（v1.9.4 Mica）：走窗口级 `FLAG_BLUR_BEHIND` + `attributes.blurBehindRadius`
 * （SurfaceFlinger 对窗口背后的内容做高斯模糊，对等于 web `.sheet` 的 `backdrop-filter:
 * blur(var(--wy-glass-blur)) saturate(170%)`，styles.css:515-518）。
 *
 * **必须在 ModalBottomSheet 的 content lambda 内调用**：`LocalView` 取的是「当前 composition
 * 的宿主视图」，只有弹层自己的 composition 才能沿 parent 链上溯到实现 DialogWindowProvider 的
 * `ModalBottomSheetDialogLayout`（见 ModelSheet 调用点注释）。
 *
 * 降级路径：① API < 31（无该 API 与常量，S 才引入）→ 不设任何 flag；② 拿不到窗口引用
 * （弹层实现变化/非对话框宿主）→ 同样不设；两种降级都维持现状：弹层自带 scrim 压暗
 * （compose 内绘的半透明 scrim，压暗足够承担可读性）。系统「关闭模糊」（开发者选项/无障碍）
 * 或低端机型忽略 blur behind 时也落在同一条 scrim 路径上，无功能影响。
 */
@Composable
private fun SheetWindowBlurBehind() {
    val view = LocalView.current
    val blurRadiusPx = with(LocalDensity.current) { SHEET_BLUR_RADIUS.toPx() }.roundToInt()
    DisposableEffect(view, blurRadiusPx) {
        val window = view.findDialogWindow()
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onDispose { }
        } else {
            val restore = enableBlurBehind(window, blurRadiusPx)
            onDispose { restore() }
        }
    }
}

/**
 * 模型选择底部弹层（design-pages 页面4，AC-10）：
 * v1.9.4 Mica：容器 = web `.sheet glass-strong edge refract`——frost 版 `--glass-strong` 填充 +
 * 顶圆角 26 + 窗口级 4dp 背景模糊（API 31+，低版本降级 scrim 压暗）+ dragHandle；
 * 提供商分组、能力徽标（chip 语言）、选中态双通道（chip 半透明 accent 底 + 1px --l2b 描边 +
 * 实心对勾），切换即生效并收起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    models: List<ModelInfo>,
    currentModelId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    onManageProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtjColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // v1.8.1 B3 修复：移除 decorView 全屏模糊——项目记忆明令「两套模糊机制不可并存（会叠加）」，
    // 抽屉模糊走 graphicsLayer，弹层再走 decorView.setRenderEffect 既叠加又有 onDispose 误清抽屉的风险。
    // v1.9.4 Mica：改为**窗口级**模糊（不清 decorView、不与页内 backdrop 层共用对象）——见下。
    // 容器色：web 弹层 = `.sheet glass-strong edge refract`（index.html:114）→ 填充取 `--glass-strong`
    // = frost + 0.17/+0.29（styles.css:484/488，Mica 下即 glassFillStrong .47/.59）；
    // 增强段给 .sheet 的 backdrop-filter 与其余 8 类玻璃面同款（styles.css:515-518）。
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val sheetColor = if (canBlur) p.glassFillStrong else p.surfaceElevated
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // v1.7.0：顶圆角 + 半透明玻璃容器（透出背后光斑）
        // v1.9.4 Mica：顶圆角 28 → 26（web `.sheet{border-radius:26px 26px 0 0}`，styles.css:319）
        shape = GtjShape.sheetTop,
        containerColor = sheetColor,
        dragHandle = { Surface(color = p.borderSoft, modifier = Modifier.size(width = 36.dp, height = 4.dp), shape = GtjShape.pill) {} },
    ) {
        // v1.9.4 Mica 弹层背后真实高斯模糊：必须挂在本 lambda 内（弹层自己的 composition）——
        // ModalBottomSheet 是**独立窗口**，内容由 ModalBottomSheetDialogLayout.setContent 承载
        // （javap material3 1.3.2：`ModalBottomSheetDialogLayout extends AbstractComposeView
        // implements DialogWindowProvider`，而 ModalBottomSheetDialogWrapper 是 ComponentDialog、
        // 不实现该接口）；`LocalView` 由 `ProvideAndroidCompositionLocals(AndroidComposeView…)`
        // 提供（javap -c AndroidCompositionLocals_androidKt:894-901），在本 lambda 里取到的才是
        // 弹层窗口内的视图、parent 上溯才够得着该 DialogWindowProvider。
        // 若写在 ModelSheet 函数体（宿主 Activity 的 composition）里，LocalView 是宿主的
        // AndroidComposeView，parent 链止于 Activity DecorView（其 parent 是 ViewRootImpl、
        // 非 View）→ 永远拿不到弹层窗口，FLAG_BLUR_BEHIND 静默不生效（曾是死路径）。
        SheetWindowBlurBehind()
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("选择模型", style = GtjType.Title, color = p.fg, modifier = Modifier.weight(1f))
                GtjIconButton(icon = Icons.Outlined.Close, contentDescription = "关闭", onClick = onDismiss, tint = p.muted)
            }
            val current = models.firstOrNull { it.id == currentModelId }
            if (current != null) {
                Text("当前：${current.name}", style = GtjType.Caption, color = p.muted)
            }
            Spacer(Modifier.height(8.dp))
            // v1.6.3 只展示模型管理里"可见"的模型（showInSheet 开关控制）
            val visible = models.filter { it.showInSheet }
            if (visible.isEmpty()) {
                EmptyState(
                    title = "没有可用模型",
                    actionText = "去设置添加",
                    onAction = onManageProviders,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visible.groupBy { it.providerName }.forEach { (providerName, list) ->
                        item(key = "header_$providerName") {
                            Text(providerName, style = GtjType.Label, color = p.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }
                        items(list, key = { "model_${it.id}" }) { model ->
                            ModelRow(
                                model = model,
                                selected = model.id == currentModelId,
                                onClick = {
                                    onSelect(model.id)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                GhostButton(text = "管理模型服务", onClick = onManageProviders, minHeight = 48.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalGtjColors.current
    // v1.9.4 Mica：模型行 = 现行玻璃语言（与 CrisisCard 等同一 GlassSurface：Frost 填充
    // glassFill + 外圈 glassBorder + 顶边内高光；旧版是 accentSoft 黄底 + accent 实边）。
    // 选中态对齐 web `.mrow.on`（styles.css:332）：`background:var(--chip)` 半透明 accent 底
    // + `border:1px solid var(--l2b)` 描边（--l2b 与输入栏聚焦环同一个 token = glassFocusRing，
    // web 侧 :focus-within 环与 .on 行描边共用该变量）+ 行尾实心对勾。
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .semantics {
            role = Role.RadioButton
            this.selected = selected
        }
    if (selected) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            shape = GtjShape.lg,
            color = p.glassChip,
            border = BorderStroke(1.dp, p.glassFocusRing),
        ) {
            ModelRowContent(model, selected, p)
        }
    } else {
        GlassSurface(
            onClick = onClick,
            modifier = rowModifier,
            shape = GtjShape.lg,
            enablePressAnimation = true,
        ) {
            ModelRowContent(model, selected, p)
        }
    }
}

@Composable
private fun ModelRowContent(
    model: ModelInfo,
    selected: Boolean,
    p: com.wenyan.app.ui.theme.GtjPalette,
) {
    // v1.5：图标缩写（前两位字母，如 DS / GPT），40dp r12 容器
    // v1.9.4 Mica：改为 web `.mrow .sic` 语言——chip 半透明 accent 底 + accent 字
    // （styles.css:333：`background:var(--chip);color:var(--accent);font-size:11px`），
    // 不再用不透明陶土棕/灰底
    val abbrev = model.name.take(2).uppercase()
    Row(
            verticalAlignment = Alignment.CenterVertically,
            // v1.7.1 二改：fillMaxSize 让 72dp 最小行高内内容垂直居中（此前内容贴顶）；
            // v1.9.4：左右内边距 14 → 12（web .mrow `padding:11px 12px`）
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // 模型图标容器：40dp r12 chip 底 + accent 字
            Surface(
                shape = GtjShape.md,
                color = p.glassChip,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        abbrev,
                        style = GtjType.Label.copy(fontSize = 11.sp),
                        color = p.accent,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = GtjType.Body,
                    color = p.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 描述行：提供商 + 能力徽标（v1.5 副标题）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.providerName, style = GtjType.Caption, color = p.muted, maxLines = 1)
                    if (model.supportsVision) {
                        Spacer(Modifier.width(6.dp))
                        // v1.9.4 Mica：徽章同 chip 语言（半透明 accent 底 + accent 字）
                        Tag(text = "视觉", kind = TagKind.CHIP, icon = Icons.Outlined.Image)
                    }
                }
            }
            if (model.isDefault) {
                Spacer(Modifier.width(6.dp))
                Tag(text = "默认", kind = TagKind.CHIP)
            }
            // v1.5：选中态——实心圆 + 对勾；v1.9.4 对齐 web `.check`（20px 圆 + accent 实底 + 白勾）
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = CircleShape, color = p.accent, modifier = Modifier.size(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Check, contentDescription = "已选择", modifier = Modifier.size(12.dp), tint = p.accentOn)
                    }
                }
            }
        }
}
