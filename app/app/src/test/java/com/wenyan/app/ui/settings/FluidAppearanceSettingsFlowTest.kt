package com.wenyan.app.ui.settings

import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * v1.9.4 三改 流光可调（fluid_hue / bg_brightness）设置流测试：
 * DataStore 两个新 key 的「仓库 Flow ↔ SettingsViewModel」装配链路 + 滑条写盘合并。
 *
 * 覆盖（对照 SettingsViewModelMemoryTest 的既有风格）：
 * - 默认值（0 / 50）随 init collect 装配进 VM；
 * - 仓库侧变化回流进 VM（设置页滑条显示的是落盘值，不是本地猜测）；
 * - 滑条写入委托 repo、本地即时更新、且**合并为一次落盘**（拖拽不产生数百次 DataStore 写）。
 * F77 精简：私有 FakeAppearanceRepo 副本删除，改用同包共享 [FakeSettingsRepository]。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidAppearanceSettingsFlowTest {

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
    fun `defaults are collected from repository`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        assertEquals(FLUID_HUE_DEFAULT, vm.fluidHue.value)
        assertEquals(BG_BRIGHTNESS_DEFAULT, vm.bgBrightness.value)
        // 主题原色（0）与中点（50）就是「不可调版本」的观感，滑条初值必须落在中点上
        assertEquals(0, vm.fluidHue.value)
        assertEquals(50, vm.bgBrightness.value)
    }

    @Test
    fun `repository changes flow into view model`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        repo.fluidHueFlow.value = 210
        repo.bgBrightnessFlow.value = 80
        advanceUntilIdle()
        assertEquals(210, vm.fluidHue.value)
        assertEquals(80, vm.bgBrightness.value)
    }

    @Test
    fun `setFluidHue updates local state immediately then persists once`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(120)
        // 合并窗口内：本地已更新（滑条零延迟），尚未落盘（拖拽不逐像素写 DataStore）
        assertEquals(120, vm.fluidHue.value)
        assertEquals(emptyList<Int>(), repo.hueWrites)
        advanceUntilIdle()
        assertEquals(listOf(120), repo.hueWrites)
    }

    @Test
    fun `rapid hue drags collapse into a single write`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        // 模拟一次拖拽：连续 40 次 onValueChange（每次移动都触发），只应落最后一笔
        (0..40).forEach { vm.setFluidHue(it * 5) }
        assertEquals(200, vm.fluidHue.value)
        advanceUntilIdle()
        assertEquals(listOf(200), repo.hueWrites)
    }

    @Test
    fun `setBgBrightness updates local state immediately then persists once`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setBgBrightness(20)
        assertEquals(20, vm.bgBrightness.value)
        advanceUntilIdle()
        assertEquals(listOf(20), repo.brightnessWrites)
    }

    @Test
    fun `slider writes are independent per setting`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(300)
        vm.setBgBrightness(70)
        advanceUntilIdle()
        assertEquals(listOf(300), repo.hueWrites)
        assertEquals(listOf(70), repo.brightnessWrites)
    }

    @Test
    fun `merge window emits before the slider stalls`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(45)
        advanceTimeBy(59) // 合并窗口未到
        assertEquals(emptyList<Int>(), repo.hueWrites)
        advanceTimeBy(2)   // 越过 60ms 合并窗口
        assertEquals(listOf(45), repo.hueWrites)
    }

    @Test
    fun `slider writes persist in order and later edit wins`() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(90)
        advanceUntilIdle()
        // F76 修复：删除原第 142 行 `repo.fluidHueFlow.value = 90`——此时 Fake 的 setFluidHue(90)
        // 已把 Flow 置为 90，等值赋值对 StateFlow 零发射，注释声称的「DataStore 回流」步骤是
        // 死操作（回流场景实际由 Fake 的 setFluidHue 写入时真实覆盖，且断言对其不敏感）
        advanceUntilIdle()
        vm.setFluidHue(180)
        advanceUntilIdle()
        assertEquals(180, vm.fluidHue.value)
        assertEquals(listOf(90, 180), repo.hueWrites)
    }
}
