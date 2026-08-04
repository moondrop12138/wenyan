package com.wenyan.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner

/**
 * 极简 ViewModel 持有（替代 lifecycle-viewmodel-compose 的 viewModel()）。
 * 基于宿主 ViewModelStore（ComponentActivity 实现 ViewModelStoreOwner），
 * 配置变更后 ViewModel 保留；联调如引入 lifecycle-viewmodel-compose 可无缝替换。
 */
@Composable
fun <VM : ViewModel> rememberViewModel(
    key: String,
    create: () -> VM,
): VM {
    val context = LocalContext.current
    val owner = remember(context) {
        context as? ViewModelStoreOwner
            ?: error("Host is not a ViewModelStoreOwner: ${context::class.java.name}")
    }
    return remember(owner, key) {
        val store = owner.viewModelStore
        @Suppress("UNCHECKED_CAST")
        store[key] as? VM ?: create().also { store.put(key, it) }
    }
}
