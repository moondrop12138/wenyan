package com.wenyan.app.ui.components.glass

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalDensity
import com.wenyan.app.ui.theme.LocalBgBrightness
import com.wenyan.app.ui.theme.LocalFluidBackground
import com.wenyan.app.ui.theme.LocalGtjColors
import com.wenyan.app.ui.theme.LocalGtjIsDark
import com.wenyan.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

// ── 动画/性能参数 ──

/** 30fps 节流：距上一写入帧 <33ms 的帧回调直接跳过（对应桌面 aqua-fluid.js 的 step = 1000/30 ms）。 */
private const val FRAME_INTERVAL_NANOS = 33_000_000L

/** 时间流速：桌面 speed=14 → uTime = 秒数 × 14/100（shader 内部 t = 0.5 × uTime 还会再减半）。 */
private const val TIME_SPEED = 0.14f

/** reducedMotion（系统"移除动画"）下的静态帧：固定 uTime 取中段构图（图案已完全展开）。 */
private const val STATIC_FRAME_TIME = 8f

// ── 桌面 AQUA_FLUID_PARAMS 默认值换算（aqua-fluid.js 5-11 行参数 → 344-367 行 uniform 绑定）──

private const val PARAM_SCALE = 0.5f           // scale 0.5（本身即 0~1 标度，不换算）
// 非 const：const val 初始化器须为编译期常量，保险起见算术表达式用普通 val（每次建 shader 求值一次）
private val PARAM_ROTATION = -5f / 90f         // rotation -5 → /90
private const val PARAM_PROPORTION = 0.5f      // proportion 50 → /100
private const val PARAM_SOFTNESS = 1.0f        // softness 100 → /100
private const val PARAM_SHAPE_SCALE = 0.1f     // shapeScale 10 → /100
private const val PARAM_DISTORTION = 0.24f     // distortion 24 → /100
private const val PARAM_SWIRL = 0.28f          // swirl 14 → /50
private const val PARAM_SWIRL_ITERATIONS = 8f
private const val PARAM_OFFSET_X = 0f          // offsetX 0 → /100
private const val PARAM_OFFSET_Y = 0.4f        // offsetY 40 → /100

/**
 * v1.9.4 流光背景 · AGSL RuntimeShader（桌面 WebGL2 DISPLAY_SHADER 移植，纯观赏版）
 *
 * shader 数学唯一事实源：D:/wenyan/app/desktop/src/main/resources/static/aqua-fluid.js 的
 * DISPLAY_SHADER（62-146 行）。与桌面的差异（均为需求裁剪，不改数学）：
 * - 删除 flow map 采样与全部鼠标交互项：influence 恒 0，
 *   u_flowmap/u_distortBoost/u_noiseBoost/u_swirlBoost 及其分支整段移除；
 * - u_shape/u_colorCount 等死 uniform 内联：colorCount 固定 3（blendMulti 两段 mix 恒生效）。
 *
 * AGSL 语法硬约束（个别 ROM 编译失败的教训，仓库硬规则）：
 * - 禁用 #define（AGSL 无预处理器）→ const float；
 * - 禁用 vec* 与 mat2 → float2/float3/float4/half4，二维旋转手写展开（rotate2d）；
 * - 字面量写全（0.5 而非 .5，1.0 而非 1.）。
 *
 * 坐标对齐：桌面 gl_FragCoord 原点在左下、AGSL fragCoord 原点在左上——
 * uv.y 先翻转（1 - y/h）再进噪声域，后续所有数学与桌面逐行同构，
 * 保证 rotation 与 offsetY=0.4 的视觉朝向和桌面完全一致。
 *
 * 渲染/性能（沿用 GlowBackground 的 v1.8.1 B4 教训：严禁每帧写状态触发重组）：
 * - withInfiniteAnimationFrameNanos 手动驱动 + 30fps 节流（<33ms 跳过不写 state）；
 *   frameNanos 只在 Canvas 的 draw lambda 中被读取 → 只触发重绘，不触发重组；
 * - 每帧仅 3 个 setFloatUniform 调用（uTime/uResolution/uVeil）+ 复用缓存的 ShaderBrush，
 *   零对象分配；基色（未旋转）在 shader 创建时写死，亮度 veil 在 draw 阶段写 uniform；
 *   色相全局跟随（v1.9.4 四改）在 MainActivity 内容根部的 graphicsLayer colorFilter 生效，
 *   本组件不读色相——拖动色相滑条与本组件完全解耦；
 * - swirl 循环用固定 30 次迭代 + step 掩码替代 break（掩码为 0 时增量恒为 0，
 *   与桌面 `if (i > iters) break` 数学完全等价），规避个别 ROM 对动态跳出循环的编译差异。
 *
 * 降级：API < 33（LiquidGlassShaders.isRuntimeShaderSupported()）或 RuntimeShader 构造失败
 * （个别 ROM 抛 IllegalArgumentException，同 createFluidShader 的 runCatching 防御）
 * → 渲染现有 GlowBackground（同样消费亮度 veil，见其文档）；
 * LocalFluidBackground=false 时不画任何内容。
 *
 * 颜色：LocalGtjColors.fluidA/B/C（唯一来源 docs/design-tokens.json color.light/dark.fluid.*，
 * 与桌面 paletteForTheme 完全一致），组件内无硬编码色值。
 * v1.9.4 三改：亮度经 [brightnessVeil]（LocalBgBrightness）写 uVeil，在 shader 末尾
 * `mix(col, uVeil.rgb, uVeil.a)` 一次性叠完（比再叠一层 View 省一次全屏合成）。
 * 默认值（50）下 uVeil.a == 0，输出与不可调版本逐位一致。
 * v1.9.4 四改 色相全局跟随：色相旋转上移到应用内容根部（MainActivity 的全局 graphicsLayer，
 * 对齐 web body 级 filter 语义），本组件改画**未旋转**基色防二次旋转。
 */
