package com.wenyan.app.ui.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.contract.SessionSummaryUi
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.9.4 回归 androidTest：会话抽屉「非当前会话」行点击恢复。
 * 非当前会话行 = GlassSurface（不传 onClick）+ 外层 combinedClickable（SessionItem else 分支），
 * 是 v1.9.3 玻璃点击修复的主战场（旧实现内层消费 DOWN → 行点击静默失效）。
 * 断言为行为级：点击行后 onSelectSession 收到该行 id，且不误触 onLongPressSession。
 * 假数据组装方式仿 SessionDrawerGroupTest。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 */
@RunWith(AndroidJUnit4::class)
class SessionDrawerSelectTest {

    @get:Rule
    val compose = createComposeRule()

    // 仿 SessionDrawerGroupTest：1/4 属档案 10（小A），3 属档案 20（小B），2 未关联
    private val sessions = listOf(
        SessionSummaryUi(id = 1L, title = "最近会话A", createdAt = 5000L, targetName = "小A", targetId = 10L),
        SessionSummaryUi(id = 2L, title = "老会话B", createdAt = 4000L, targetName = null, targetId = null),
        SessionSummaryUi(id = 3L, title = "另一会话", createdAt = 3000L, targetName = "小B", targetId = 20L),
        SessionSummaryUi(id = 4L, title = "小A会话2", createdAt = 2000L, targetName = "小A", targetId = 10L),
    )

    @Test
    fun clickNonCurrentSessionRow_reportsThatSessionId() {
        var selectedId = -1L
        var longPressed: SessionSummaryUi? = null
        compose.setContent {
            GtjTheme {
                SessionDrawerContent(
                    sessions = sessions,
                    // id=1 为当前会话（Surface 行）；其余为玻璃行（回归路径）
                    currentSessionId = 1L,
                    onNewSession = {},
                    onSelectSession = { selectedId = it },
                    onLongPressSession = { longPressed = it },
                )
            }
        }

        // 点「另一会话」（id=3，玻璃行）→ onSelectSession 收到 3，长按回调不触发
        compose.onNodeWithText("另一会话").performClick()
        assertEquals(3L, selectedId)
        assertNull(longPressed)
    }
}
