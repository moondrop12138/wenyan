package com.wenyan.app.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import com.wenyan.app.ui.components.glass.GlassFill
import com.wenyan.app.ui.components.glass.hueRotated
import com.wenyan.app.ui.components.glass.hueRotateColorMatrix
import com.wenyan.app.ui.components.glass.liquidGlass
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.GtjType
import com.wenyan.app.ui.theme.LocalFluidHue
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
 * v1.9.4 对齐 web `.sheet.glass-strong.edge.refract`——liquidGlass 玻璃卡片容器
 * （Strong 填充 frost+0.17 + glassBorder 发丝描边 + 顶边内高光 + --g-shadow 双影）+
 * 顶圆角 26 + 窗口级 4dp 背景模糊（API 31+，低版本降级不透明 elevated 面）+ muted 拖拽条；
 * **色相跟随**：弹层是独立窗口，主窗口全局色相层罩不到——本组件内容自套同一 hue-rotate
 * 矩阵（LocalFluidHue 随 composition 传播进 Dialog，填充挪进内容层自绘随层一起转），
 * 色相≠0 时弹层与主界面同步变色（web .sheet 是 body 子节点天然被转，此处对齐该语义）；
 * 头部 = 标题 15sp/500 + 副文案 11sp muted（web .sheet-head）；
 * 模型行 = web `.mrow` 无卡片扁平行（r16 透明底，选中 chip 底 + 1px l2b 描边 + 20dp
 * 勾选圈常驻；标题 13sp / 副文案 11sp muted「提供商 · 支持图片 · 默认」），切换即生效并收起。
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // v1.7.0：顶圆角 + 半透明玻璃容器（透出背后光斑）
        // v1.9.4 Mica：顶圆角 28 → 26（web `.sheet{border-radius:26px 26px 0 0}`，styles.css:319）
        // 容器色改 Transparent：填充挪进内容层自绘（见下方色相层）——ModalBottomSheet 是
        // 独立窗口，挂在主窗口内容根上的全局色相层罩不到本窗口，若填充仍由容器参数绘制，
        // 色相≠0 时弹层保持暖白而主界面整体变色（v1.9.4 曾作为已知取舍，现消掉）
        shape = GtjShape.sheetTop,
        containerColor = Color.Transparent,
        // 拖拽条 = web ::before（muted 35%）；色相跟随：值旋转（近中性色，偏移极小，保持一致语义）
        dragHandle = {
            Surface(
                color = hueRotated(p.muted, LocalFluidHue.current.toFloat()).copy(alpha = 0.35f),
                modifier = Modifier.size(width = 36.dp, height = 4.dp),
                shape = GtjShape.pill,
            ) {}
        },
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
        // v1.9.4 弹层跟随全局色相：对齐 web 语义——styles.css:493 的 hue-rotate 挂在 body 上，
        // .sheet 作为 body 子节点整层被转；安卓弹层是独立窗口，主窗口的全局层（MainActivity）
        // 罩不到这里，故在弹层自己的 composition 里套**同一个矩阵**（CompositionLocal 会随
        // composition 传播进 Dialog 内容，LocalFluidHue 在此可读）。写法与主窗口一致：
        // saveLayer(paint.colorFilter) 在图层合成时套矩阵，色相 = 0 直接透传零开销。
        // 填充由本层自绘（containerColor=Transparent）：填充/行/文字随层一起转，不会双重旋转。
        val hue = LocalFluidHue.current
        val huePaint = remember(hue) {
            if (hue == FLUID_HUE_DEFAULT) {
                null
            } else {
                android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(
                        hueRotateColorMatrix(hue.toFloat()).values,
                    )
                }
            }
        }
        // v1.9.4：容器 = 真玻璃卡片（liquidGlass 引擎全套：Strong 填充 + glassBorder 1px 发丝
        // 描边 + 顶边内高光线 + --g-shadow 双影，= web `.glass-strong.edge` 完整层叠，
        // styles.css:86-93）；backdrop=false——真实磨砂由窗口级 FLAG_BLUR_BEHIND 承担，
        // 不跨窗口取样页面层；API<31 无窗口模糊时降级不透明 elevated 面（可读性优先）
        val sheetSurface = if (canBlur) {
            Modifier.liquidGlass(
                shape = GtjShape.sheetTop,
                fill = GlassFill.Strong,
                backdrop = false,
                enablePressAnimation = false,
            )
        } else {
            Modifier.background(p.surfaceElevated, GtjShape.sheetTop)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    if (huePaint == null) {
                        drawContent()
                    } else {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            val save = native.saveLayer(null, huePaint)
                            drawContent()
                            native.restoreToCount(save)
                        }
                    }
                }
                .then(sheetSurface),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
            // v1.9.4 对齐 web .sheet-head（styles.css:326-328）：标题 15px/500 左 + 副文案 11px muted 右
            // （margin 14px 4px 10px）；关闭走 scrim 点按/下拉/返回键，不再放 X 按钮，「当前」行取消
            // （选中态由行内勾选圈表达，与 web 一致）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
            ) {
                Text(
                    "选择模型",
                    style = GtjType.Subtitle.copy(fontSize = 15.sp),
                    color = p.fg,
                    modifier = Modifier.weight(1f),
                )
                Text("点按切换，实时生效", style = GtjType.Caption.copy(fontSize = 11.sp), color = p.muted)
            }
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
                // v1.9.4 对齐 web .sheet-body（gap:3px）与 renderModelSheet('main')：不分组——
                // 提供商名并入行副文案，无分组头
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(visible, key = { "model_${it.id}" }) { model ->
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
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                GhostButton(text = "管理模型服务", onClick = onManageProviders, minHeight = 48.dp)
            }
            Spacer(Modifier.height(16.dp))
            }
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
    // v1.9.4 对齐 web .mrow（styles.css:330-332）：**无卡片背景的扁平行**（radius 16、透明底），
    // 选中态 = --chip 半透明 accent 底 + 1px --l2b 描边（glassFocusRing 同 token）；未选中不再
    // 包 GlassSurface 玻璃卡（旧观感：暖白玻璃盒与 web 扁平列表不一致，读作"黄卡片"）
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            },
        shape = GtjShape.lg,
        color = if (selected) p.glassChip else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, p.glassFocusRing) else null,
    ) {
        ModelRowContent(model, selected, p)
    }
}

