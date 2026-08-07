package com.wenyan.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.wenyan.app.ui.navigation.AppRoot
import com.wenyan.app.ui.navigation.rememberViewModel
import com.wenyan.app.ui.theme.GtjTheme

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
            GtjTheme(themeMode = themeMode) {
                AppRoot(container = container, appViewModel = appViewModel)
            }
        }
    }
}
