package com.wenyan.app.ui.components.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize

/**
 * v1.9.4 真实背景模糊（backdrop blur）· 对齐桌面 styles.css Mica 悬浮玻璃配方
 * （styles.css 455 行 `--wy-glass-blur:20px` + 514 行 `backdrop-filter: ... saturate(170%)`）。
 *
 * 根因④：v1.8.0 玻璃本体只是半透明填充，无真实背景模糊——安卓端缺失 backdrop-filter。
 * 本文件用 Compose GraphicsLayer（BOM 2025.06.01 / ui 1.8）自研实现，零第三方库。
 *
 * 双层结构（GraphicsLayer 底层是 RenderNode，display list 持久持有子层引用）：
 * - 内容层（[GlassBackdrop.contentLayer]）：ChatScreen 内容区经 [glassBackdropContent]
 *   `record` 进层后正常画出。内容自身失效（滚动/新消息）→ record 更新——这就是图层
 *   失效机制本身，不手动 invalidate；
 * - 模糊层（[GlassBackdropLayer.blurLayer]，每玻璃面一个）：玻璃面经 [glassBackdropLayer]
 *   在 drawBehind 中把内容层按"栏相对内容区的位置"translate 后画进自己（只在玻璃节点
 *   重绘时 record），renderEffect = BlurEffect、colorFilter = 饱和度/亮度 ColorMatrix
 *   （对应桌面 saturate(170%)），再 clip 进玻璃圆角路径、垫在玻璃填充之下。
 *
 * 性能（v1.8.1 B4 教训）：
 * - 不每帧手动 invalidate/重录：滚动帧只有内容层 record（内容自身重绘）；模糊层的
 *   display list 经 RenderNode 嵌套引用自动取到最新内容，玻璃节点不重放；
 * - contentPosition/barPositionInRoot 用 snapshot State：onGloballyPositioned 写
 *   （layout 阶段，等值短路——滚动帧内容区/栏自身不动，零写入开销），
 *   玻璃侧 onDrawBehind 读（draw 阶段观察 → 只触发该玻璃重绘，不触发重组）；
 * - 效果对象零分配：BlurEffect/ColorFilter/Path 均缓存复用（仅模糊半径变化时重建效果）；
 *   唯一例外（评审偏差记录）：GraphicsLayer.record 非 inline，其 lambda 每次重录分配 1 个
 *   闭包小对象——内容层滚动帧、模糊层玻璃重绘帧各一个，非渲染热路径；
 * - 采样余量 samplePadding（≈2×blur）：模糊输入超出玻璃边界，边缘不褪色（对应 CSS
 *   backdrop-filter 采样元素外内容的语义）。
 *
 * 降级：API < 31（RenderEffect 门槛）时 [rememberGlassBackdrop] 返回 null，所有相关
 * modifier 直接 no-op → 调用方回退现状静态玻璃（半透明填充）。
 * 范围（v1.9.4 扩展）：
 * - 悬浮栏全量磨砂：ChatScreen 顶栏/输入栏（glassBackdropContent 显式接入，取样
 *   「背景+内容」全量层，消息从栏下穿过被磨砂，对齐桌面 Mica）；
 * - 卡片透光磨砂（v1.9.4 新增）：Chat/Settings/Onboarding/ProviderEdit/MemoryEdit 各页
 *   record 自己的「流光背景层」并 [LocalGlassBackdrop] provide，全部 GlassSurface/liquidGlass
 *   内部自动消费——卡片之下垫真实高斯模糊（[GlassBackdropParams.cardBlurRadius]，
 *   对齐桌面玻璃设置「模糊度」运行时默认 4px；v1.9.4 三改随玻璃通透化加倍到 8dp），
 *   卡片自反馈/自重影经「取样纯背景层」结构性消除
 *   （见 [GlassBackdropLayer.recordBlur] 注释），未接入页面 null 自动回退半透明。
 *
 * 与抽屉模糊（ChatScreen 根 Box 的整层 BlurEffect(18f)）共存：抽屉 blur 挂在更外层的
 * graphicsLayer 上，作用于整棵子树（含本组件的模糊结果），两层离屏效果叠加，无冲突。
 */

/** 玻璃 backdrop 数值参数（唯一来源 docs/design-tokens.json component.glassBackdrop）。 */
object GlassBackdropParams {
    /** 模糊半径（对应桌面 --wy-glass-blur:20px）。 */
    val blurRadius: Dp = 20.dp

    /** 饱和度提升（对应桌面 saturate(170%)）。 */
    const val SATURATION = 1.7f

    /** 轻微亮度提升（磨砂后的暖色不发闷）。 */
    const val BRIGHTNESS = 1.03f

