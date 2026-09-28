package com.wenyan.app.ui.components.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wenyan.app.ui.theme.GtjTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.9.4 回归 androidTest：锁定 v1.9.3 玻璃点击修复（GlassSurface 无 onClick 分支）。
 * 复现历史会话行/CrisisCard 的真实用法：GlassSurface 不传 onClick（走 else 分支，
 * 玻璃按压观察器在内层），由调用方在外层 modifier 挂 combinedClickable。
 * 旧实现 detectTapGestures(onPress) 在内层消费 DOWN → 外层 awaitFirstDown(requireUnconsumed = true)
 * 拿不到未消费事件，点击/长按静默失效；修复后内层观察器不消费任何事件，外层回调应正常触发。
 * 断言为行为级：外层 onClick / onLongClick 回调被调用（不校验节点存在性）。
 * 依赖模拟器/真机；本机无模拟器则先保证 assembleAndroidTest 编译通过，实机验证。
 */
@RunWith(AndroidJUnit4::class)
class GlassSurfaceClickTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        const val TAG = "glass_surface_under_test"
    }

    /** 最小组件：外层 combinedClickable + 内层玻璃按压观察（enablePressAnimation = true） */
    @OptIn(ExperimentalFoundationApi::class)
    private fun setContent(onClick: () -> Unit, onLongClick: () -> Unit) {
        compose.setContent {
            GtjTheme {
                GlassSurface(
                    // 刻意不传 onClick：GlassSurface 走无内部 clickable 分支，手势完全由外层接管
                    modifier = Modifier
                        .testTag(TAG)
                        .size(200.dp)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                    enablePressAnimation = true,
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun click_onOuterCombinedClickable_fires() {
        var clicks = 0
        setContent(onClick = { clicks++ }, onLongClick = {})
        compose.onNodeWithTag(TAG, useUnmergedTree = true).performClick()
        assertEquals("外层 combinedClickable 的 onClick 应收到点击", 1, clicks)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun longClick_onOuterCombinedClickable_fires() {
        var longClicks = 0
        setContent(onClick = {}, onLongClick = { longClicks++ })
        compose.onNodeWithTag(TAG, useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals("外层 combinedClickable 的 onLongClick 应收到长按", 1, longClicks)
    }
}
