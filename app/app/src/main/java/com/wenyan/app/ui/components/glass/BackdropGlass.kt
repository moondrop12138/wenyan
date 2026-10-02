package com.wenyan.app.ui.components.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import kotlin.math.roundToInt

/**
 * v1.9.4 真实背景模糊（backdrop blur）· v1.9.4 对齐桌面**增强 Mica** 配方
 * （生效值：`backdrop-filter: blur(var(--wy-glass-blur)) saturate(170%)`，styles.css:517-518）。
 * v1.9.4 收尾：模糊半径锁死 [com.wenyan.app.ui.theme.GLASS_BLUR_DEFAULT]（100dp，设置页
 * 「玻璃模糊度」滑条已移除，= web 同名 CSS 变量 --wy-glass-blur 的安卓对应值；web 滑条
 * 0-60px/运行时默认 4px 仅注释对照 app.js:1069/73/130。超平台单次模糊上限（MAX_BLUR_PX）的
 * 段由降采样放大渲染（blurRecordScale）保证渲染）。
 *
 * 根因④：v1.8.0 玻璃本体只是半透明填充，无真实背景模糊——安卓端缺失 backdrop-filter。
 * 本文件用 Compose GraphicsLayer（BOM 2025.06.01 / ui 1.8）自研实现，零第三方库。
 *
 * v1.9.4 修复（栏模糊静默失效）· **design J：record+draw 同分发**——
 * 真机（小米 HyperOS/Android 12+）与模拟器（API 35）一致实证：旧构造「玻璃 onDrawBehind 内
 * recordBlur 取样 contentLayer 再画」恒出空层（栏后文字清晰、模糊档位间零像素差）。根因是
 * Compose ui 1.8.3 的 rememberGraphicsLayer 层语义（探针逐项实证）：
 * ① 层的 record 与 drawLayer **不在同一次 draw 分发**时绘制静默不出图——
 *    GraphicsLayer.draw$ui_graphics_release 发现 display list 失效会按存档 drawBlock 重录
 *    （recreateDisplayListIfNeeded→recordInternal，异常**吞掉**），而含 drawContent() 的块
 *    在原分发外重放必然失败 → 空层；
 * ② 嵌套 drawLayer（某层 record 块内再画另一 remember 层）恒缺内容——无论源层新鲜与否；
 * ③ 只有「同分发内 record → 直接绘制」稳定出图（GraphicsLayerV29.draw = 画布
 *    drawRenderNode，无重放路径）。
 * 据此两阶段重排：
 * - 悬浮栏全量磨砂（[GlassBackdrop.barLayers] + [GlassBackdropLayer.recordBlurUnderlay]）：
 *   **内容节点宿主**（[glassBackdropContent]）在同一次 draw 分发内逐栏 record+draw——内容子树
 *   经 drawContext.canvas 换靶直接画进模糊层（无嵌套层），模糊层再以 saveLayer(饱和度) +
 *   栏圆角 clip 垫画到玻璃之下（内容节点先于 Scaffold 栏绘制，z 序天然正确）；玻璃节点
 *   零绘制。饱和度拆出为独立 saveLayer paint（构造加固：blur 与 saturate 单层单效果）。
 *   v1.9.4 评审删除 contentLayer（record+回画零消费者，见 [glassBackdropContent] 注释）。
 * - 卡片透光磨砂（[GlassBackdropLayer.recordBlur]）：record 与 draw 仍同在卡片自身 draw
 *   分发内（满足③），维持既有形态。
 *
 * 性能（v1.8.1 B4 教训修订）：
 * - 滚动帧：逐栏 underlay 取样 = 内容子树经 drawContent() 每栏重画一遍（LazyColumn 只画
 *   可见项，可接受；旧「嵌套引用自动取新内容」的零重录设计在本引擎上不可用，见①②）；
 * - contentPosition/barPositionInRoot/barSize 用 snapshot State：onGloballyPositioned 写
 *   （layout 阶段，等值短路），draw 阶段读（观察 → 只触发相关节点重绘，不触发重组）；
 * - 效果对象零分配：BlurEffect/Paint/Path/RectF 均缓存复用（仅模糊半径变化时重建效果）；
 *   例外（评审偏差记录）：GraphicsLayer.record 非 inline，其 lambda 每次重录分配 1 个
 *   闭包小对象——内容层滚动帧、每栏 underlay 帧各一个，非渲染热路径；
 * - 采样余量 samplePadding（= 2×blur 不变式）：模糊输入超出玻璃边界，边缘不褪色（对应 CSS
 *   backdrop-filter 采样元素外内容的语义）。
 *
 * 降级：API < 31（RenderEffect 门槛）时 [rememberGlassBackdrop] 返回 null，所有相关
 * modifier 直接 no-op → 调用方垫 [glassFogAlpha] 雾化兜底（√ 曲线加浓：默认最低/默认档
 * 100dp ≈ 0.41（√(100/500)×0.92，与旧 12/60 同比例）、满档 0.92，肉眼明显可见；设置页
 * SettingsScreen.kt「玻璃」卡状态行同步声明）。
 * v1.9.4 探测降级为观测：能力探测不再充当行为门禁——API 31+ 恒创建 backdrop 垫真实模糊
 * （唯一行为判定 = [glassRenderMode] 纯函数），[GlassBlurCapabilityProbe] 只在真实渲染
 * 管线上验证构造并把结论送到 logcat + 设置页状态行，不驱动雾化或任何玻璃行为。
 * 范围（v1.9.4 扩展）：
 * - 悬浮栏全量磨砂：ChatScreen 顶栏/输入栏（liquidGlass 的 backdropLayer 显式接入，
 *   [GlassBackdrop.barLayers] 注册，underlay 由内容层宿主绘制；取样内容子树——栏后消息
 *   穿透被磨砂，对齐桌面 Mica；流光背景为低频渐变不进采样，模糊前后视觉近似）；
 * - 卡片透光磨砂（v1.9.4 新增）：Chat/Settings/Onboarding/ProviderEdit/MemoryEdit 各页
 *   record 自己的「流光背景层」并 [LocalGlassBackdrop] provide，全部 GlassSurface/liquidGlass
 *   内部自动消费——卡片之下垫真实高斯模糊（半径 = 锁死的 GLASS_BLUR_DEFAULT 100dp，
 *   [GlassBackdropParams.cardBlurRadius] 仅作默认值/文档），
 *   卡片自反馈/自重影经「取样纯背景层」结构性消除
 *   （见 [GlassBackdropLayer.recordBlur] 注释），未接入页面 null 自动回退半透明。
 *
 * 与抽屉模糊（ChatScreen 根 Box 的整层 BlurEffect(18f)）共存：抽屉 blur 挂在更外层的
 * graphicsLayer 上，作用于整棵子树（含本组件的模糊结果），两层离屏效果叠加，无冲突。
 */

