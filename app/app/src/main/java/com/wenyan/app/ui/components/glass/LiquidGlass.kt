package com.wenyan.app.ui.components.glass

import android.annotation.SuppressLint
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wenyan.app.ui.theme.rememberReducedMotion
import com.wenyan.app.ui.theme.GtjShape
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.LocalGtjIsDark
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** 30fps 节流：距上一写入帧 <33ms 的帧回调直接跳过（同 FluidBackground，uTime 波纹无需 60fps）。 */
private const val FRAME_INTERVAL_NANOS = 33_000_000L

/** 磨砂颗粒噪声位图边长（design-tokens.json component.glass.grainSize，≤128px）。 */
private const val GRAIN_SIZE = 96

/** 磨砂颗粒叠加 alpha（design-tokens.json component.glass.grainAlpha）。 */
private const val GRAIN_ALPHA = 0.06f

/** 磨砂颗粒噪声固定种子（装饰性纹理，非安全用途；固定种子=纹理稳定不闪烁）。 */
private const val GRAIN_SEED = 0x5EEDL

/** 进程内共享磨砂颗粒位图缓存（键 = 亮/暗噪点 token 色；仅 Compose 主线程访问，无需加锁）。 */
private var cachedGrainBitmap: android.graphics.Bitmap? = null
private var cachedGrainKey: Pair<Int, Int>? = null

/**
 * 取共享磨砂颗粒位图：同 token 色复用同一张（纹理仅依赖 glassGrainLight/glassGrainDark），
 * 色值变化（主题切换）时重建。像素逻辑与 v1.9.4 首版逐行一致，仅从"每实例一张"下沉为共享。
 */
private fun sharedGrainBitmap(grainLight: Color, grainDark: Color): android.graphics.Bitmap {
    val key = grainLight.toArgb() to grainDark.toArgb()
    cachedGrainBitmap?.let { cached -> if (cachedGrainKey == key) return cached }
    // 固定种子：同一次运行内纹理恒定，不随重组重掷（避免闪烁）
    val rnd = kotlin.random.Random(GRAIN_SEED)
    val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) { _ ->
        val base = if (rnd.nextBoolean()) grainLight else grainDark
        (0xFF shl 24) or
            (base.red.toInt() shl 16) or
            (base.green.toInt() shl 8) or
            base.blue.toInt()
    }
    return android.graphics.Bitmap.createBitmap(GRAIN_SIZE, GRAIN_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        .apply { setPixels(pixels, 0, GRAIN_SIZE, 0, 0, GRAIN_SIZE, GRAIN_SIZE) }
        .also {
            cachedGrainBitmap = it
            cachedGrainKey = key
        }
}

