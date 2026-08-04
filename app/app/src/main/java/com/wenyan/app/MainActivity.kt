package com.wenyan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
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