    /** 模糊采样余量（≈2×blur）：玻璃边缘模糊采到栏外内容，不出现边缘褪色。 */
    val samplePadding: Dp = 40.dp

    // ── v1.9.4 卡片透光磨砂（全 App 玻璃卡片升级）──

    /**
     * 卡片级模糊半径（对应桌面玻璃设置「模糊度」运行时默认 4px，app.js:66 glassBlur 默认值；
     * styles.css :root 的 20px 仅为未开启玻璃主题时的占位）。
     * v1.9.4 三改（卡片通透化）：4dp → 8dp —— 玻璃填充 alpha 由 .55/.72 降到 .40/.549 后，
     * 透出的背景更依赖「磨砂」而非「半透明覆盖」，模糊半径加倍让流光在卡片内真正化开
     * （同时把流光的深色涡心平均掉，卡片内文字底对比反而更稳，见 GlassTransparencyReadabilityTest）。
     * 仍取小值是性能护栏：卡片数量多（消息气泡/设置行），每卡片一个模糊 pass；
     * 8dp 远低于悬浮栏 20dp，且模糊层的 RenderEffect 只在半径变化时重建（[GlassBackdropLayer.recordBlur]）。
     */
    val cardBlurRadius: Dp = 8.dp

    /** 卡片级采样余量（≈2×卡片 blur，与悬浮栏 samplePadding=2×blur 同比例）：8dp → 16dp。 */
    val cardSamplePadding: Dp = 16.dp
}

/**
 * backdrop 的饱和度+亮度合并 ColorMatrix（对应桌面 `saturate(170%)` + 轻微提亮）。
 * 标准饱和度矩阵（luminance 系数 0.213/0.715/0.072，saturation s）：
 *   R' = (0.213+0.787s)R + 0.715(1-s)G + 0.072(1-s)B
 *   G' = 0.213(1-s)R + (0.715+0.285s)G + 0.072(1-s)B
 *   B' = 0.213(1-s)R + 0.715(1-s)G + (0.072+0.928s)B
 * 再整体乘亮度 b（RGB 通道 ×b，alpha 行不变）——等价 CSS `saturate(s) brightness(b)`。
 * internal（仅模块内 + 单测可见）：词法作用域只在本文件的进程内单例使用，放宽到 internal
 * 只是为了让 GlassTransparencyReadabilityTest 用**真实生产参数**复算卡片内最坏底，而非在测试里复刻公式。
 */