/**
 * 玻璃 backdrop 数值参数（默认值唯一来源 docs/design-tokens.json component.glassBackdrop，
 * 已与代码同步 100/200；运行时半径 = component.glassAppearance 的 blurDefaultDp 100dp
 * 锁死值，见 [recordBlur]）。
 */
object GlassBackdropParams {
    /**
     * 悬浮栏模糊半径（= 运行时唯一值：GLASS_BLUR_DEFAULT 锁死 100dp，v1.9.4 收尾移除设置页
     * 滑条；见 [recordBlur]。超平台单次模糊上限的段由降采样放大渲染（[blurRecordScale]）。
     * web 滑条 0-60px（app.js:1069）、运行时默认 4px（app.js:73/130），仅注释对照——web 8 类
     * 玻璃元素共用同一个 --wy-glass-blur，卡片与栏不分档）。
     */
    val blurRadius: Dp = 100.dp

    /** 饱和度提升（对应桌面 saturate(170%)，styles.css:517-518 固定值）。 */
    const val SATURATION = 1.7f

    /**
     * 亮度增益（v1.9.4：1.03 → 1，移除 web 没有的增益——增强段 backdrop-filter
     * 只有 blur() saturate() 两项，矩阵仍按 brightness=1 参与构造以保持公式完整）。
     */
    const val BRIGHTNESS = 1f

    /** 模糊采样余量默认值/文档（≈2×blur）：玻璃边缘模糊采到栏外内容，不出现边缘褪色；运行时 = 2×半径。 */
    val samplePadding: Dp = 200.dp

    // ── 卡片透光磨砂（全 App 玻璃卡片升级）──

    /**
     * 卡片级模糊半径**默认值/文档**：与悬浮栏同一个半径（锁死 GLASS_BLUR_DEFAULT 100dp，
     * v1.9.4 收尾移除滑条；web 8 类玻璃元素共用同一个 --wy-glass-blur，卡片与栏不分档）。
     * 性能护栏依然成立：模糊层的 RenderEffect 只在半径变化时重建（[GlassBackdropLayer.recordBlur]）。
     */
    val cardBlurRadius: Dp = 100.dp

