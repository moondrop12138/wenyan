package com.wenyan.app.ui.navigation

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.wenyan.app.AppViewModel
import com.wenyan.app.ui.chat.ChatScreen
import com.wenyan.app.ui.contract.AppContainer
import com.wenyan.app.ui.onboarding.OnboardingScreen
import com.wenyan.app.ui.settings.ProviderEditScreen
import com.wenyan.app.ui.settings.SettingsScreen

/**
 * 根路由装配（零业务）：依据首启问卷完成态决定初始页，按 AppNavigator 返回栈渲染。
 * 转场 Crossfade 200ms 收敛（design-tokens motion.base）。
 */
@Composable
fun AppRoot(
    container: AppContainer,
    appViewModel: AppViewModel,
) {
    val onboardingCompleted by appViewModel.onboardingCompleted.collectAsState()
    val navigator = remember {
        AppNavigator(if (onboardingCompleted) Route.Chat else Route.Onboarding)
    }
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted && navigator.current == Route.Onboarding) {
            navigator.replaceAll(Route.Chat)
        }
    }
    // 拦截系统返回键/手势：栈内还有页面时回退到上一页；
    // v1.7.1-4：栈底（主页面）时两次确认才退出（防边缘滑退误触）——2 秒内再次返回才退出
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (navigator.stack.size > 1) {
            navigator.pop()
        } else {
            val now = SystemClock.uptimeMillis()
            if (now - lastBackAt < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackAt = now
                Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Crossfade(targetState = navigator.current, label = "nav") { route ->
        when (route) {
            Route.Onboarding -> OnboardingScreen(
                container = container,
                onDone = { navigator.replaceAll(Route.Chat) },
            )
            Route.Chat -> ChatScreen(
                container = container,
                onOpenSettings = { navigator.push(Route.Settings) },
            )
            Route.Settings -> SettingsScreen(
                container = container,
                onBack = { navigator.pop() },
                onEditProvider = { id -> navigator.push(Route.ProviderEdit(id)) },
            )
            is Route.ProviderEdit -> ProviderEditScreen(
                container = container,
                providerId = route.providerId,
                onBack = { navigator.pop() },
            )
        }
    }
}