@Composable
private fun ModelRowContent(
    model: ModelInfo,
    selected: Boolean,
    p: com.wenyan.app.ui.theme.GtjPalette,
) {
    // v1.9.4 逐项对齐 web .mrow 内部（styles.css:333-340 + app.js renderModelSheet）：
    // sic 40px r12 chip 底 accent 缩写 / t 13px fg / d 11px muted（提供商 · 支持图片 · 默认
    // 并入副文案，无徽章 chip）/ check 20px 圆常驻（未选中 muted 空圈，选中 accent 实底 + 对勾）；
    // 行内边距 11/12（web padding:11px 12px）
    val abbrev = model.name.take(2).uppercase()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Surface(
            shape = GtjShape.md,
            color = p.glassChip,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    abbrev,
                    style = GtjType.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = p.accent,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                model.name,
                style = GtjType.Body.copy(fontSize = 13.sp),
                color = p.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val desc = buildString {
                append(model.providerName)
                if (model.supportsVision) append(" · 支持图片")
                if (model.isDefault) append(" · 默认")
            }
            Text(
                desc,
                style = GtjType.Caption.copy(fontSize = 11.sp),
                color = p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        // 勾选圈常驻（web .check：20px 圆 + 1.5px muted 描边；.on 才 accent 实底 + 对勾）
        if (selected) {
            Surface(shape = CircleShape, color = p.accent, modifier = Modifier.size(20.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, contentDescription = "已选择", modifier = Modifier.size(12.dp), tint = p.accentOn)
                }
            }
        } else {
            Box(modifier = Modifier.size(20.dp).border(1.5.dp, p.muted, CircleShape))
        }
    }
}