internal fun buildBackdropColorMatrix(saturation: Float, brightness: Float): ColorMatrix {
    val inv = 1f - saturation
    return ColorMatrix(
        floatArrayOf(
            (0.213f + 0.787f * saturation) * brightness, (0.715f * inv) * brightness, (0.072f * inv) * brightness, 0f, 0f,
            (0.213f * inv) * brightness, (0.715f + 0.285f * saturation) * brightness, (0.072f * inv) * brightness, 0f, 0f,
            (0.213f * inv) * brightness, (0.715f * inv) * brightness, (0.072f + 0.928f * saturation) * brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** 饱和度/亮度合并矩阵进程内一份（参数为编译期常量，无状态）。 */
private val backdropColorMatrix = buildBackdropColorMatrix(GlassBackdropParams.SATURATION, GlassBackdropParams.BRIGHTNESS)

/**
 * 共享取样层 holder（页面内所有玻璃面共用）。
 * v1.9.4 扩展为双层：[contentLayer] = 「背景+内容」全量（悬浮栏 Mica 取样，消息从栏下穿过
 * 被磨砂）；[backgroundLayer] = 纯流光背景（卡片级磨砂取样源）。
 * 两个 position 均为原点的 root 坐标：record 侧 onGloballyPositioned 写（snapshot 等值短路），
 * 玻璃侧 onDrawBehind 读（draw 阶段观察 → 位置变化只触发该玻璃重绘，不触发重组）。
 */
class GlassBackdrop internal constructor(
    internal val contentLayer: GraphicsLayer,
    internal val backgroundLayer: GraphicsLayer,
) {
    internal var contentPosition by mutableStateOf(Offset.Zero)
    internal var backgroundPosition by mutableStateOf(Offset.Zero)
}

/**
 * v1.9.4 卡片透光磨砂分发：页面把 record 好「背景层」的 [GlassBackdrop] provide 进来，
 * 全部 [GlassSurface]/[liquidGlass] 玻璃面内部自动消费——卡片之下垫真实高斯模糊（API 31+），
 * 未 provide（或 API < 31）时为 null → 自动回退现状半透明玻璃，不新增设置开关。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 每玻璃面一个模糊层（顶栏/输入栏/各卡片各自独立，几何互不覆盖）。 */
class GlassBackdropLayer internal constructor(
    internal val blurLayer: GraphicsLayer,
    private val backdrop: GlassBackdrop,
    private val backgroundOnly: Boolean,
) {
    /** 本玻璃面原点的 root 坐标：onGloballyPositioned 写，onDrawBehind 读（等值短路）。 */
    internal var barPositionInRoot by mutableStateOf(Offset.Zero)

    /**
     * 最近一次 [recordBlur] 实际使用的采样余量（px，悬浮栏 40dp / 卡片 8dp 换算）：
     * record 把「玻璃左上角背后的内容」放在层内 (pad,pad)，绘制侧 drawLayer 前须
     * translate(-recordedPadPx) 反向对齐（drawLayer 把层的 (0,0) 画在玻璃 (0,0)）。
     * recordBlur 与绘制点紧邻先后执行（同一 onDrawBehind），直接读字段即得本帧值，零分配。
     */
    internal var recordedPadPx = 0f
        private set

    private var cachedRadiusPx = -1f
    private var cachedBlurEffect: BlurEffect? = null
    private val saturateFilter: ColorFilter = ColorFilter.colorMatrix(backdropColorMatrix)

    /**
     * 把取样层按玻璃相对位置画进模糊层（record 只在玻璃节点重绘时发生，非每帧）。
     * record 的 display list 持久持有取样层 RenderNode 引用——之后取样层更新（流光动画/
     * 内容滚动），模糊层无需重录即可渲染出最新模糊。
     *
     * v1.9.4 防反馈关键：[backgroundOnly]=true（卡片级）时取样源是 [GlassBackdrop.backgroundLayer]
     * ——它不引用 contentLayer、也不引用任何卡片的模糊层，因此「contentLayer（录制含卡片磨砂
     * 绘制）→ 卡片 blurLayer → backgroundLayer」引用链单向无环；且卡片自身内容不进磨砂，
     * 不会把自己的文字模糊进底色（无重影）。卡片之间在滚动布局中互不重叠（spacedBy 间距），
     * 「仅磨背景」与「磨背景+未磨砂内容」视觉等价；真正的内容穿透区（顶栏/输入栏之下）仍由
     * 悬浮栏的 contentLayer 全量磨砂承担，不回退。
     */
    internal fun recordBlur(
        density: Density,
        layoutDirection: LayoutDirection,
        barSize: Size,
        barPositionInRoot: Offset,
    ) {
        val source = if (backgroundOnly) backdrop.backgroundLayer else backdrop.contentLayer
        val sourcePosition = if (backgroundOnly) backdrop.backgroundPosition else backdrop.contentPosition
        val radius = if (backgroundOnly) GlassBackdropParams.cardBlurRadius else GlassBackdropParams.blurRadius
        val padDp = if (backgroundOnly) GlassBackdropParams.cardSamplePadding else GlassBackdropParams.samplePadding
        // 效果对象缓存：仅半径变化（密度/配置变化）时重建，玻璃重绘时零分配
        val blurRadiusPx = with(density) { radius.toPx() }
        if (blurRadiusPx != cachedRadiusPx || cachedBlurEffect == null) {
            cachedBlurEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Decal)
            cachedRadiusPx = blurRadiusPx
        }
        blurLayer.renderEffect = cachedBlurEffect
        // 饱和度/亮度挂层级 colorFilter：renderEffect（blur）先作用、colorFilter 后作用，
        // 与桌面 backdrop-filter: blur() saturate() 的顺序一致
        blurLayer.colorFilter = saturateFilter

        // 采样余量外扩：blurLayer 尺寸 = 玻璃尺寸 + 2×pad，(pad,pad) 对应玻璃左上角
        val padPx = with(density) { padDp.toPx() }
        // 缓存给绘制侧（glassBackdropLayer/liquidGlass）translate(-pad) 反向对齐用
        recordedPadPx = padPx
        val padded = Size(
            (barSize.width + padPx * 2).coerceAtLeast(1f),
            (barSize.height + padPx * 2).coerceAtLeast(1f),
        )
        val barOrigin = barPositionInRoot - sourcePosition
        blurLayer.record(density, layoutDirection, padded.roundToIntSize()) {
            translate(padPx - barOrigin.x, padPx - barOrigin.y) {
                drawLayer(source)
            }
        }
    }
}

/**
 * 创建玻璃 backdrop（内容层+背景层共享 holder）。
 * API < 31（RenderEffect 门槛）返回 null → 所有相关 modifier no-op，回退静态玻璃。
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val contentLayer = rememberGraphicsLayer()
    val backgroundLayer = rememberGraphicsLayer()
    return remember { GlassBackdrop(contentLayer, backgroundLayer) }
}

/**
 * 创建单个玻璃面的模糊层（顶栏/输入栏/卡片各自 remember 一个，几何独立）。
 * v1.9.4：[backgroundOnly]=true 供卡片级磨砂（liquidGlass 内部自动消费，取样纯背景层），
 * 默认 false 供悬浮栏全量磨砂（glassBackdropLayer 显式接入，取样背景+内容）。
 */
@Composable
fun rememberGlassBackdropLayer(
    backdrop: GlassBackdrop?,
    backgroundOnly: Boolean = false,
): GlassBackdropLayer? {
    if (backdrop == null) return null
    val layer = rememberGraphicsLayer()
    return remember(backdrop) { GlassBackdropLayer(layer, backdrop, backgroundOnly) }
}

/**
 * v1.9.4 背景侧：把流光背景（FluidBackground 所在铺满节点）record 进背景层后再正常画出。
 * 关键缺口修复：此前 contentLayer 只挂 Scaffold 内容，而 FluidBackground 画在 Scaffold 之外
 * ——卡片若直接取样会漏掉流光背景（模糊区域背后没有消息时无内容可磨）。现在背景独立成层：
 * 卡片磨砂取样它（纯背景），[glassBackdropContent] 再把它合进内容层头部（悬浮栏全量磨砂
 * 同样获得背景成分）。record 只在本节点内容失效时执行（流光动画每帧重绘 = 图层失效机制）。
 */
fun Modifier.glassBackdropBackground(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.backgroundPosition = it.positionInRoot() }
        .drawWithContent {
            backdrop.backgroundLayer.record(drawContext.density, drawContext.layoutDirection, size.roundToIntSize()) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.backgroundLayer)
        }
}

