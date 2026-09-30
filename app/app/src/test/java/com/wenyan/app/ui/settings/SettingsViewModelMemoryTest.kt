package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.TargetUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * v1.7.2 SettingsViewModel「记忆」分组状态装配测试（fake SettingsRepository）：
 * 列表装配 / 激活+Toast / 新建自动激活 / 删除回退 / 自动记忆开关。
 * F52 精简：删除「updateTarget delegates and closes edit dialog」——该用例验证的是 v1.7.3
 * 已移除的编辑弹窗死链路（唯一调用方），随 editTarget 状态机一并移除。
 * F77 精简：私有 FakeSettingsRepository 副本删除，改用同包共享 [FakeSettingsRepository]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelMemoryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `targets collected from repository`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true, factCount = 3),
            )
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        assertEquals(2, vm.targets.value.size)
        assertEquals(true, vm.targets.value[1].isActive)
        // v1.7.3-fix：事实数字段透传到 VM（设置页 caption 展示「已记住 3 条」）
        assertEquals(3, vm.targets.value[1].factCount)
    }

    @Test
    fun `setActiveTarget delegates and emits toast`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(TargetUi(1, "小A", "", 0, isActive = false))
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setActiveTarget(TargetUi(1, "小A", "", 0, isActive = false))
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.activated)
        assertEquals("已切换到「小A」的记忆", vm.toastMessage.value)
    }

    @Test
    fun `createTarget auto activates when none active`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.createTarget("小A")
        advanceUntilIdle()
        assertEquals(listOf("小A"), repo.created)
        assertEquals(1L, repo.activeFlow.value)
    }

    @Test
    fun `delete active target falls back to first remaining`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true),
            )
            activeFlow.value = 2L
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.deleteTarget(2L)
        advanceUntilIdle()
        assertEquals(listOf(2L), repo.deleted)
        assertEquals(1L, repo.activeFlow.value)
    }

    @Test
    fun `delete non-active target keeps active unchanged`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(
                TargetUi(1, "小A", "", 0, isActive = false),
                TargetUi(2, "小B", "", 0, isActive = true),
            )
            activeFlow.value = 2L
        }
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.deleteTarget(1L)
        advanceUntilIdle()
        assertEquals(2L, repo.activeFlow.value)
    }

    @Test
    fun `memory auto toggle collected and delegated`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        assertEquals(true, vm.memoryAutoEnabled.value)
        vm.setMemoryAutoEnabled(false)
        advanceUntilIdle()
        assertEquals(false, repo.memoryAutoFlow.value)
        assertEquals(false, vm.memoryAutoEnabled.value)
    }

    @Test
    fun `consumeToast clears message`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setActiveTarget(TargetUi(1, "小A", "", 0, isActive = false))
        advanceUntilIdle()
        assertEquals("已切换到「小A」的记忆", vm.toastMessage.value)
        vm.consumeToast()
        assertNull(vm.toastMessage.value)
    }
}
