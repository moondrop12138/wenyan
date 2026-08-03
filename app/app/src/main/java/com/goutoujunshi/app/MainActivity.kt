package com.goutoujunshi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.goutoujunshi.app.ui.navigation.AppRoot
import com.goutoujunshi.app.ui.navigation.rememberViewModel
import com.goutoujunshi.app.ui.theme.GtjTheme

/**
 * 入口：只装配 Navigation/主题，零业务逻辑（code-organization 硬规则 4）。
 * 容器来自 GoutoujunshiApp（联调时替换为后端真实 AppContainer）。
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as GoutoujunshiApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
