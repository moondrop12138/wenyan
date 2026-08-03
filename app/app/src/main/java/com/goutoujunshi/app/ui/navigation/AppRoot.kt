package com.goutoujunshi.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.goutoujunshi.app.AppViewModel
import com.goutoujunshi.app.ui.chat.ChatScreen
import com.goutoujunshi.app.ui.contract.AppContainer
import com.goutoujunshi.app.ui.onboarding.OnboardingScreen
import com.goutoujunshi.app.ui.settings.ProviderEditScreen
import com.goutoujunshi.app.ui.settings.SettingsScreen

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
    // 拦截系统返回键/手势：栈内还有页面时回退到上一页，而不是直接退出应用。
    // 仅当栈底（Chat / Onboarding）时才放行系统默认行为（退出）。
    BackHandler(enabled = navigator.stack.size > 1) {
        navigator.pop()
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
