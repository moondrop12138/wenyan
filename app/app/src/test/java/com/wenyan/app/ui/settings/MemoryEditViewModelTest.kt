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
import org.junit.Before
import org.junit.Test

/**
 * v1.7.4 MemoryEditViewModel 前置搬移测试（fake SettingsRepository）：
 * 打开档案详情页 init 即触发 ensureMigrated（老 note 数据不再因手工加事实而丢失）。
 * F77 精简：私有 FakeSettingsRepoForMemoryEdit 副本删除，改用同包共享 [FakeSettingsRepository]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryEditViewModelTest {

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
    fun `init triggers ensureMigrated for target`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(TargetUi(1, "小A", "她喜欢猫", 0, isActive = false))
        }
        val vm = MemoryEditViewModel(repo, targetId = 1L)
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.ensureMigratedCalls)
        assertEquals("小A", vm.target?.name)
    }
}
