package com.wenyan.app.ui.components.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.rememberReducedMotion
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.LocalGtjColors

/**
 * 玻璃填充组（web 增强 Mica 的两组填充，styles.css cautions：两套填充色相不同，勿混用）。
 *
 * - [Frost]：frost 版 `--glass`（亮 rgb(253 249 242/0.300) / 暗 rgb(46 36 28/0.300) 直色）——
 *   web 默认组：侧栏/胶囊/用户气泡/弹层/toast 及全部基础 .glass 元素；
 * - [Card]：`--wy-card-light/dark` 纵向渐变（亮 白 frost×0.50→frost×0.35，暗 rgb(42,46,56)→
 *   rgb(22,25,34) 均 frost×0.50）——仅顶栏/输入栏两个悬浮栏（styles.css:534/538）。
 */
enum class GlassFill {
    /** frost 版 --glass 直色（web 默认组：侧栏/胶囊/用户气泡/toast） */
    Frost,

    /** frost 版 --glass-strong 直色（frost+0.17/.29：弹层 .sheet glass-strong 组，styles.css:86-91） */
    Strong,

    /** --wy-card-* 纵向渐变（仅顶栏/输入栏悬浮栏） */
    Card,
}

/**
 * 液态玻璃引擎 · v1.9.4 扁平化对齐 web 增强 Mica——只保留 web 有的五层，逐层对应：
 * 1. 渐变填充：[GlassFill] 两组（Frost/Card，styles.css --glass / --wy-card-*）；
 * 2. backdrop 模糊：[BackdropGlass.kt]（glassBackdropContent + [backdropLayer]/卡片透光磨砂，
 *    对应 web backdrop-filter: blur() saturate(170%)）；
 * 3. 1px 描边：外圈读 glassBorder（= web 1px solid var(--glass-border)，borderColor 可覆盖）；
 * 4. 顶边内高光：**只沿顶边一条**渐变细线（= web .edge::before），见下方「内凹改版」；
 * 5. 柔和投影：BlurMaskFilter 高斯模糊（web --g-shadow 双影 → 远影 + 近影，见 [liquidGlass] 参数）。
 *
 * v1.9.4 扁平化：删掉 web 没有的四层——流光边缘 RuntimeShader（LENS_EDGE_SHADER）及其静态
 * 降级描边、多停靠 specular 厚度层、底部内阴影、磨砂颗粒（连 sharedGrainBitmap/GRAIN_*
 * 与 LiquidGlassShaders.createLensEdgeShader 一并删净）；随 lens shader 失去消费方的
 * refractionStrength 死参数同步移除（GlassSurface 签名一并收窄）。
 *
 * v1.9.4 内凹改版（外凸 → 内凹，对齐 web 观感）：旧版沿整条 innerPath 描一圈内发丝
 * （hairlineInner）——四边一圈亮线读作外凸珠边；web 的 `inset 0 1px 0` 内高光只渲染顶边一条，
 * 配合向下柔和投影才读作「内凹面」。现整圈内描边删除，顶边内高光与既有 2dp 顶部高光线
 * **合并为一条** `.edge` 语义的渐变细线（高 1.5dp、左右各内缩 10%、两端渐隐）；外圈
 * 1dp glassBorder 描边保留（= web border）。
 *
 * 果冻按压（保留，web :active scale 的对应物）：awaitEachGesture 纯手势观察器——全程不调用
 * consume()，真正不消费任何事件，可与外层 clickable/combinedClickable 共存；即使他人消费了
 * UP，仍能凭 changes.pressed 观察到抬起并复位（v1.9.4 修复：旧 detectTapGestures(onPress)
 * 实现消费 DOWN/UP，历史会话行/危机卡长按静默失效）。reducedMotion 时禁用。
 *
 * 用法：`.liquidGlass(shape).clip(shape)` 或直接用 [GlassSurface]。
 * **顺序坑（v1.7.1 二改）**：clip 必须放在 liquidGlass **之后**——liquidGlass 的软投影
 * 溢出圆角，若 clip 在前会把投影裁掉。
 *
 * @param shape 玻璃形状（决定 fill/stroke/highlight 的路径）
 * @param fill 填充组（默认 [GlassFill.Frost] = web frost --glass 组；顶栏/输入栏传
 * [GlassFill.Card] 用 --wy-card-* 渐变——web 两组填充色相不同，勿混用）
 * @param tint 叠加在 fill 之上、高光之下的渐变停靠点（用户气泡深棕 tint）
 * @param borderColor 覆盖外圈描边色（null 用 glassBorder = web 1px solid var(--glass-border)）
 * @param enablePressAnimation 是否启用果冻按压效果（默认 true）
 * @param shadowColor 远影色（null 用 glassShadow = web --g-shadow 首条；alpha=0 时整条不画）
 * @param shadowFeather 远影羽化半径（BlurMaskFilter 的 σ；CSS blur 半径 ≈ 2σ，web `0 14px 40px`
 * → σ 20dp）
 * @param shadowLift 远影垂直位移（web `0 14px …` 的 14px）
 * @param shadowNearColor 近影色（null 用 glassShadowNear = web --g-shadow 次条 `0 2px 6px
 * rgba(110,70,30,.1)`；暗色 web 无次条故该 token 全透明 → 自动不画）。web 对 Mica 顶栏/输入栏
 * 的 box-shadow 是**整条覆盖**（只剩一条栏级阴影）→ 两栏传 [Color.Transparent] 关掉近影
 * @param shadowNearFeather 近影羽化 σ（web 6px → 3dp）
 * @param shadowNearLift 近影垂直位移（web 2px → 2dp）
 * @param backdrop v1.9.4 卡片透光磨砂开关（默认 true）：页面 [LocalGlassBackdrop] 有层且
 *  API 31+ 时，在玻璃填充之下垫真实高斯模糊（取样页面 record 的流光背景层，防自反馈见
 *  BackdropGlass.kt）；null/低版本自动回退现状半透明，无设置开关。已显式接入全量磨砂的
 *  悬浮面（ChatScreen 顶栏/输入栏，经 [backdropLayer] 取样背景+内容）与本玻璃内部小件
 *  （顶栏模型 pill/状态点）传 false 防双重模糊/观感割裂
 * @param backdropLayer F41 修复：悬浮栏显式传入的全量磨砂层（[rememberGlassBackdropLayer]
 *  创建，取样「背景+内容」层）。原实现经独立的 glassBackdropLayer modifier 垫在链最底层，
 *  磨砂画在本面栏级投影**之前**——不透明磨砂被 α.30/.40 投影整体压暗，与卡片路径
 *  （投影之后、填充之前）层级矛盾。现由本函数在 onDrawBehind 内紧随投影之后绘制磨砂，
 *  两条路径层级统一；null 时回退 [backdrop] 的卡片级磨砂逻辑
 *
 * v1.8.1 B4：移除 glowPositions/glowIntensities——dead path（接收后从未使用）且引发 60fps 重组。
 * v1.9.4 评审修复：移除 scrollVelocity 死参数（接收后从未消费）与死 API liquidGlassScrollAware
 * （全仓库无调用点）。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GtjShape.xl,
    fill: GlassFill = GlassFill.Frost,
    tint: List<Pair<Float, Color>>? = null,
    borderColor: Color? = null,
    enablePressAnimation: Boolean = true,
    backdrop: Boolean = true,
    backdropLayer: GlassBackdropLayer? = null,
    shadowColor: Color? = null,
    shadowFeather: Dp = 20.dp,
    shadowLift: Dp = 14.dp,
    shadowNearColor: Color? = null,
    shadowNearFeather: Dp = 3.dp,
    shadowNearLift: Dp = 2.dp,
): Modifier {
    val p = LocalGtjColors.current

    // v1.9.4：reducedMotion 统一读取一次（果冻按压共用；系统"移除动画"→全静态）
    val reduced = rememberReducedMotion()

    // v1.9.4 卡片透光磨砂：消费页面 provide 的 LocalGlassBackdrop（背景+内容双层 holder），
    // 卡片级取样纯背景层（backgroundOnly=true，防自反馈/自重影，见 BackdropGlass.kt）。
    // null（页面未接入 / API<31 / backdrop=false）→ 本玻璃保持现状半透明，零额外开销。
    val cardBackdrop = if (backdrop) LocalGlassBackdrop.current else null
    val cardBackdropLayer = rememberGlassBackdropLayer(cardBackdrop, backgroundOnly = true)

    // L33 修复：enablePressAnimation 此前是死参数——KDoc 宣称「果冻按压」但函数体从未实现，
    // 7 处调用点传 true 全部无效。现补真实实现：观察按下/抬起驱动 0.97 缩放。
    // v1.9.4 修复：旧实现用 detectTapGestures(onPress)，它会消费 DOWN/UP——GlassSurface 在
    // onClick==null 时把本修饰符放在内层（调用方的 combinedClickable 在外层），内层先于外层
    // 收到事件并把 DOWN 消费掉，外层 awaitFirstDown(requireUnconsumed = true) 拿不到未消费的
    // DOWN，点击/长按静默失效（历史会话行、CrisisCard 长按均中招）。
    // 现改用 awaitEachGesture 纯观察器：全程不调用 consume()，真正不消费任何事件，
    // 可与外层 clickable/combinedClickable 共存；即使他人消费了 UP，
    // 我们仍能凭 changes.pressed 观察到抬起并复位。reducedMotion 时禁用。
    val pressEnabled = enablePressAnimation && !reduced
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "liquidGlassPressScale",
    )

    return this
        .graphicsLayer {
            if (pressEnabled && pressScale != 1f) {
                scaleX = pressScale
                scaleY = pressScale
            }
        }
        // v1.9.4 卡片透光磨砂 + F41 悬浮栏全量磨砂：玻璃面 root 位置由 onGloballyPositioned
        // 写入 holder（snapshot 等值短路），onDrawBehind 读——滚动/布局变化只触发该玻璃重绘，
        // 不触发重组
        .then(
            if (cardBackdropLayer != null || backdropLayer != null) {
                Modifier.onGloballyPositioned {
                    backdropLayer?.barPositionInRoot = it.positionInRoot()
                    cardBackdropLayer?.barPositionInRoot = it.positionInRoot()
                }
            } else {
                Modifier
            },
        )
        .pointerInput(pressEnabled) {
            if (!pressEnabled) return@pointerInput
            // v1.9.4：纯手势观察器，不消费任何事件（全程无 consume()）。
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    // 循环等待直到所有指针抬起（他人消费 UP 也不影响观察）→ finally 复位
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                } finally {
                    pressed = false
                }
            }
        }
        .drawWithCache {
            // 组合期已解析 token；缓存块内仅依赖 size/layoutDirection，跨帧复用
            val outline = shape.createOutline(size, layoutDirection, this)
            val fillPath: Path = when (outline) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }

            // ②' 用户 tint 渐变（150° 方向近似：右下斜向，尺寸相关 → 缓存块内构造）
            val tintBrush: Brush? = tint?.let { stops ->
                Brush.linearGradient(
                    colorStops = stops.map { it.first to it.second }.toTypedArray(),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.85f, size.height * 0.55f),
                )
            }

            // ① 外层柔和投影（web --g-shadow 双影，styles.css:18/40）：
            // 远影 `0 14px 40px rgba(110,70,30,.18)`（暗 rgba(0,0,0,.5)）→ CSS blur 40px ≈ 2σ
            // = σ20dp、位移 14dp、色取 glassShadow；近影 `0 2px 6px rgba(110,70,30,.1)`（暗色
            // web 无次条 → glassShadowNear 全透明，alpha==0 直接不画，零开销）。顶栏/输入栏的
            // Mica box-shadow 是整条覆盖（styles.css:535/539）→ 调用方传 Transparent 关近影，
            // 改用自己的栏级阴影色 + 位移 8dp。alpha==0 的影一律不建 Paint。
            val farColor = shadowColor ?: p.glassShadow
            val shadowPaint = if (farColor.alpha > 0f) {
                Paint().apply {
                    color = farColor
                    asFrameworkPaint().maskFilter =
                        android.graphics.BlurMaskFilter(
                            shadowFeather.toPx(),
                            android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                }
            } else {
                null
            }
            val shadowDy = shadowLift.toPx()
            val nearColor = shadowNearColor ?: p.glassShadowNear
            val shadowNearPaint = if (nearColor.alpha > 0f) {
                Paint().apply {
                    color = nearColor
                    asFrameworkPaint().maskFilter =
                        android.graphics.BlurMaskFilter(
                            shadowNearFeather.toPx(),
                            android.graphics.BlurMaskFilter.Blur.NORMAL,
                        )
                }
            } else {
                null
            }
            val shadowNearDy = shadowNearLift.toPx()

            // ② 玻璃填充：v1.9.4 Mica 两组填充（web cautions：色相不同勿混用）——
            // Frost（默认）= frost 版 --glass 直色（亮 rgb(253,249,242)/暗 rgb(46,36,28) 均 .300，
            // 对应侧栏/胶囊/用户气泡/弹层/toast 组）；Card = --wy-card-light/dark 纵向渐变
            // （styles.css:526-531，仅顶栏/输入栏）。统一走纵向渐变绘制，Frost 双停靠点同色即平涂
            val fillBrush = when (fill) {
                GlassFill.Frost -> Brush.verticalGradient(listOf(p.glassFill, p.glassFill))
                GlassFill.Strong -> Brush.verticalGradient(listOf(p.glassFillStrong, p.glassFillStrong))
                GlassFill.Card -> Brush.verticalGradient(listOf(p.glassCardFillTop, p.glassCardFillBottom))
            }

            // ③ 顶边内高光（内凹改版·只沿顶边一条渐变细线）= web .edge::before：
            // `top:0;left:10%;right:10%;height:1.5px;border-radius:99px;
            //  background:linear-gradient(90deg,transparent,var(--edge),transparent)`
            // （styles.css:93）。web 的「内凹面」= 这一条顶边高光 + 向下柔和投影；旧版另有
            // 一圈内发丝（innerPath 全路径 stroke），四边亮线成环读作外凸珠边，已删。
            val edgeHeight = 1.5.dp.toPx()
            val edgeRect = Rect(
                left = size.width * 0.1f,
                top = 0f,
                right = size.width * 0.9f,
                bottom = edgeHeight,
            )
            val edgeBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to p.glassEdgeHighlight,
                    1f to Color.Transparent,
                ),
                startX = edgeRect.left,
                endX = edgeRect.right,
            )

            // ④ 外圈描边 1dp（= web `border:1px solid var(--glass-border)`，styles.css:82/39）：
            // 读 glassBorder（borderColor 可覆盖——用户气泡传棕色 --uborder 同 web）。
            // 居中描边不裁剪：一半溢出圆角形成外缘发丝，随投影一同浮出。
            val outerBorderWidth = 1.dp.toPx()
            val outerBorderColor = borderColor ?: p.glassBorder

            onDrawBehind {
                // ① 软投影：远影 + 近影（web --g-shadow 双影；任一 alpha==0 时对应 Paint 为 null 不画）。
                // 先画，溢出圆角无碍
                if (shadowPaint != null) {
                    translate(top = shadowDy) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), shadowPaint.asFrameworkPaint())
                        }
                    }
                }
                if (shadowNearPaint != null) {
                    translate(top = shadowNearDy) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), shadowNearPaint.asFrameworkPaint())
                        }
                    }
                }

                // 磨砂垫层：投影之后、玻璃填充之前（卡片路径的原有层级）。
                // F41 修复：显式传入的悬浮栏全量磨砂层优先——原独立 glassBackdropLayer modifier
                // 把不透明磨砂垫在链最底层，本面的 α.30/.40 栏级投影画在磨砂之上把整条栏压暗；
                // 现与卡片路径同序。未传入时回退卡片透光磨砂（取样纯背景层防自反馈）：
                // recordBlur 读 barPositionInRoot/backdrop position state（draw 阶段观察）；
                // 模糊层 display list 持久引用取样层 RenderNode——流光动画每帧更新取样层，
                // 玻璃静止时无需重录即得最新模糊（除本 record 外零分配）。
                val activeBackdropLayer = backdropLayer ?: cardBackdropLayer
                if (activeBackdropLayer != null) {
                    drawBackdropBlur(activeBackdropLayer, fillPath)
                }

                // ② 玻璃填充（Mica 纵向渐变 → 底下光斑/背景透出；backdrop 模糊层由独立 modifier 垫在本层之下）
                drawPath(fillPath, fillBrush)

                // ②' 用户 tint 渐变（在 fill 之上、高光/描边之下）
                if (tintBrush != null) {
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawRect(brush = tintBrush)
                    }
                }

                // ③ 顶边内高光（= web .edge，只沿顶边一条；内凹面的「上沿受光」）
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(
                        brush = edgeBrush,
                        topLeft = edgeRect.topLeft,
                        size = edgeRect.size,
                    )
                }

                // ④ 外圈描边（单线 = web `1px solid var(--glass-border)`；整圈内发丝已删，见类注释
                // 「内凹改版」）。居中描边不裁剪——一半溢出圆角形成外缘发丝，随投影一同浮出。
                drawPath(fillPath, outerBorderColor, style = Stroke(width = outerBorderWidth))
            }
        }
    }

/**
 * v1.8.0 玻璃容器：内容置于玻璃绘制之上。
 * 带 onClick 时内部先 clip 再 clickable，保证涟漪不溢出圆角而投影保持完整。
 *
 * @param fill 填充组（默认 [GlassFill.Frost]，语义同 [liquidGlass]；顶栏/输入栏走 Card）
 * @param backdrop v1.9.4 卡片透光磨砂开关（默认 true，语义同 [liquidGlass]）
 * @param shadow 外层投影开关（默认 true = web .glass 双影）。false 用于 web 里本就没有
 * box-shadow 的玻璃面（侧栏行 .sb-item / 新建会话 .sb-new 等面板内行）——两条影都以
 * Transparent 传入，引擎 alpha==0 直接跳过，零开销
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GtjShape.xl,
    fill: GlassFill = GlassFill.Frost,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    enablePressAnimation: Boolean = true,
    backdrop: Boolean = true,
    shadow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = Modifier.liquidGlass(
        shape = shape,
        fill = fill,
        enablePressAnimation = enablePressAnimation,
        backdrop = backdrop,
        shadowColor = if (shadow) null else Color.Transparent,
        shadowNearColor = if (shadow) null else Color.Transparent,
    )
    if (onClick != null) {
        Box(
            modifier
                .then(glass)
                .clip(shape)
                .clickable(enabled = enabled, onClick = onClick),
            content = content,
        )
    } else {
        Box(modifier.then(glass), content = content)
    }
}