    /** 卡片级采样余量默认值/文档（≈2×卡片 blur，运行时随滑条半径 = 2×radius）。 */
    val cardSamplePadding: Dp = 200.dp

    /**
     * 雾化兜底的满量程 alpha（玻璃模糊度滑条拖到满档时；只用于 [glassFogAlpha]）。
     * v1.9.4 修复加浓 0.85→0.92：雾化是 API<31 设备唯一可用的模糊近似（v1.9.4 探测降级
     * 为观测后 31+ 不再落雾化），兜底必须肉眼明显可见。
     * 取 0.92 而非 1：满档仍留 8% 透底，与「磨实」区分。
     */
    const val FOG_ALPHA_MAX = 0.92f

    /**
     * 平台 RenderEffect 模糊半径的**像素硬上限安全档**（玻璃可调二改实测：模拟器 API 35 上
     * 52px 有效、66px 起整个模糊效果被静默丢弃——「滑条拖大不更糊」的最终根因；边界落在
     * ~64px 的 2 的幂两侧，按安全余量取 52）。半径超过它时 [blurRecordScale] 把内容降采样
     * 录进模糊层、画回时放大：层空间模糊半径恒 ≤ 本值，视觉模糊半径 = 滑条值全量程有效。
     * 探测器（[GlassBlurCapabilityProbe]）同构模糊层半径本就在上限内，不受影响。
     */
    const val MAX_BLUR_PX = 52f
}

/**
 * 悬浮栏降采样放大的录制缩放（纯函数，JVM 钉值）：模糊半径像素值超过 [GlassBackdropParams.MAX_BLUR_PX]
 * （平台 RenderEffect 硬上限，见其 KDoc）时返回 s = 上限/半径（<1），否则 1（走既证可靠的 1:1 路径）。
 * 层空间模糊半径 = 半径×s 恒 ≤ 上限；画回时放大 1/s，视觉模糊半径 = 滑条半径全量程有效——
 * 上行重采样本身再叠加磨砂，力度只增不减。
 */
internal fun blurRecordScale(radiusPx: Float, maxPx: Float = GlassBackdropParams.MAX_BLUR_PX): Float =
    if (radiusPx <= maxPx || radiusPx <= 0f) 1f else maxPx / radiusPx

/**
 * 雾化兜底（仅 API < 31，判定唯一来源 [glassRenderMode]；v1.9.4 探测降级为观测后 31+
 * 恒真实模糊、不再落雾）：半径锁死的玻璃模糊值
 * （GLASS_BLUR_DEFAULT 100dp，对齐 web --wy-glass-blur 的安卓对应）在这些场景没有 backdrop
 * 模糊消费方——雾化浓度作为其近似响应：alpha = √(半径/满档) × [GlassBackdropParams.FOG_ALPHA_MAX]。
 * v1.9.4 修复由线性改 √ 曲线并加浓：线性旧默认档只有 0.17（几乎不可见，兜底失效）；
 * √ 曲线抬高低半径区浓度——√(默认/满档)×0.92 ≈ 0.41（默认 100/满档 500，
 * 与旧 12/60 同比例、钉值不变），0dp 恒全透明，单调连续。雾色取玻璃填充基色
 * （frost 版 --glass 的 RGB，alpha 独立于磨砂度派生），故与磨砂度滑条独立叠加、不互相污染。
 * internal（仅模块内 + 单测可见）：同 [buildBackdropColorMatrix] 惯例，测试用生产函数钉值。
 */
