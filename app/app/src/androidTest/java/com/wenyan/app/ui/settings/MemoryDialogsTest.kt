package com.wenyan.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7.3 T2 记忆弹窗交互 androidTest：
 * 新建（空白禁用 / 输入启用 + 回调）/ 删除确认（取消 / 确认）。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 * F06 修复：补「删除」确认路径用例——原先只点「取消」，confirmButton 误接 onDismiss 也照样全绿。
 */
@RunWith(AndroidJUnit4::class)
class MemoryDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nameDialog_blankDisabled_confirmCallsBack() {
        var confirmed: String? = null
        compose.setContent {
            GtjTheme {
                MemoryNameDialog(onDismiss = {}, onConfirm = { confirmed = it })
            }
        }
        compose.onNodeWithText("创建").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("小A")
        compose.onNodeWithText("创建").performClick()
        assertTrue(confirmed == "小A")
    }

    @Test
    fun deleteDialog_confirmCallsBack_dismissDoesNot() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            GtjTheme {
                MemoryDeleteDialog(
                    targetName = "小A",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }
        compose.onNodeWithText("删除后「小A」的记忆将无法恢复，确定删除？", substring = true).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertTrue(!confirmed)
    }

    @Test
    fun deleteDialog_confirmButton_invokesOnConfirmOnly() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            GtjTheme {
                MemoryDeleteDialog(
                    targetName = "小A",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }
        // F06 修复：弹窗层直接验证确认路径——点「删除」必须触发 onConfirm 且不触发 onDismiss
        //（若 confirmButton 误接成 onDismiss，本用例即红）
        compose.onNodeWithText("删除后「小A」的记忆将无法恢复，确定删除？", substring = true).assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()
        assertTrue(confirmed)
        assertTrue(!dismissed)
    }
}
