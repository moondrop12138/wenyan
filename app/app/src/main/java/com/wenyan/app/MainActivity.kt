package com.wenyan.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.wenyan.app.ui.components.glass.hueRotateColorMatrix
import com.wenyan.app.ui.navigation.AppRoot
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import com.wenyan.app.ui.theme.GtjTheme
import com.wenyan.app.ui.theme.LocalBgBrightness
import com.wenyan.app.ui.theme.LocalFluidBackground
import com.wenyan.app.ui.theme.LocalFluidHue

/**
 * 入口：只装配 Navigation/主题，零业务逻辑（code-organization 硬规则 4）。
 * 容器来自 WenyanApp（联调时替换为后端真实 AppContainer）。
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as WenyanApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+（targetSdk 36）强制 edge-to-edge：统一进入后由各 Scaffold/顶栏/底栏
        // 用 WindowInsets 自适应状态栏/导航栏，禁止写死高度（insets 自适应，不写死值）。
        // v1.6.3 沉浸式手势小白条：scrim 全透明（默认 auto 浅色 scrim≈0xE6FFFFFF 即白条来源），
        // 导航栏区域露出 App 背景色；手势条颜色由系统按背景亮度自动对比（浅底→深灰条/深底→浅条）。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val appViewModel: AppViewModel = rememberViewModel("AppViewModel") {
                AppViewModel(container)
            }
            val themeMode by appViewModel.themeMode.collectAsState()
            // v1.9.4 流光背景开关：AppViewModel 统一收集后全局下发（GtjTheme 块内包 AppRoot，
            // FluidBackground 组件读 LocalFluidBackground，默认 true；关闭 = 不绘制露出主题底色）
            val fluidBackground by appViewModel.fluidBackground.collectAsState()
            // v1.9.4 三改 流光可调：色相（0-360°）与背景亮度（0-100，50 = 不叠 veil）同样全局下发，
            // 默认值（0/50）下输出与不可调版本逐位一致（见 FluidAppearance.kt）
            val fluidHue by appViewModel.fluidHue.collectAsState()
            val bgBrightness by appViewModel.bgBrightness.collectAsState()
            // v1.9.4 玻璃可调：磨砂度经 GtjTheme 的 withGlassFrost 派生玻璃填充 alpha（默认
            // 60 = 磨砂档）；玻璃模糊半径已锁死 GLASS_BLUR_DEFAULT 100dp（v1.9.4 收尾移除滑条，
            // LocalGlassBlur 一并移除），消费方直接读常量
            val glassFrost by appViewModel.glassFrost.collectAsState()
            GtjTheme(themeMode = themeMode, glassFrost = glassFrost / 100f) {
                CompositionLocalProvider(
                    LocalFluidBackground provides fluidBackground,
                    LocalFluidHue provides fluidHue,
                    LocalBgBrightness provides bgBrightness,
                ) {
                    // v1.9.4 四改 色相全局跟随：对齐桌面 styles.css:493 把
                    // `filter: hue-rotate(var(--wy-fluid-hue))` 挂在整个 body 上的语义——
                    // 流光、玻璃、文字整树一起转（此前只转流光基色，UI 层固定色不跟随）。
                    // 矩阵 = W3C 亮度保持 hue-rotate（hueRotateColorMatrix，与旧逐色旋转
                    // 同一条算子）；色相 = 0（默认）直接透传绘制，零开销。
                    // 实现注（javap 实测，非推测）：本项目 Compose BOM 2025.06.01 → ui 1.8.3，
                    // `Modifier.graphicsLayer {}` 的 GraphicsLayerScope 无 colorFilter 成员
                    // （仅 scaleX/alpha/renderEffect/compositingStrategy 等；GraphicsLayer
                    // 类本身有 setColorFilter，但 modifier 作用域未暴露），故用
                    // drawWithContent + saveLayer(paint.colorFilter) 在图层合成时套同一矩阵——
                    // saveLayer 的 paint 滤镜作用于整层合成结果，与「整树套 filter」语义等价。
                    // 全局层是 LocalFluidHue 的消费方（CompositionLocal 下发链路的唯一读取点）。
                    // 已知偏差（web 无此概念）：Compose Dialog/ModalBottomSheet 等独立窗口
                    // 不在本层子树内——主弹层 ModelSheet 已在自己的内容里套同一矩阵跟随
                    // （见 ModelSheet.kt），其余 AlertDialog 仍不跟随。
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
                    Box(
                        modifier = Modifier.fillMaxSize().drawWithContent {
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
                        },
                    ) {
                        AppRoot(container = container, appViewModel = appViewModel)
                    }
                }
            }
        }
    }
}