internal fun glassFogAlpha(blur: Dp, maxBlur: Dp): Float {
    val max = maxBlur.value
    if (max <= 0f) return 0f // 防御：滑条上限为 0 时无雾可言
    val t = blur.value.coerceIn(0f, max) / max
    return kotlin.math.sqrt(t) * GlassBackdropParams.FOG_ALPHA_MAX
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
 * v1.9.4 design J 收敛为单层：[backgroundLayer] = 纯流光背景（卡片级磨砂取样源，由
 * [glassBackdropBackground] record+draw）；悬浮栏磨砂不再经层取样——[recordBlurUnderlay]
 * 在内容节点分发内经 drawContent() 直接取样（旧 contentLayer 在 design J 后零消费者，
 * v1.9.4 评审删除，见 [glassBackdropContent] 注释）。
 * 两个 position 均为原点的 root 坐标：onGloballyPositioned 写（snapshot 等值短路），
 * draw 侧读（draw 阶段观察 → 位置变化只触发相关节点重绘，不触发重组）。
 */
class GlassBackdrop internal constructor(
    internal val backgroundLayer: GraphicsLayer,
) {
    internal var contentPosition by mutableStateOf(Offset.Zero)
    internal var backgroundPosition by mutableStateOf(Offset.Zero)

    /**
     * [design J] 悬浮栏模糊层注册表：模糊层的 record+draw 一体在内容层宿主节点（
     * glassBackdropContent 的 draw 分发内）执行——Compose ui 1.8 的 rememberGraphicsLayer 层
     * 在「录制分发之外」drawLayer 会静默不出图（draw$ui_graphics_release →
     * recreateDisplayListIfNeeded 的 drawBlock 重放吞异常；嵌套层取样恒缺内容，探针实证），
     * 故取样与绘制都必须发生在同一 draw 分发内。玻璃面零绘制，underlay 天然垫在玻璃填充之下。
     */
    internal val barLayers = mutableListOf<GlassBackdropLayer>()
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
    /** 本玻璃面原点的 root 坐标：onGloballyPositioned 写，draw 侧读（快照等值短路）。 */
    internal var barPositionInRoot by mutableStateOf(Offset.Zero)

    /** 本玻璃面尺寸（px，快照态）：onGloballyPositioned 写——v1.9.4 修复（design J）后 record 在内容层宿主分发内执行，栏尺寸不再能从玻璃 DrawScope 现取，须缓存。v1.9.4 评审修复改快照态：尺寸变化（不伴随位置变化）也触发内容宿主重绘重录（旧普通 var 写入不失效，静止画面下陈旧 record 永久驻留）。 */
    internal var barSize by mutableStateOf(Size.Zero)

    /**
     * 栏形状路径（栏本地坐标，含圆角，快照态）：liquidGlass 的 drawWithCache 构建 fillPath 时写入。
     * v1.9.4 修复（design J）后 underlay 由内容层宿主绘制，圆角裁剪须在该坐标系重建，故缓存。
     * v1.9.4 评审修复改快照态：玻璃节点晚于内容宿主绘制 → 首帧内容宿主取到 null（underlay
     * 兜底 clipRect 到栏矩形，见 [recordBlurUnderlay]），本写入使内容宿主下一帧带圆角重录，
     * 无裁剪首帧不再驻留（旧普通 var 写入不触发任何失效）。
     */
    internal var barShapePath: Path? by mutableStateOf(null)

    /**
     * 最近一次 record 实际使用的采样余量（px，= 2×运行时滑条半径，默认 100dp 档即 200dp）：
     * record 把「玻璃左上角背后的内容」放在层内 (pad,pad)，绘制侧 drawLayer 前须
     * translate(-recordedPadPx) 反向对齐（drawLayer 把层的 (0,0) 画在玻璃 (0,0)）。
     */
    internal var recordedPadPx = 0f
        private set

    /**
     * 最近一次卡片级 record（[recordBlur]）实际使用的降采样缩放（悬浮栏路径在
     * [recordBlurUnderlay] 内自持）：绘制侧 [drawBackdropBlur] 画回时放大 1/s，
     * 与 1:1 路径对齐语义一致（层 (pad·s, pad·s) 落回玻璃 (0,0)）。
     */
    internal var recordedScale = 1f
        private set

    private var cachedRadiusPx = -1f
    private var cachedBlurEffect: BlurEffect? = null

    /** 卡片路径（record/draw 同分发）的饱和度：挂层级 colorFilter（v1.9.4 原语义）。 */
    private val saturateFilter: ColorFilter = ColorFilter.colorMatrix(backdropColorMatrix)

    /** 悬浮栏路径（design J）的饱和度：挂独立 saveLayer Paint——饱和度与模糊层彻底分离（构造加固），blur→saturate 顺序与桌面一致。 */
    private val saturatePaint = android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(backdropColorMatrix.values))
    }
    private val scratchPath = android.graphics.Path()
    private val scratchRect = android.graphics.RectF()

    /** 效果对象缓存：仅半径变化（密度/配置变化/滑条拖动）时重建，其余帧零分配。悬浮栏传 [recordScale] 时缓存键 = 层空间半径（恒 ≤ MAX_BLUR_PX）。 */
    private fun obtainBlurEffect(density: Density, blurRadius: Dp, recordScale: Float = 1f): BlurEffect {
        val blurRadiusPx = with(density) { blurRadius.toPx() } * recordScale
        if (blurRadiusPx != cachedRadiusPx || cachedBlurEffect == null) {
            cachedBlurEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Decal)
            cachedRadiusPx = blurRadiusPx
        }
        return cachedBlurEffect!!
    }

    /**
     * 卡片级磨砂取样（backgroundOnly=true，record/draw 同在卡片自身 draw 分发内执行）：
     * 取样源是 [GlassBackdrop.backgroundLayer]（不引用任何 remember 层/卡片模糊层，单向无环，
     * 防自反馈/自重影）。饱和度按 v1.9.4 原语义挂层级 colorFilter。
     * 采样余量 = 2×半径（保持既有 ≈2×blur 不变式）；效果缓存按半径自愈。
     * v1.9.4 评审修复（卡片接入降采样放大）：与悬浮栏路径（[recordBlurUnderlay]）同构——
     * 运行时滑条半径像素值超平台 RenderEffect 硬上限（[GlassBackdropParams.MAX_BLUR_PX]，
     * 66px 起效果被静默丢弃）时按 [blurRecordScale] 缩小录层、[drawBackdropBlur] 画回放大，
     * 层空间半径恒 ≤ 上限，卡片模糊全量程有效（旧实现半径原样入 BlurEffect，滑条 100dp 起步
     * 后卡片模糊整段被平台丢弃）。
     */
    internal fun recordBlur(
        density: Density,
        layoutDirection: LayoutDirection,
        barSize: Size,
        barPositionInRoot: Offset,
        blurRadius: Dp = GlassBackdropParams.cardBlurRadius,
        samplePadding: Dp = blurRadius * 2,
    ) {
        val radiusPx = with(density) { blurRadius.toPx() }
        val recordScale = blurRecordScale(radiusPx)
        blurLayer.renderEffect = obtainBlurEffect(density, blurRadius, recordScale)
        blurLayer.colorFilter = saturateFilter
        val padPx = with(density) { samplePadding.toPx() }
        recordedPadPx = padPx
        recordedScale = recordScale
        val padded = Size(
            (barSize.width + padPx * 2).coerceAtLeast(1f),
            (barSize.height + padPx * 2).coerceAtLeast(1f),
        )
        val barOrigin = barPositionInRoot - backdrop.backgroundPosition
        val scaledPadded = IntSize(
            (padded.width * recordScale).roundToInt().coerceAtLeast(1),
            (padded.height * recordScale).roundToInt().coerceAtLeast(1),
        )
        blurLayer.record(density, layoutDirection, scaledPadded) {
            scale(recordScale, recordScale, pivot = Offset.Zero) {
                translate(padPx - barOrigin.x, padPx - barOrigin.y) {
                    drawLayer(backdrop.backgroundLayer)
                }
            }
        }
    }

    /**
     * v1.9.4 修复（栏模糊静默失效）· design J：悬浮栏全量磨砂的 **record + draw 一体**，
     * 必须在内容层宿主（[glassBackdropContent]）的 draw 分发内调用。
     *
     * 根因（Compose ui 1.8.3 实测，emulator API 35 与真机一致）：rememberGraphicsLayer 的层
     * 「录制分发之外」经 drawLayer 绘制会静默不出图—— GraphicsLayer.draw$ui_graphics_release
     * 发现 display list 失效时按存档 drawBlock 重录（recreateDisplayListIfNeeded→recordInternal，
     * 异常吞掉），而含 drawContent() 的块在原分发外重放必然失败；且嵌套 drawLayer（层 record
     * 块内再画另一 remember 层）同样静默丢内容（探针 E5/E9 实证：块内直接绘制可见、嵌套内容
     * 恒缺）。旧构造「玻璃 onDrawBehind 内 recordBlur 取样 contentLayer」因此恒出空层。
     *
     * 本函数在内容节点宿主分发内调用（页面内容刚画完、仍在同一次 draw 分发）：
     * ① blurLayer.record：经 drawContext.canvas 换靶把内容子树（[ContentDrawScope.drawContent]）
     *    **直接**画进模糊层录制画布——无嵌套层。取景 = 内容本地坐标平移 (pad − barLocal)，
     *    栏背后那块内容落在层内 (pad,pad)；采样余量 = 2×blur（不变式保持）。
     * ② 同分发内把模糊层垫画到栏区域之下（内容节点先于 Scaffold 栏绘制 → 天然在玻璃填充之下）：
     *    saveLayer(saturatePaint) 包住 clipPath(栏圆角形状)+drawLayer——blur（层 renderEffect）
     *    先作用、saturate（saveLayer paint colorFilter）后作用，与桌面
     *    `backdrop-filter: blur() saturate()` 顺序一致；饱和度与模糊分属两个效果载体（构造加固）。
     *
     * 取样语义说明：源 = 内容子树（消息/列表）——流光背景不进采样（背景层嵌套取样不可用；
     * 低频渐变背景模糊前后视觉近似等价），对齐 web backdrop-filter 的「磨砂消息穿透」主诉求。
     *
     * 性能：效果对象仅半径变化重建（护栏保持）；record 闭包每内容帧每栏 1 个（既有例外惯例）；
     * scratchPath/scratchRect 复用零分配；内容子树每内容帧多画一遍（模糊取样成本，
     * LazyColumn 只画可见项，可接受——换来的是真实可用的 backdrop 模糊）。
     */
    internal fun recordBlurUnderlay(owner: ContentDrawScope, blurRadius: Dp) {
        val density = owner.drawContext.density
        val layoutDirection = owner.drawContext.layoutDirection
        // v1.9.4 玻璃可调二改（降采样放大）：半径像素值超平台 RenderEffect 硬上限（~64px，
        // 实测 52px 有效/66px 起效果被静默丢弃）时，内容以 s<1 缩小录进层、画回时放大 1/s——
        // 层空间模糊半径恒 ≤ MAX_BLUR_PX，视觉模糊半径 = 滑条值全量程有效（上行重采样再叠加磨砂）。
        val radiusPx = with(density) { blurRadius.toPx() }
        val recordScale = blurRecordScale(radiusPx)
        blurLayer.renderEffect = obtainBlurEffect(density, blurRadius, recordScale)

        val padPx = with(density) { (blurRadius * 2).toPx() }
        recordedPadPx = padPx
        val size = barSize
        val padded = Size(
            (size.width + padPx * 2).coerceAtLeast(1f),
            (size.height + padPx * 2).coerceAtLeast(1f),
        )
        val barLocal = barPositionInRoot - backdrop.contentPosition
        val scaledPadded = IntSize(
            (padded.width * recordScale).roundToInt().coerceAtLeast(1),
            (padded.height * recordScale).roundToInt().coerceAtLeast(1),
        )

        // ① record：内容子树直接画进模糊层（canvas 换靶，无嵌套层）；recordScale<1 时
        //    scale(pivot=0,0) 包住原 translate——内容先取景平移再整体缩小进小尺寸层
        blurLayer.record(density, layoutDirection, scaledPadded) {
            scale(recordScale, recordScale, pivot = Offset.Zero) {
                translate(padPx - barLocal.x, padPx - barLocal.y) {
                    val savedCanvas = owner.drawContext.canvas
                    owner.drawContext.canvas = drawContext.canvas
                    try {
                        owner.drawContent()
                    } finally {
                        owner.drawContext.canvas = savedCanvas
                    }
                }
            }
        }

        // ② draw：饱和度 saveLayer → 栏形状 clip → 反向对齐画模糊层（recordScale<1 时先
        //    translate 到栏左上- pad、再 scale(1/s) 把小尺寸层放大回满尺寸，层 (0,0) 仍落在
        //    内容坐标 (barLocal.x - padPx, barLocal.y - padPx)，对齐语义与 1:1 路径一致）
        with(owner) {
            scratchRect.set(barLocal.x - padPx, barLocal.y - padPx, barLocal.x - padPx + padded.width, barLocal.y - padPx + padded.height)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val saveCount = native.saveLayer(scratchRect, saturatePaint)
                val shape = barShapePath
                if (shape != null) {
                    scratchPath.rewind()
                    scratchPath.set(shape.asAndroidPath())
                    scratchPath.offset(barLocal.x, barLocal.y)
                    native.clipPath(scratchPath)
                } else {
                    // 首帧兜底（v1.9.4 评审修复）：形状由玻璃 cache 块在 draw 阶段写入，而玻璃
                    // 晚于内容宿主绘制 → 本帧尚无形状。旧实现直接跳过 clip，把「栏+2×pad」
                    // 整块模糊影像无裁剪盖在内容上且普通 var 不触发失效（静止画面永久驻留）；
                    // 现至少裁到栏矩形本体（几何正确、仅缺圆角），形状快照态写入后下一帧
                    // 即带圆角重录
                    native.clipRect(barLocal.x, barLocal.y, barLocal.x + size.width, barLocal.y + size.height)
                }
                native.translate(barLocal.x - padPx, barLocal.y - padPx)
                if (recordScale < 1f) native.scale(1f / recordScale, 1f / recordScale)
                drawLayer(blurLayer)
                native.restoreToCount(saveCount)
            }
        }
    }
}

