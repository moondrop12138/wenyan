package com.wenyan.app.ui.settings

import com.wenyan.app.ui.contract.LlmError
import com.wenyan.app.ui.contract.MemoryFactUi
import com.wenyan.app.ui.contract.ModelInfo
import com.wenyan.app.ui.contract.ProviderInfo
import com.wenyan.app.ui.contract.SettingsRepository
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.contract.UsageMetricsUi
import com.wenyan.app.ui.theme.BG_BRIGHTNESS_DEFAULT
import com.wenyan.app.ui.theme.FLUID_HUE_DEFAULT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val repo = FakeAppearanceRepo()
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
        val repo = FakeAppearanceRepo()
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
        val repo = FakeAppearanceRepo()
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
        val repo = FakeAppearanceRepo()
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
        val repo = FakeAppearanceRepo()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setBgBrightness(20)
        assertEquals(20, vm.bgBrightness.value)
        advanceUntilIdle()
        assertEquals(listOf(20), repo.brightnessWrites)
    }

    @Test
    fun `slider writes are independent per setting`() = runTest(dispatcher) {
        val repo = FakeAppearanceRepo()
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
        val repo = FakeAppearanceRepo()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(45)
        advanceTimeBy(59) // 合并窗口未到
        assertEquals(emptyList<Int>(), repo.hueWrites)
        advanceTimeBy(2)   // 越过 60ms 合并窗口
        assertEquals(listOf(45), repo.hueWrites)
    }

    @Test
    fun `persisted value survives further local edits order`() = runTest(dispatcher) {
        val repo = FakeAppearanceRepo()
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()
        vm.setFluidHue(90)
        advanceUntilIdle()
        repo.fluidHueFlow.value = 90 // DataStore 回流（写入后的实测值）
        advanceUntilIdle()
        vm.setFluidHue(180)
        advanceUntilIdle()
        assertEquals(180, vm.fluidHue.value)
        assertEquals(listOf(90, 180), repo.hueWrites)
    }
}

/**
 * 内存 SettingsRepository（镜像 RealSettingsRepository 的流光可调契约：DataStore 直通 + 记录写入）。
 * 与既有测试的 Fake 同构；本次新增的 fluidHue/bgBrightness 覆盖实现并记录写调用。
 */
private class FakeAppearanceRepo : SettingsRepository {

    val fluidHueFlow = MutableStateFlow(FLUID_HUE_DEFAULT)
    val bgBrightnessFlow = MutableStateFlow(BG_BRIGHTNESS_DEFAULT)
    val hueWrites = mutableListOf<Int>()
    val brightnessWrites = mutableListOf<Int>()

    override val fluidHue: Flow<Int> = fluidHueFlow
    override val bgBrightness: Flow<Int> = bgBrightnessFlow

    override suspend fun setFluidHue(degrees: Int) {
        hueWrites.add(degrees)
        fluidHueFlow.value = degrees
    }

    override suspend fun setBgBrightness(value: Int) {
        brightnessWrites.add(value)
        bgBrightnessFlow.value = value
    }

    // ===== 以下为契约其余成员的最小桩（本测试不触达）=====

    override val providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    override val models = MutableStateFlow<List<ModelInfo>>(emptyList())
    override val currentModelId = MutableStateFlow<Long?>(null)
    override val visionModelId = MutableStateFlow<Long?>(null)
    override val themeMode = MutableStateFlow("system")
    override val privacyAck = MutableStateFlow(false)
    override val targets: Flow<List<TargetUi>> = MutableStateFlow(emptyList())
    override val activeTargetId: Flow<Long?> = MutableStateFlow(null)
    override val memoryAutoEnabled: Flow<Boolean> = MutableStateFlow(true)
    override val fluidBackgroundEnabled: Flow<Boolean> = MutableStateFlow(true)

    override fun observeFacts(targetId: Long): Flow<List<MemoryFactUi>> = MutableStateFlow(emptyList())
    override suspend fun addFact(targetId: Long, text: String) = Unit
    override suspend fun updateFact(factId: Long, text: String) = Unit
    override suspend fun deleteFact(factId: Long) = Unit
    override suspend fun makePermanent(factId: Long) = Unit
    override suspend fun undoLastMemoryWrite(): List<Long> = emptyList()
    override suspend fun updateTargetDetails(
        id: Long, name: String, mbti: String?, score: Int?, relationStatus: String?, timelineJson: String,
    ) = Unit

    override suspend fun setCurrentModel(id: Long) = Unit
    override suspend fun setVisionModel(id: Long) = Unit
    override suspend fun setThemeMode(mode: String) = Unit
    override suspend fun setFluidBackgroundEnabled(enabled: Boolean) = Unit
    override suspend fun testConnection(providerId: Long): LlmError? = null
    override suspend fun saveProvider(name: String, baseUrl: String, apiKey: String, isPreset: Boolean): Long = 0L
    override suspend fun updateProvider(id: Long, name: String, baseUrl: String, apiKey: String?) = Unit
    override suspend fun deleteProviderApiKey(providerId: Long) = Unit
    override suspend fun deleteProvider(id: Long) = Unit
    override suspend fun getProviderApiKey(providerId: Long): String? = null
    override suspend fun addModel(providerId: Long, name: String, supportsVision: Boolean) = Unit
    override suspend fun deleteModel(id: Long) = Unit
    override suspend fun toggleSheetVisible(id: Long) = Unit
    override suspend fun setVisionFlag(id: Long, supportsVision: Boolean) = Unit
    override suspend fun markConnectionStatus(providerId: Long, ok: Boolean) = Unit
    override suspend fun wipeAll() = Unit
    override suspend fun setPrivacyAck(ack: Boolean) = Unit
    override suspend fun createTarget(name: String): Long = 0L
    override suspend fun updateTarget(id: Long, name: String, note: String) = Unit
    override suspend fun deleteTarget(id: Long) = Unit
    override suspend fun ensureMigrated(targetId: Long) = Unit
    override suspend fun setActiveTarget(id: Long) = Unit
    override suspend fun setMemoryAutoEnabled(enabled: Boolean) = Unit
    override suspend fun exportCrashLog(): android.net.Uri? = null
    override suspend fun checkUpdate(): com.wenyan.app.data.update.UpdateCheckResult =
        com.wenyan.app.data.update.UpdateCheckResult.UpToDate
    override suspend fun downloadUpdateApk(info: com.wenyan.app.data.update.UpdateInfo): java.io.File? = null
    override suspend fun installApk(file: java.io.File): Boolean = false
    override suspend fun importBackup(uri: android.net.Uri): Pair<Boolean, String> = false to "测试未实现"
    override suspend fun exportMemoryJson(): String? = null
    override suspend fun writeMemoryExport(uri: android.net.Uri, json: String): Boolean = false
    override suspend fun importMemoryMerge(uri: android.net.Uri): Pair<Boolean, String> = false to "测试未实现"
    override fun usageMetrics(): UsageMetricsUi = UsageMetricsUi(0L, 0L, 0L, 0L, emptyMap())
}