/**
 * 内容侧：把本节点（页面滚动内容区）的绘制 record 进内容层后再正常画出，
 * v1.9.4 起录制头部先合成背景层——内容层 = 「背景+内容」全量（悬浮栏 Mica 取样源）。
 * record 只在本节点内容失效时执行（内容自身重绘 = 图层失效机制），无手动 invalidate。
 */
fun Modifier.glassBackdropContent(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.contentPosition = it.positionInRoot() }
        .drawWithContent {
            backdrop.contentLayer.record(drawContext.density, drawContext.layoutDirection, size.roundToIntSize()) {
                // 先画流光背景层（按背景层相对内容区的位置对齐），再画内容。
                // backgroundLayer 是独立 RenderNode，此处引用单向无环（见 GlassBackdropLayer.recordBlur）
                val bgOffset = backdrop.backgroundPosition - backdrop.contentPosition
                translate(bgOffset.x, bgOffset.y) {
                    drawLayer(backdrop.backgroundLayer)
                }
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.contentLayer)
        }
}

/**
 * 玻璃面侧：把模糊后的内容层 clip 进 [shape] 圆角路径，垫在玻璃填充之下绘制。
 * 必须放在 `.liquidGlass(...)` **之前**（Compose 绘制顺序：链上靠前的 drawBehind 先画）
 * ——投影（liquidGlass ①）之后、玻璃填充之前，层级与桌面 Mica 一致。
 */
fun Modifier.glassBackdropLayer(
    layer: GlassBackdropLayer?,
    shape: Shape,
): Modifier {
    if (layer == null) return this
    return onGloballyPositioned { layer.barPositionInRoot = it.positionInRoot() }
        .drawWithCache {
            val glassPath: Path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            onDrawBehind {
                layer.recordBlur(
                    density = this,
                    layoutDirection = layoutDirection,
                    barSize = size,
                    barPositionInRoot = layer.barPositionInRoot,
                )
                // v1.9.4 评审修复（磨砂影像偏移）：record 时玻璃左上角的内容在层内 (pad,pad)，
                // drawLayer 会把层的 (0,0) 画在玻璃 (0,0)——不反向平移则磨砂影像整体向右下
                // 偏移一个 samplePadding（悬浮栏 40dp 顶栏/输入栏明显、卡片 8dp 轻微）。
                // translate(-pad) 后层内 (pad,pad) 落回玻璃 (0,0)，与 CSS backdrop-filter 对齐。
                // pad 由 [GlassBackdropLayer.recordedPadPx] 缓存（上方 recordBlur 刚写入本帧值）。
                clipPath(glassPath) {
                    translate(-layer.recordedPadPx, -layer.recordedPadPx) {
                        drawLayer(layer.blurLayer)
                    }
                }
            }
        }
}