/**
 * v1.8.0 液态玻璃 2.0 · iOS 26 Liquid Glass 风格
 *
 * 核心升级（对齐 iOS 26 Liquid Glass 四大特征）：
 * 1. 折射（Refraction）：边缘透镜效应，内容透过玻璃时边缘弯曲（API 33+ RuntimeShader）
 * 2. 动态镜面高光：随时间/滚动流动的光带（API 33+ RuntimeShader）
 * 3. 边缘透镜（Lens Edge）：玻璃边缘 1.5dp 亮边/深色模式辉光
 * 4. 果冻按压（Squishy Press）：按压时局部凹陷 + 回弹 overshoot
 *
 * 降级链：
 * - API 33+：完整 RuntimeShader 效果（折射 + 动态高光 + 边缘透镜）
 * - API 31-32：RenderEffect 模糊 + 预渲染位图折射 + 静态高光
 * - API < 31：v1.7.6 四要素静态玻璃（半透明填充 + 顶部高光 + 细描边 + 柔和投影）
 *
 * 用法：`.liquidGlass(shape).clip(shape)` 或直接用 [GlassSurface]。
 * **顺序坑（v1.7.1 二改）**：clip 必须放在 liquidGlass **之后**——liquidGlass 的软投影
 * 溢出圆角，若 clip 在前会把投影裁掉。
 *
 * v1.9.4 质感升级（修复 v1.8.0 实测质感平/廉价的根因）：
 * - 根因①：LENS_EDGE_SHADER 的 vec3（AGSL 不支持）已规范为 float3（见 LiquidGlassShaders.kt），
 *   真机不再静默回退静态亮边，色散/波纹真正渲染；
 * - 根因②：createLensEdgeShader 调用从不传 time（恒 0f）→ 所有 sin(uTime…) 静止。
 *   现按 GlowBackground/FluidBackground 模式驱动：withInfiniteAnimationFrameNanos 30fps 节流
 *   写 mutableLongStateOf，onDrawBehind 内才读取（只触发重绘不触发重组，v1.8.1 B4 教训）；
 *   shader 与 Paint 均在 drawWithCache 缓存块内创建跨帧复用，每帧仅 setFloatUniform，零分配；
 * - 根因③：createRefractionShader/createSpecularShader 死代码已删（LiquidGlassShaders.kt）；
 * - 根因④：真实背景模糊由 [BackdropGlass.kt]（glassBackdropContent/glassBackdropLayer，
 *   Compose GraphicsLayer，API 31+）承担——独立 modifier 垫在 liquidGlass 之前绘制，
 *   默认不接入，其他页面 GlassSurface 行为不变；
 * - 材质细节：双发丝描边（外深内浅）、顶部多停靠 specular、底部内阴影、磨砂颗粒
 *   （预生成 ≤128px 噪声位图 + BitmapShader 双线性平铺），色值全走 token。
 *
 * @param shape 玻璃形状（决定 fill/stroke/highlight 的路径）
 * @param strong true 用 glassFillStrong（输入胶囊/高密度容器），false 用 glassFill
 * @param tint 叠加在 fill 之上、高光之下的渐变停靠点（用户气泡深棕 tint）
 * @param borderColor 覆盖双发丝外圈色（null 用 glassHairlineOuter）
 * @param refractionStrength 折射强度 0.0~1.0（默认 0.5，仅 API 33+ 生效）
 * @param enablePressAnimation 是否启用果冻按压效果（默认 true）
 * @param backdrop v1.9.4 卡片透光磨砂开关（默认 true）：页面 [LocalGlassBackdrop] 有层且
 *  API 31+ 时，在玻璃填充之下垫真实高斯模糊（取样页面 record 的流光背景层，防自反馈见
 *  BackdropGlass.kt）；null/低版本自动回退现状半透明，无设置开关。已显式接入全量磨砂的
 *  悬浮面（ChatScreen 顶栏/输入栏，glassBackdropLayer 取样背景+内容）与本玻璃内部小件
 *  （顶栏模型 pill/状态点）传 false 防双重模糊/观感割裂
 *
 * v1.8.1 B4：移除 glowPositions/glowIntensities——dead path（接收后从未使用）且引发 60fps 重组。
 * v1.9.4 评审修复：移除 scrollVelocity 死参数（接收后从未消费）与死 API liquidGlassScrollAware
 * （全仓库无调用点）——「别留死路径」，动态高光流动实际由 uTime 恒时驱动。
 */
