package com.wenyan.app.ui.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.7.3 T2 会话分组 androidTest（v1.7.3-fix：分组键改 targetId）：
 * 按档案 id 分组渲染组头（组头文字取组内第一条 targetName）；同名档案（不同 id）不再合并；
 * 未关联（targetId=null）归最后显示「未关联」组头。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 *
 * F03/F35 修复：原断言全是「存在且可见」，组头断言因行内 Tag 同名而形同虚设、
 * 排序声明无任何顺序断言，fixtures 也没有同名不同 id 的档案对。现改为：
 * - fixtures 增加同名不同 targetId（10 与 30 都叫「小A」）的档案对；
 * - 用 unmergedTree 计数断言组头真实渲染（组头+Tag 的节点数随组数变化）；
 * - 用 positionInRoot.y 断言「未关联」组排在所有具名组之后。
 */
@RunWith(AndroidJUnit4::class)
class SessionDrawerGroupTest {

    @get:Rule
    val compose = createComposeRule()

    // 1/4 同属档案 10（小A），3 属档案 20（小B），5 属档案 30（同名「小A」，不与 10 合并），
    // 2 未关联 → 四个分组：小A(10)、小B、小A(30)、未关联
    private val sessions = listOf(
        SessionSummaryUi(id = 1L, title = "最近会话A", createdAt = 5000L, targetName = "小A", targetId = 10L),
        SessionSummaryUi(id = 2L, title = "老会话B", createdAt = 4000L, targetName = null, targetId = null),
        SessionSummaryUi(id = 3L, title = "另一会话", createdAt = 3000L, targetName = "小B", targetId = 20L),
        SessionSummaryUi(id = 4L, title = "小A会话2", createdAt = 2000L, targetName = "小A", targetId = 10L),
        SessionSummaryUi(id = 5L, title = "同名档案会话", createdAt = 1000L, targetName = "小A", targetId = 30L),
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
        // 组头 + 条目 Tag 都含档案名 → unmergedTree 计数：
        // 「小A」= 2 个组头（档案 10 与 30）+ 3 个行内 Tag = 5；若组头漏渲染（-2）或
        // 分组退回按 targetName 合并（v1.7.3-fix 前行为，10/30 并成一组 → -1）计数即失败
        compose.onAllNodesWithText("小A", useUnmergedTree = true).assertCountEquals(5)
        // 「小B」= 1 个组头 + 1 个行内 Tag = 2
        compose.onAllNodesWithText("小B", useUnmergedTree = true).assertCountEquals(2)
        // 「未关联」文本仅组头产生（fixtures 无 targetName=未关联 的行）
        compose.onNodeWithText("未关联").assertIsDisplayed()
        compose.onNodeWithText("最近会话A").assertIsDisplayed()
        compose.onNodeWithText("老会话B").assertIsDisplayed()

        // F35 修复：真排序断言——「未关联」组头的 y 位置必须严格大于所有具名组节点
        //（组头回归到最前/中间时本断言即红）
        val namedNodes = compose.onAllNodesWithText("小A", useUnmergedTree = true).fetchSemanticsNodes() +
            compose.onAllNodesWithText("小B", useUnmergedTree = true).fetchSemanticsNodes()
        val unlinkedY = compose.onAllNodesWithText("未关联", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
            .positionInRoot.y
        val namedMaxY = namedNodes.maxOf { it.positionInRoot.y }
        assertTrue("未关联组应排在所有具名组之后（unlinkedY=$unlinkedY, namedMaxY=$namedMaxY）", unlinkedY > namedMaxY)
    }
}