/**
 * 创建玻璃 backdrop（内容层+背景层共享 holder）。
 * API < 31（RenderEffect 门槛）返回 null → 所有相关 modifier no-op，回退静态玻璃+雾化。
 *
 * v1.9.4 探测降级为观测：[GlassBlurCapabilityProbe] 不再充当行为门禁——行为判定唯一来源
 * 是 [glassRenderMode] 纯函数，API 31+ **恒创建并返回** backdrop、垫真实高斯模糊（冷启动
 * 「雾化过渡」窗口随之消失，真实模糊立即可用）。探测照常懒触发（供 logcat 与设置页状态行
 * 消费），结论不驱动重组/任何行为；probeAsync 幂等且 FAILED 后永久早退（GlassBlurCapabilityProbe
 * 结论单写），失败结论每进程最多记一次日志。结果进程内缓存，一次探测全 App 生效。
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (glassRenderMode(Build.VERSION.SDK_INT) != GlassRenderMode.REAL_BLUR) return null
    // 观测：探测结论只进 logcat（GlassBlurCapabilityProbe.finish）与设置页「玻璃」卡状态行
    // （glassProbeStatusText 组合期读 state/failure），此处不再读结论、不再门禁任何行为。
    DisposableEffect(Unit) {
        if (GlassBlurCapabilityProbe.cachedResult == null) {
            GlassBlurCapabilityProbe.probeAsync()
        }
        onDispose { }
    }
    val backgroundLayer = rememberGraphicsLayer()
    return remember { GlassBackdrop(backgroundLayer) }
}

/**
 * 创建单个玻璃面的模糊层（顶栏/输入栏/卡片各自 remember 一个，几何独立）。
 * v1.9.4：[backgroundOnly]=true 供卡片级磨砂（liquidGlass 内部自动消费，取样纯背景层），
 * 默认 false 供悬浮栏全量磨砂（经 [liquidGlass] 的 backdropLayer 参数显式接入，取样背景+内容）。
 */
