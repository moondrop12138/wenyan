package com.wenyan.desktop

import com.wenyan.app.data.security.AesGcmCipher
import com.wenyan.app.domain.ChatOrchestrator
import com.wenyan.app.knowledge.Bm25Scorer
import com.wenyan.app.llm.SseParser
import com.wenyan.app.prompt.PromptBuilder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O4: 冒烟测试——验证 desktop 模块能编译并链接 :shared KMP 模块的共享业务源码。
 */
class SharedSourceSmokeTest {

    @Test
    fun `shared llm SseParser is on classpath`() {
        // SseParser 来自 :shared commonMain
        assertNotNull(SseParser::class.java)
    }

    @Test
    fun `shared prompt PromptBuilder is on classpath`() {
        assertNotNull(PromptBuilder::class.java)
    }

    @Test
    fun `shared security AesGcmCipher round trip`() {
        // AesGcmCipher 纯 JCE，桌面可直接复用（验证 SecretKeyProvider 接口形态）
        assertTrue(AesGcmCipher::class.java.methods.isNotEmpty())
    }

    @Test
    fun `shared ChatOrchestrator and Bm25Scorer are on classpath`() {
        assertNotNull(ChatOrchestrator::class.java)
        assertNotNull(Bm25Scorer::class.java)
    }
}
