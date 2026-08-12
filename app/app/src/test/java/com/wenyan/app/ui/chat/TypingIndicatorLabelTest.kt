package com.wenyan.app.ui.chat

import com.wenyan.app.ui.components.resolveWaitingLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.9.2 等待文案三档判定（confirming > transcribing > 普通）纯函数测试，无需 Compose 环境 */
class TypingIndicatorLabelTest {

    @Test
    fun `default is knowledge base copy`() {
        assertEquals("正在翻知识库，梳理你的处境…", resolveWaitingLabel(transcribing = false, confirming = false))
    }

    @Test
    fun `transcribing shows vision extract copy`() {
        assertEquals("视觉模型正在提取截图文字…", resolveWaitingLabel(transcribing = true, confirming = false))
    }

    @Test
    fun `confirming shows analysis copy`() {
        assertEquals("军师分析中…", resolveWaitingLabel(transcribing = false, confirming = true))
    }

    @Test
    fun `confirming wins over transcribing`() {
        assertEquals("军师分析中…", resolveWaitingLabel(transcribing = true, confirming = true))
    }
}
