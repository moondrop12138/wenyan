package com.wenyan.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.contract.TargetUi
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7.3 T2 记忆设置流 androidTest：
 * 添加档案（弹窗）→ 点档案行切换激活 → 编辑图标跳详情页 → 删除二次确认。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 */
@RunWith(AndroidJUnit4::class)
class MemorySettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private fun TargetUi.fact() = this

    @Test
    fun memoryFlow_addActivateEditDelete() {
        val fake = FakeSettingsRepository().apply {
            targetsFlow.value = listOf(TargetUi(id = 1L, name = "小A", note = "", createdAt = 0L, isActive = true))
            activeFlow.value = 1L
        }
        val container = FakeAppContainer(fake)
        var editTargetId: Long? = null

        compose.setContent {
            GtjTheme {
                SettingsScreen(
                    container = container,
                    onBack = {},
                    onEditProvider = {},
                    onEditTarget = { editTargetId = it },
                )
            }
        }

        // 记忆分组档案行渲染
        compose.onNodeWithText("小A").assertIsDisplayed()

        // 添加档案：点「添加记忆」→ 弹窗 → 输入名称 → 创建
        compose.onNodeWithContentDescription("添加记忆").performClick()
        compose.onNodeWithText("档案名称").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("小B")
        compose.onNodeWithText("创建").performClick()
        assertEquals(listOf("小B"), fake.created)

        // 点档案行 → 切换激活
        compose.onNodeWithText("小A").performClick()
        assertEquals(listOf(1L), fake.activated)

        // 编辑图标 → 跳 MemoryEdit 页（替代 v1.7.2 改名弹窗）
        compose.onNodeWithContentDescription("编辑记忆").performClick()
        assertEquals(1L, editTargetId)

        // 删除图标 → 二次确认弹窗 → 确认删除
        compose.onNodeWithContentDescription("删除记忆").performClick()
        compose.onNodeWithText("删除记忆", substring = true).assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        assertTrue(fake.deleted.contains(1L))
    }
}
