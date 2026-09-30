package com.wenyan.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel

/**
 * 极简 ViewModel 持有（替代 lifecycle-viewmodel-compose 的 viewModel()）。
 * 基于宿主 ViewModelStore（ComponentActivity 实现 ViewModelStoreOwner），
 * 配置变更后 ViewModel 保留；联调如引入 lifecycle-viewmodel-compose 可无缝替换。
 *
 * 注意（F07）：自研 Crossfade 导航没有 NavBackStackEntry per-entry scope，页面出栈只是离开
 * 组合、VM 仍留在 Activity store（lifecycle 无公开 per-key remove，无法逐 key 驱逐）——
 * 因此按实体 key 的页面（MemoryEdit/ProviderEdit）一律改用 [rememberEphemeralViewModel]，
 * 只有无页面栈语义的单例页面（App/Chat/Settings/Onboarding）才用本函数。
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
        // get/put 标注了 RestrictedApi（仅限 lifecycle 库组内调用），此处是受控用法：
        // 宿主 Activity 的 ViewModelStore + 自定义 key，行为与官方 viewModel(key) 一致
        @Suppress("UNCHECKED_CAST", "RestrictedApi")
        store[key] as? VM ?: create().also { store.put(key, it) }
    }
}

/**
 * 页面级 ViewModel：进入组合时创建、离开组合即弃（viewModelScope 取消）。
 *
 * 与 [rememberViewModel] 的差异：不放进宿主 Activity 的 ViewModelStore——
 * - F07 修复：store 只放不取删且无任何驱逐路径，`MemoryEdit_$targetId` / `ProviderEdit_$providerId`
 *   这类按实体 key 的 VM 随使用无限累积，且 init 里常驻的 Room/DataStore 收集
 *   （InvalidationTracker 观察者）永不停止；改为页面作用域后，卸载即取消收集，
 *   VM 对象失去引用后由 GC 回收；
 * - F46 修复：固定 key + store 复用会让第二次点「添加提供商」复用上一轮 VM——
 *   旧草稿（含上次 API Key 明文）原样预填，遗留 persistedId 还会让「测试连接」静默
 *   改写上一轮落库的行。改为每次 push 创建全新 VM 后该复用路径不复存在。
 *
 * 取舍：本页 VM 不再跨配置变更保留——自研 AppNavigator 栈同样不跨配置变更
 * （remember { AppNavigator() }，旋转后整体回到栈根 Chat），编辑页状态本就不会
 * 在旋转后继续展示，故无可观察的行为回退。
 */
@Composable
fun <VM : ViewModel> rememberEphemeralViewModel(
    key: String,
    create: () -> VM,
): VM {
    val vm = remember(key) { create() }
    DisposableEffect(key) {
        onDispose {
            // 停掉 init 常驻收集与在途任务（Room InvalidationTracker 观察者随收集结束注销）
            vm.viewModelScope.cancel()
        }
    }
    return vm
}