@SuppressLint("NewApi") // RuntimeShader 伪折射已由 isRuntimeShaderSupported() 做 API 33 守卫
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    tint: List<Pair<Float, Color>>? = null,
    borderColor: Color? = null,
    refractionStrength: Float = 0.5f,
    enablePressAnimation: Boolean = true,
    backdrop: Boolean = true,
): Modifier {
    val p = LocalGtjColors.current
    val density = LocalDensity.current

    // v1.8.1 B5：深色模式改读显式 token（Theme 层解析后下发），不再靠 bg.red 启发式猜（陶土棕/中性灰会误判）
    val isDarkMode = LocalGtjIsDark.current
    val supportsRuntimeShader = LiquidGlassShaders.isRuntimeShaderSupported()

    // v1.9.4：reducedMotion 统一读取一次（uTime 动画与果冻按压共用；系统"移除动画"→全静态）
    val reduced = rememberReducedMotion()

    // v1.9.4 根因②修复：uTime 动画驱动（照 GlowBackground.kt / FluidBackground.kt 模式）。
    // frameNanos 仅在 onDrawBehind（draw 阶段）被读取 → 状态写入只触发本节点重绘，
    // 不触发重组（v1.8.1 B4 教训）；30fps 节流：距上一写入 <33ms 的帧回调直接跳过；
    // reducedMotion（系统"移除动画"）不启动循环，uTime 恒 0 静态帧。
    var lensTimeNanos by remember { mutableLongStateOf(0L) }
    if (supportsRuntimeShader && !reduced) {
        LaunchedEffect(Unit) {
            var lastEmitted = 0L
            while (currentCoroutineContext().isActive) {
                withInfiniteAnimationFrameNanos { now ->
                    if (now - lastEmitted >= FRAME_INTERVAL_NANOS) {
                        lastEmitted = now
                        lensTimeNanos = now
                    }
                }
            }
        }
    }

    // v1.9.4 磨砂颗粒：预生成一张小噪声位图（≤128px，remember 一次），
    // 亮/暗噪点色取 token（glassGrainLight/glassGrainDark），固定种子保证纹理跨帧稳定。
    // 评审修复：纹理仅依赖两 token 色 → 进程内共享一张（此前每玻璃实例各建一张 ≈36KB，
    // 长会话随屏上玻璃面数线性增长），主题切换（token 色变化）时重建。
    val grainBitmap = remember(p.glassGrainLight, p.glassGrainDark) {
        sharedGrainBitmap(p.glassGrainLight, p.glassGrainDark)
    }

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
        // v1.9.4 卡片透光磨砂：玻璃面 root 位置由 onGloballyPositioned 写入 holder（snapshot
        // 等值短路），onDrawBehind 读——滚动/布局变化只触发该玻璃重绘，不触发重组
        .then(
            if (cardBackdropLayer != null) {
                Modifier.onGloballyPositioned { cardBackdropLayer.barPositionInRoot = it.positionInRoot() }
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
            // 组合期已解析 token；缓存块内仅依赖 size/density/layoutDirection，跨帧复用
            val outline = shape.createOutline(size, layoutDirection, this)
            val fillPath: Path = when (outline) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }

            // v1.9.4 评审修复：uRadius 取实际 shape 圆角（outline 半径已按尺寸 clamp，如 48dp 高
            // 输入栏的 inputBar 28dp→24dp、CircleShape pill 取半宽）——圆角区亮边/色散带与物理
            // 边缘对齐；此前恒用 xlRadius 18dp 与实际圆角错位约 6dp（v1.8.0 既有，v1.9.4 修好
            // vec3 后 shader 真正渲染才显形）。Rectangle 无圆角、Generic 无圆角信息 → 回退默认。
            val cornerRadiusPx = when (outline) {
                is Outline.Rectangle -> 0f
                is Outline.Rounded -> maxOf(
                    outline.roundRect.topLeftCornerRadius.x,
                    outline.roundRect.topRightCornerRadius.x,
                    outline.roundRect.bottomRightCornerRadius.x,
                    outline.roundRect.bottomLeftCornerRadius.x,
                )
                is Outline.Generic -> with(density) { GtjShape.xlRadius.toPx() }
            }

            // ②' 用户 tint 渐变（150° 方向近似：右下斜向，尺寸相关 → 缓存块内构造）
            val tintBrush: Brush? = tint?.let { stops ->
                Brush.linearGradient(
                    colorStops = stops.map { it.first to it.second }.toTypedArray(),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.85f, size.height * 0.55f),
                )
            }

            // ① 柔和外投影（BlurMaskFilter 高斯模糊，色来自 glassShadow token）
            val shadowPaint = Paint().apply {
                color = p.glassShadow
                asFrameworkPaint().maskFilter =
                    android.graphics.BlurMaskFilter(
                        14.dp.toPx(),
                        android.graphics.BlurMaskFilter.Blur.NORMAL,
                    )
            }
            val shadowDy = 5.dp.toPx()

            // ② 玻璃填充
            val fillPaint = Paint().apply {
                color = if (strong) p.glassFillStrong else p.glassFill
                isAntiAlias = true
            }

            // ②'' v1.9.4 玻璃厚度层（材质升级）：顶部多停靠 specular + 底部内阴影
            // specular：方向性高光（顶部最亮 → 22% 处衰减 → 68% 处回光），基色 token glassSpecular
            val specularColor = p.glassSpecular
            val specularBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to specularColor,
                    0.07f to specularColor.copy(alpha = specularColor.alpha * 0.45f),
                    0.22f to specularColor.copy(alpha = 0f),
                    0.68f to specularColor.copy(alpha = 0f),
                    1f to specularColor.copy(alpha = specularColor.alpha * 0.35f),
                ),
            )
            // 底部内阴影（token glassInnerShade，62% 以下渐入）
            val innerShadeColor = p.glassInnerShade
            val bottomShadeBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to innerShadeColor.copy(alpha = 0f),
                    1f to innerShadeColor,
                ),
                startY = size.height * 0.62f,
                endY = size.height,
            )

            // ②''' v1.9.4 磨砂颗粒：BitmapShader 双线性平铺（REPEAT）+ 低 alpha 叠进玻璃填充，
            // Paint 跨帧复用（颗粒位图/着色器与动画无关，缓存块内一次构建）
            val grainPaint = Paint().apply {
                asFrameworkPaint().shader = android.graphics.BitmapShader(
                    grainBitmap,
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT,
                )
                // 双线性滤波：颗粒在非整数倍缩放时平滑（"双线性平铺"）
                asFrameworkPaint().isFilterBitmap = true
                asFrameworkPaint().alpha = (GRAIN_ALPHA * 255).toInt()
                isAntiAlias = true
            }

            // ③ 顶部高光线（静态降级方案）
            val edgeHeight = 2.dp.toPx()
            val edgeRect = Rect(
                left = size.width * 0.1f,
                top = 0f,
                right = size.width * 0.9f,
                bottom = edgeHeight,
            )
            val edgeBrush = Brush.verticalGradient(
                colorStops = arrayOf(0f to p.glassEdgeHighlight, 1f to Color.Transparent),
                startY = 0f,
                endY = edgeHeight,
            )

            // ④ v1.9.4 双发丝描边：外圈深线（hairlineOuter，borderColor 可覆盖）+ 内圈亮线
            // （hairlineInner）。外圈 1dp 居中描边不裁剪（一半溢出圆角形成外缘发丝）；
            // 内圈 0.75dp 描边在 clip 内画，紧贴边缘内侧（对应桌面 inset 0 1px 内高光）。
            val hairlineOuterWidth = 1.dp.toPx()
            val hairlineOuterColor = borderColor ?: p.glassHairlineOuter
            val hairlineInnerWidth = 0.75.dp.toPx()
            val hairlineInnerColor = p.glassHairlineInner
            // 内圈路径：整体内缩 hairlineInnerWidth/2 后的 outline（构造逻辑与 lensEdgePath 一致）
            val innerInset = hairlineInnerWidth / 2
            val innerPath: Path = when (val innerOutline = shape.createOutline(
                Size(size.width - innerInset * 2, size.height - innerInset * 2),
                layoutDirection,
                this,
            )) {
                is Outline.Rectangle -> Path().apply { addRect(innerOutline.rect.translate(Offset(innerInset, innerInset))) }
                is Outline.Rounded -> Path().apply {
                    val r = innerOutline.roundRect
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = r.left + innerInset,
                            top = r.top + innerInset,
                            right = r.right - innerInset,
                            bottom = r.bottom - innerInset,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.topLeftCornerRadius.x, r.topLeftCornerRadius.y),
                        ),
                    )
                }
                is Outline.Generic -> Path().apply { addPath(innerOutline.path, Offset(innerInset, innerInset)) }
            }

            // v1.8.0：API 33+ 使用 RuntimeShader 实现折射 + 动态高光 + 边缘透镜
            val useRuntimeShader = supportsRuntimeShader && refractionStrength > 0f

            // v1.9.4 根因②配套：lensEdgePath 从 onDrawBehind 移入缓存块——路径只依赖 size/shape，
            // 与动画无关；原先每帧绘制都新建 Path（违背每帧零分配）。inset 0.75dp 与内发丝一致。
            val lensEdgeWidth = 1.5.dp.toPx()
            val lensEdgePath: Path = run {
                val inset = lensEdgeWidth / 2
                when (val outline = shape.createOutline(
                    Size(size.width - inset * 2, size.height - inset * 2),
                    layoutDirection,
                    this,
                )) {
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect.translate(Offset(inset, inset))) }
                    is Outline.Rounded -> Path().apply {
                        val r = outline.roundRect
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = r.left + inset,
                                top = r.top + inset,
                                right = r.right - inset,
                                bottom = r.bottom - inset,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r.topLeftCornerRadius.x, r.topLeftCornerRadius.y),
                            ),
                        )
                    }
                    is Outline.Generic -> Path().apply { addPath(outline.path, Offset(inset, inset)) }
                }
            }

            // v1.9.4：边缘透镜色（RuntimeShader 与静态降级分支共用）
            val lensEdgeColor = if (isDarkMode) {
                p.glowA.copy(alpha = 0.3f)
            } else {
                Color.White.copy(alpha = 0.5f)
            }

            // v1.9.4 根因①②配套：lensEdgeShader 与其 Paint 移入缓存块真正创建一次（跨帧复用）。
            // （v1.8.1 B2 的注释宣称"缓存块内创建一次"，实际代码在 onDrawBehind 内每帧重建——
            // 本次一并落实。）createLensEdgeShader 失败返回 null（个别 ROM AGSL 编译失败）
            // → 回退静态亮边分支；time 初始 0f，onDrawBehind 内每帧覆写 uTime uniform。
            val lensEdgeShader = if (useRuntimeShader) {
                LiquidGlassShaders.createLensEdgeShader(
                    size = size,
                    cornerRadius = cornerRadiusPx,
                    edgeColor = lensEdgeColor,
                    glowColor = if (isDarkMode) p.glowA else Color.White,
                    isDarkMode = isDarkMode,
                    refractionStrength = refractionStrength,
                )
            } else {
                null
            }
            val lensEdgeShaderPaint = lensEdgeShader?.let {
                Paint().apply {
                    this.shader = it
                    isAntiAlias = true
                }
            }

            onDrawBehind {
                // ① 软投影：先画，溢出圆角无碍
                translate(top = shadowDy) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), shadowPaint.asFrameworkPaint())
                    }
                }

                // v1.9.4 卡片透光磨砂：把模糊后的背景层 clip 进玻璃形状，垫在玻璃填充之下
                // （投影之后、填充之前）。recordBlur 读 barPositionInRoot/backdrop position
                // state（draw 阶段观察）；模糊层 display list 持久引用背景层 RenderNode——
                // 流光动画每帧更新背景层，卡片静止时无需重录即得最新模糊（除本 record 外零分配）。
                if (cardBackdropLayer != null) {
                    cardBackdropLayer.recordBlur(
                        density = this,
                        layoutDirection = layoutDirection,
                        barSize = size,
                        barPositionInRoot = cardBackdropLayer.barPositionInRoot,
                    )
                    // v1.9.4 评审修复（磨砂影像偏移）：record 时玻璃左上角的内容在层内
                    // (cardPad,cardPad)，drawLayer 把层的 (0,0) 画在玻璃 (0,0)——不反向平移
                    // 则卡片磨砂影像整体向右下偏移一个 cardSamplePadding（8dp）。translate(-pad)
                    // 后层内 (pad,pad) 落回玻璃 (0,0)；pad 由 recordBlur 写入 recordedPadPx 缓存。
                    clipPath(fillPath) {
                        translate(-cardBackdropLayer.recordedPadPx, -cardBackdropLayer.recordedPadPx) {
                            drawLayer(cardBackdropLayer.blurLayer)
                        }
                    }
                }

                // ② 玻璃填充（半透明 → 底下光斑/背景透出；backdrop 模糊层由独立 modifier 垫在本层之下）
                drawPath(fillPath, fillPaint.color)

                // ②· v1.9.4 磨砂颗粒：低 alpha 噪声平铺叠进玻璃填充（fill 之上、厚度层之下）
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawPath(fillPath.asAndroidPath(), grainPaint.asFrameworkPaint())
                    }
                }

                // ②' v1.9.4 厚度层：顶部多停靠 specular + 底部内阴影（原上微光/下微影升级）
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(brush = specularBrush)
                    drawRect(brush = bottomShadeBrush)
                }

                // ②'' 用户 tint 渐变（在 fill/厚度层之上、高光/描边之下）
                if (tintBrush != null) {
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawRect(brush = tintBrush)
                    }
                }

                // v1.8.0：RuntimeShader 伪折射效果（API 33+）
                // v1.9.4 根因②：每帧覆写 uTime uniform（shader/Paint 均为缓存块内复用对象，
                // 本帧零分配）；frameNanos → 秒，reducedMotion 时恒 0 静态帧。
                // 本块读 lensTimeNanos state → 仅触发本节点重绘，不触发重组（v1.8.1 B4）。
                if (lensEdgeShader != null && lensEdgeShaderPaint != null) {
                    lensEdgeShader.setFloatUniform(
                        "uTime",
                        if (reduced) 0f else lensTimeNanos / 1e9f,
                    )
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                0f, 0f, size.width, size.height,
                                lensEdgeShaderPaint.asFrameworkPaint(),
                            )
                        }
                    }
                } else {
                    // 降级：静态边缘亮边（API < 33 / 折射关闭 / AGSL 编译失败）
                    withTransform({
                        clipPath(fillPath)
                    }) {
                        drawPath(
                            path = lensEdgePath,
                            color = lensEdgeColor,
                            style = Stroke(width = lensEdgeWidth),
                        )
                    }
                }

                // ③ 顶部高光（静态降级：API < 33 或未启用折射）
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawRect(
                        brush = edgeBrush,
                        topLeft = edgeRect.topLeft,
                        size = edgeRect.size,
                    )
                }

                // ④ v1.9.4 双发丝描边：外圈深线（hairlineOuter，不裁剪——一半溢出圆角形成外缘
                // 发丝，随投影一同浮出）+ 内圈亮线（hairlineInner，clip 内紧贴边缘内侧）。
                // 替代 v1.7.6 单色 1dp 描边（borderColor 仍可覆盖外圈色，签名向后兼容）。
                drawPath(fillPath, hairlineOuterColor, style = Stroke(width = hairlineOuterWidth))
                withTransform({
                    clipPath(fillPath)
                }) {
                    drawPath(
                        path = innerPath,
                        color = hairlineInnerColor,
                        style = Stroke(width = hairlineInnerWidth),
                    )
                }
            }
        }
    }

/**
 * v1.8.0 玻璃容器：内容置于玻璃绘制之上。
 * 带 onClick 时内部先 clip 再 clickable，保证涟漪不溢出圆角而投影保持完整。
 *
 * @param backdrop v1.9.4 卡片透光磨砂开关（默认 true，语义同 [liquidGlass]）
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GtjShape.xl,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    refractionStrength: Float = 0.5f,
    enablePressAnimation: Boolean = true,
    backdrop: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = Modifier.liquidGlass(
        shape = shape,
        strong = strong,
        refractionStrength = refractionStrength,
        enablePressAnimation = enablePressAnimation,
        backdrop = backdrop,
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