@Composable
fun rememberGlassBackdropLayer(
    backdrop: GlassBackdrop?,
    backgroundOnly: Boolean = false,
): GlassBackdropLayer? {
    if (backdrop == null) return null
    val layer = rememberGraphicsLayer()
    val holder = remember(backdrop) { GlassBackdropLayer(layer, backdrop, backgroundOnly) }
    if (!backgroundOnly) {
        // [design F] 悬浮栏模糊层注册进内容层宿主：record 在内容层 draw 分发内执行（见
        // GlassBackdrop.barLayers 注释）。注册/注销走 DisposableEffect，与玻璃组合生命周期对齐。
        DisposableEffect(backdrop, holder) {
            backdrop.barLayers.add(holder)
            onDispose { backdrop.barLayers.remove(holder) }
        }
    }
    return holder
}

/**
 * v1.9.4 背景侧：把流光背景（FluidBackground 所在铺满节点）record 进背景层后再正常画出。
 * 关键缺口修复：卡片若直接取样页面内容会漏掉流光背景（模糊区域背后没有消息时无内容可磨）。
 * 背景独立成层：卡片磨砂取样它（纯背景，防卡片自反馈/自重影）；悬浮栏磨砂（design J）经
 * drawContent() 取样内容子树、流光背景不进采样（低频渐变模糊前后视觉近似，见
 * recordBlurUnderlay 注释）。record 只在本节点内容失效时执行（流光动画每帧重绘 = 图层失效
 * 机制），record+draw 同分发（满足层引用绘制约束）。
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
 * 内容侧：悬浮栏磨砂 underlay 的宿主节点（[GlassBackdrop.barLayers] 的 record+draw 一体
 * 在本节点 draw 分发内执行——Compose ui 1.8 的 rememberGraphicsLayer 层在「录制分发之外」
 * 绘制会静默不出图（探针实证，见 [GlassBackdrop.barLayers] 注释），故取样与绘制都必须
 * 发生在同一次 draw 分发内；v1.9.4 design J）。本节点自身内容（含流光背景，由
 * [glassBackdropBackground] 独立成层绘制）先正常画出，underlay 随后垫上——underlay 经
 * 栏圆角 clip 只覆盖栏区域（栏区域内以模糊影像替代锐利内容），栏外内容保持原样；
 * Scaffold 栏节点在本节点之后绘制，玻璃填充 z 序天然正确。
 *
 * v1.9.4 评审删除 contentLayer：旧「record 内容子树 → 回画」在 design J 下零消费者
 * （recordBlurUnderlay 经 drawContent() 取样，不再读该层），纯每帧重录+合成开销；且其
 * record 块内嵌套 drawLayer(backgroundLayer) 按根因②恒缺内容，「背景+内容全量」早已失实。
 * 流光背景本就由背景节点独立绘制上屏，无需进内容层。
 *
 * 模糊半径 [blurRadius] 传 GLASS_BLUR_DEFAULT（锁死 100dp）；采样余量运行时 = 2×半径
 * （保持既有 ≈2×blur 不变式）。
 * [GlassBackdrop.contentPosition] 仍由 onGloballyPositioned 写入，供 underlay 把栏 root
 * 坐标换算到本节点内容坐标系。
 */