@SuppressLint("NewApi") // RuntimeShader 构造已由 LiquidGlassShaders.isRuntimeShaderSupported() 做 API 33 守卫
@Composable
fun FluidBackground(
    modifier: Modifier = Modifier,
) {
    // 设置总开关：关闭时不画任何内容，直接露出主题底色（也不回落光斑层）
    if (!LocalFluidBackground.current) return

    val palette = LocalGtjColors.current
    val reduced = rememberReducedMotion()
    // v1.9.4 四改：色相由 MainActivity 内容根部全局层统一旋转，本组件不再读 LocalFluidHue；
    // 亮度（0-100）/当前深浅主题（决定亮度叠白还是叠黑）照旧
    val bgBrightness = LocalBgBrightness.current
    val isDark = LocalGtjIsDark.current
    // uPixelRatio = 设备密度：uv 在 shader 内先乘分辨率再除以它 → 图案尺度按 dp 归一
    // （对应桌面的 window.devicePixelRatio，aqua-fluid.js 346 行）。
    // 已知取舍（规格钦定，评审提示项）：桌面渲染分辨率封顶 1.5×CSS（aqua-fluid.js:258
    // dprCap，261-262 行）而 pixelRatio 不封顶，故 dpr>1.5 的屏幕上桌面噪声图案更粗、
    // 安卓（density 不封顶，uResolution 传物理 px）相对更细；dpr≤1.5 时两者一致。
    val pixelRatio = LocalDensity.current.density

    // v1.9.4 四改：基色用未旋转 token 原色（色相由全局层统一转，防二次旋转）
    val color1 = palette.fluidA
    val color2 = palette.fluidB
    val color3 = palette.fluidC
    // 亮度 veil（50 = Transparent，零叠加）：alpha 0 时 shader 里的 mix 恒等，观感与不可调版本一致
    val veil = remember(bgBrightness, isDark) { brightnessVeil(bgBrightness, isDark) }

    // RuntimeShader 仅在基色/密度变化时重建（重组级、低频）；动画帧内只改 uniform。
    val shader = remember(palette.fluidA, palette.fluidB, palette.fluidC, pixelRatio) {
        if (!LiquidGlassShaders.isRuntimeShaderSupported()) {
            null
        } else {
            runCatching { createFluidShader(color1, color2, color3, veil, pixelRatio) }.getOrNull()
        }
    }

    // 降级：API < 33 或个别 ROM AGSL 编译失败 → 现有光斑背景
    if (shader == null) {
        GlowBackground(modifier = modifier)
        return
    }

    // Brush 只包一次 RuntimeShader 并缓存；帧内仅改 uniform，不新建对象
    val brush = remember(shader) { ShaderBrush(shader) }

    var frameNanos by remember { mutableLongStateOf(0L) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            var lastEmitted = 0L
            // v1.9.4 评审修复：uTime 起点取首帧（对应桌面 aqua-fluid.js:294 start=performance.now()），
            // 而非开机纪元绝对帧时间——开机数日后 t 达上万，float32 ulp(t) 大于每像素噪声步长，
            // noise(uv+t) 的空间采样会量化成块状伪影且随 uptime 恶化，hashNoise 的 sin 大参数
            // 精度同步劣化；桌面从 0 起算无此问题。
            var startNanos = 0L
            while (currentCoroutineContext().isActive) {
                withInfiniteAnimationFrameNanos { now ->
                    if (startNanos == 0L) startNanos = now
                    if (now - lastEmitted >= FRAME_INTERVAL_NANOS) {
                        lastEmitted = now
                        // 存自挂载起的相对帧时间；draw 阶段才读取该 state → 只触发 Canvas 重绘，不触发重组（v1.8.1 B4）
                        frameNanos = now - startNanos
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        shader.setFloatUniform("uTime", if (reduced) STATIC_FRAME_TIME else frameNanos / 1e9f * TIME_SPEED)
        shader.setFloatUniform("uResolution", w, h)
        // v1.9.4 三改：亮度 veil 每帧写 uniform——保证「设置即时生效」与「reducedMotion 静帧
        // 同样生效」都不依赖任何重建/失效时序；默认 50 下 alpha=0，mix 恒等。
        // uColor1/2/3 基色静态（v1.9.4 四改起未旋转且不随设置变），已在 shader 创建时写入。
        shader.setFloatUniform("uVeil", veil.red, veil.green, veil.blue, veil.alpha)
        drawRect(brush = brush)
    }
}

/**
 * 创建流光 RuntimeShader 并写入全部静态 uniform（uTime/uResolution/uVeil 每帧覆写，
 * uColor1-3 基色静态）。v1.9.4 四改：color1-3 为**未旋转**基色（色相由 MainActivity 内容
 * 根部全局层统一旋转，防二次旋转）、veil 为亮度叠加色（默认 Transparent）。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createFluidShader(
    color1: Color,
    color2: Color,
    color3: Color,
    veil: Color,
    pixelRatio: Float,
): RuntimeShader = RuntimeShader(FLUID_SHADER_SRC).apply {
    setFloatUniform("uResolution", 1f, 1f) // 占位，draw 阶段按实际尺寸覆写
    setFloatUniform("uTime", 0f)
    setFloatUniform("uPixelRatio", pixelRatio.coerceAtLeast(0.1f))
    setFloatUniform("uScale", PARAM_SCALE)
    setFloatUniform("uRotation", PARAM_ROTATION)
    setFloatUniform("uColor1", color1.red, color1.green, color1.blue, color1.alpha)
    setFloatUniform("uColor2", color2.red, color2.green, color2.blue, color2.alpha)
    setFloatUniform("uColor3", color3.red, color3.green, color3.blue, color3.alpha)
    setFloatUniform("uVeil", veil.red, veil.green, veil.blue, veil.alpha)
    setFloatUniform("uProportion", PARAM_PROPORTION)
    setFloatUniform("uSoftness", PARAM_SOFTNESS)
    setFloatUniform("uShapeScale", PARAM_SHAPE_SCALE)
    setFloatUniform("uDistortion", PARAM_DISTORTION)
    setFloatUniform("uSwirl", PARAM_SWIRL)
    setFloatUniform("uSwirlIterations", PARAM_SWIRL_ITERATIONS)
    setFloatUniform("uOffset", PARAM_OFFSET_X, PARAM_OFFSET_Y)
}

/**
 * AGSL 源码：aqua-fluid.js DISPLAY_SHADER（62-146 行）的逐行移植（influence 恒 0 裁剪版）。
 * 命名对应：u_resolution→uResolution、u_time→uTime、……；AGSL 语法约束见 [FluidBackground] 文档。
 */
private const val FLUID_SHADER_SRC = """
uniform float2 uResolution;
uniform float uTime;
uniform float uPixelRatio;
uniform float uScale;
uniform float uRotation;
uniform float4 uColor1;
uniform float4 uColor2;
uniform float4 uColor3;
uniform float uProportion;
uniform float uSoftness;
uniform float uShapeScale;
uniform float uDistortion;
uniform float uSwirl;
uniform float uSwirlIterations;
uniform float2 uOffset;
// v1.9.4 三改：亮度叠加色（rgb + alpha；alpha 0 = 完全不叠 = 默认 50 的观感）
uniform float4 uVeil;

// 桌面 #define TWO_PI / PI（aqua-fluid.js 86-87 行）→ AGSL 用 const
const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530718;

// 桌面 rotate：mat2(cos(th), sin(th), -sin(th), cos(th)) * uv（列主序）
// mat2 在个别 ROM 上编译失败 → 手写展开：col0=(cos,sin)、col1=(-sin,cos)
float2 rotate2d(float2 v, float th) {
    float c = cos(th);
    float s = sin(th);
    return float2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// 桌面 random（90 行）：fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453123)
float hashNoise(float2 st) {
    return fract(sin(dot(st, float2(12.9898, 78.233))) * 43758.5453123);
}

// 桌面 noise（91-96 行）：value noise，四角 hash + smoothstep 双线性插值
float noise(float2 st) {
    float2 cell = floor(st);
    float2 f = fract(st);
    float a = hashNoise(cell);
    float b = hashNoise(cell + float2(1.0, 0.0));
    float c = hashNoise(cell + float2(0.0, 1.0));
    float d = hashNoise(cell + float2(1.0, 1.0));
    float2 w = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
}

// 桌面 blend_multi（98-104 行）：colorCount=3 内联 → 两段 mix 恒生效
float3 blendMulti(float mixer, float softness) {
    float edge = 1.0 - softness;
    float3 col = uColor1.rgb;
    col = mix(col, uColor2.rgb, smoothstep(0.0 + 0.35 * edge, 0.7 - 0.35 * edge, mixer));
    col = mix(col, uColor3.rgb, smoothstep(0.3 + 0.35 * edge, 1.0 - 0.35 * edge, mixer));
    return col;
}

half4 main(float2 fragCoord) {
    // 桌面 gl_FragCoord 原点在左下、AGSL 在左上：先翻转 y 再进噪声域，
    // 保证后续旋转/offset 与桌面视觉朝向一致（fragUV 同值，仅坐标来源不同）
    float2 uv = float2(fragCoord.x / uResolution.x, 1.0 - fragCoord.y / uResolution.y);
    float t = 0.5 * uTime;
    float ns = 0.0005 + 0.006 * uScale;
    uv -= 0.5;
    uv *= ns * uResolution;
    uv = rotate2d(uv, uRotation * 0.5 * PI);
    uv = uv / uPixelRatio;
    uv += 0.5;
    uv += uOffset;

    // 桌面 flow map 采样/flowDir/noiseBoost 分支整段删除：influence 恒 0（纯观赏，无交互）
    // → totalDistortion = uDistortion，swirlAmt = clamp(uSwirl, 0.0, 2.0)
    float n1 = noise(uv + t);
    float n2 = noise(uv * 2.0 - t);
    float angle = n1 * TWO_PI;

    uv.x += 4.0 * uDistortion * n2 * cos(angle);
    uv.y += 4.0 * uDistortion * n2 * sin(angle);

    // 桌面 132-138 行 swirl 循环：固定 30 次迭代 + step 掩码替代 break（数学等价，
    // 掩码为 0 时增量恒为 0）；uv.y 项读取本轮已更新的 uv.x，与桌面语句顺序逐行一致
    float iters = ceil(clamp(uSwirlIterations, 1.0, 30.0));
    float swirlAmt = clamp(uSwirl, 0.0, 2.0);
    for (float i = 1.0; i <= 30.0; i += 1.0) {
        float m = step(i, iters);
        uv.x += m * swirlAmt / i * cos(t + i * 1.5 * uv.y);
        uv.y += m * swirlAmt / i * cos(t + i * 1.0 * uv.x);
    }

    // 桌面 140-145 行：形状场 + 混色
    float proportion = clamp(uProportion, 0.0, 1.0);
    float2 cuv = uv * (0.5 + 3.5 * uShapeScale);
    float shape = 0.5 + 0.5 * sin(cuv.x) * cos(cuv.y);
    float mixer = shape + 0.48 * sign(proportion - 0.5) * pow(abs(proportion - 0.5), 0.5);
    float3 col = blendMulti(mixer, clamp(uSoftness, 0.0, 1.0));
    // v1.9.4 三改 背景亮度：整屏叠一层 veil（对应桌面 --wy-brightness-white/black 的
    // linear-gradient 叠加层）。uVeil.a == 0 时 mix 恒等 → 默认 50 输出与不可调版本逐位一致
    col = mix(col, uVeil.rgb, clamp(uVeil.a, 0.0, 1.0));
    return half4(col, 1.0);
}
"""
