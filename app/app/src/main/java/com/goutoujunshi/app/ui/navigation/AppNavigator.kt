package com.goutoujunshi.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 轻量导航（Spec 未锁定 navigation-compose，UI 边界内自研）：
 * 返回栈 + 响应式 current，满足"MainActivity 只装配"与页面路由（SPEC §7）。
 */
sealed interface Route {
    data object Onboarding : Route
    data object Chat : Route
    data object Settings : Route
    data class ProviderEdit(val providerId: Long) : Route
}

class AppNavigator(initial: Route) {
    var stack by mutableStateOf(listOf(initial))
        private set

    val current: Route get() = stack.last()

    fun push(route: Route) {
        stack = stack + route
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun replaceAll(route: Route) {
        stack = listOf(route)
    }
}
