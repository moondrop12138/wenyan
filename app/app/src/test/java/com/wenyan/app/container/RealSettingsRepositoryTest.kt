package com.wenyan.app.container

import com.wenyan.app.ui.contract.LlmError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.6.3 testAllModels 纯函数测试：
 * 任一模型成功（返回 null）→ 整体 null（绿灯）；全部失败 → 返回最后一个错误（红灯）。
 */
class RealSettingsRepositoryTest {

    @Test
    fun `any model success returns null`() = runTest {
        val result = testAllModels(listOf("a", "b", "c")) { name ->
            if (name == "b") null else LlmError("x", "fail $name", false)
        }
        assertNull(result)
    }

    @Test
    fun `all models fail returns last error`() = runTest {
        val result = testAllModels(listOf("a", "b")) { name ->
            LlmError("x", "fail $name", false)
        }
        assertEquals("fail b", result?.message)
    }

    @Test
    fun `empty models returns null without testing`() = runTest {
        var called = false
        val result = testAllModels(emptyList()) {
            called = true
            null
        }
        assertNull(result)
        assertEquals(false, called)
    }
}