fun Modifier.glassBackdropContent(backdrop: GlassBackdrop?, blurRadius: Dp = GlassBackdropParams.blurRadius): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.contentPosition = it.positionInRoot() }
        .drawWithContent {
            // [design J] 悬浮栏全量磨砂：本分发内逐栏 record+draw 磨砂 underlay——内容子树
            // （drawContent）经 canvas 换靶直接画进模糊层（recordBlurUnderlay 内），underlay
            // 以栏圆角 clip 垫画到玻璃之下。半径随滑条（modifier 重建即生效）；栏位置/尺寸
            // 为快照态，本 draw 观察到变化即重绘重录。
            this@drawWithContent.drawContent()
            for (barLayer in backdrop.barLayers) {
                barLayer.recordBlurUnderlay(this, blurRadius)
            }
        }
}

/**
 * F43 去重：把「clipPath + translate(-recordedPadPx) 反向对齐 + drawLayer」的磨砂影像绘制
 * 收敛为单一实现——v1.9.4 修复（design J）后仅**卡片透光磨砂**调用点使用（record 与 draw
 * 同在卡片自身 draw 分发内，满足「同分发」约束；悬浮栏 underlay 由内容层宿主绘制，
 * 见 [GlassBackdropLayer.recordBlurUnderlay]）。须在玻璃自身的 onDrawBehind 内、
 * 投影之后 / 玻璃填充之前调用。
 *
 * 层引用绘制约束（Compose ui 1.8.3）：rememberGraphicsLayer 的层在「录制分发之外」经
 * drawLayer 绘制会静默不出图（draw$ui_graphics_release → recreateDisplayListIfNeeded 的
 * drawBlock 重放吞异常 → 空层）；本函数能工作正因调用方在紧邻的 recordBlur（同分发）里
 * 刚录好模糊层。
 * v1.9.4 评审修复（磨砂影像偏移）：record 时玻璃左上角的内容在层内 (pad,pad)，drawLayer 会把
 * 层的 (0,0) 画在玻璃 (0,0)——translate(-pad) 后层内 (pad,pad) 落回玻璃 (0,0)，与 CSS
 * backdrop-filter 对齐。pad 由 [GlassBackdropLayer.recordedPadPx] 缓存（同分发 recordBlur
 * 刚写入本帧值）。
 * v1.9.4 评审修复（卡片降采样放大）：recordBlur 按 [blurRecordScale] 缩小录层时（
 * [GlassBackdropLayer.recordedScale] < 1），画回前 scale(1/s)（pivot=0,0，与悬浮栏路径的
 * native.scale 同序）把小尺寸层放大回满尺寸——层 (pad·s, pad·s) 仍落回玻璃 (0,0)，
 * 对齐语义与 1:1 路径一致。
 */
internal fun DrawScope.drawBackdropBlur(
    layer: GlassBackdropLayer,
    clip: Path,
) {
    clipPath(clip) {
        translate(-layer.recordedPadPx, -layer.recordedPadPx) {
            val s = layer.recordedScale
            if (s < 1f) {
                scale(1f / s, 1f / s, Offset.Zero) {
                    drawLayer(layer.blurLayer)
                }
            } else {
                drawLayer(layer.blurLayer)
            }
        }
    }
}
