package com.wenyan.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O8: 冷启动 Baseline Profile 采集。
 * 覆盖 App 启动路径（WenyanApp/MainActivity/Compose 首页首帧），
 * CI 由 :benchmark:pixel6Api34BaselineProfile 生成并回写 src/main/baseline-prof.txt。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.wenyan.app",
            includeInStartupProfile = true,
        ) {
            startActivityAndWait()
            // 等待主界面首帧（设置入口出现），确保 Compose 首页热路径被采集
            val device = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .let { androidx.test.uiautomator.UiDevice.getInstance(it) }
            device.wait(Until.hasObject(By.desc("设置")), 5_000)
        }
    }
}
