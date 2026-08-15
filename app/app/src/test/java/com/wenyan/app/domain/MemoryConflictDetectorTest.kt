package com.wenyan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O2: 记忆冲突检测测试 */
class MemoryConflictDetectorTest {

    @Test
    fun `antonym pair conflicts`() {
        assertTrue(MemoryConflictDetector.conflicts("她喜欢猫", "她讨厌猫"))
        assertTrue(MemoryConflictDetector.conflicts("他想复合", "她想放下"))
    }

    @Test
    fun `negation conflict`() {
        assertTrue(MemoryConflictDetector.conflicts("她在备考", "她没在备考"))
    }

    @Test
    fun `non conflict returns false`() {
        assertFalse(MemoryConflictDetector.conflicts("她喜欢猫", "她喜欢狗"))
        assertFalse(MemoryConflictDetector.conflicts("她喜欢猫", "她喜欢猫"))
    }

    @Test
    fun `findConflicts returns conflicting facts`() {
        val conflicts = MemoryConflictDetector.findConflicts("她讨厌猫", listOf("她喜欢猫", "她在备考"))
        assertEquals(listOf("她喜欢猫"), conflicts)
    }
}
