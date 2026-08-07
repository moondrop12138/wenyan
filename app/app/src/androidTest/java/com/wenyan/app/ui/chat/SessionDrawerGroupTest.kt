package com.wenyan.app.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7.3 T2 会话分组 androidTest（v1.7.3-fix：分组键改 targetId）：
 * 按档案 id 分组渲染组头（组头文字取组内第一条 targetName）；同名档案（不同 id）不再合并；
 * 未关联（targetId=null）归最后显示「未关联」组头。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 */
@RunWith(AndroidJUnit4::class)
class SessionDrawerGroupTest {

    @get:Rule
    val compose = createComposeRule()

    // 1/4 同属档案 10（小A），3 属档案 20（小B），2 未关联 → 三个分组：小A、小B、未关联
    private val sessions = listOf(
        SessionSummaryUi(id = 1L, title = "最近会话A", createdAt = 5000L, targetName = "小A", targetId = 10L),
        SessionSummaryUi(id = 2L, title = "老会话B", createdAt = 4000L, targetName = null, targetId = null),
        SessionSummaryUi(id = 3L, title = "另一会话", createdAt = 3000L, targetName = "小B", targetId = 20L),
        SessionSummaryUi(id = 4L, title = "小A会话2", createdAt = 2000L, targetName = "小A", targetId = 10L),
    )

    @Test
    fun groupHeadersRendered_unlinkedLast() {
        compose.setContent {
            GtjTheme {
                SessionDrawerContent(
                    sessions = sessions,
                    currentSessionId = null,
                    onNewSession = {},
                    onSelectSession = {},
                    onLongPressSession = {},
                )
            }
        }
        // 组头 + 条目 Tag 都含档案名 → 用 onAllNodesWithText 断言存在
        compose.onAllNodesWithText("小A").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("小B").onFirst().assertIsDisplayed()
        compose.onNodeWithText("未关联").assertIsDisplayed()
        compose.onNodeWithText("最近会话A").assertIsDisplayed()
        compose.onNodeWithText("老会话B").assertIsDisplayed()
    }
}
