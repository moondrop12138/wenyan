package com.wenyan.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ProviderUrlNormalizer 测试（v1.7.x 新增）
 * 覆盖：正常根地址 / 尾斜杠 / 完整端点 / 空格 / 非法字符（逗号、中文）/ 空串 / query·fragment / 不补 /v1
 * F22：空串改为拒绝（原样放行会落库出无 scheme 的相对路径）、含 ?/# 的输入拒绝（硬拼端点恒 404）
 */
class ProviderUrlNormalizerTest {

    @Test
    fun `root url stays unchanged`() {
        assertEquals(
            "https://api.deepseek.com",
            ProviderUrlNormalizer.normalize("https://api.deepseek.com"),
        )
    }

    @Test
    fun `trailing slash is stripped`() {
        assertEquals(
            "https://api.kimi.com/coding/v1",
            ProviderUrlNormalizer.normalize("https://api.kimi.com/coding/v1/"),
        )
    }

    @Test
    fun `full chat completions endpoint is trimmed to root`() {
        assertEquals(
            "https://api.kimi.com/coding/v1",
            ProviderUrlNormalizer.normalize("https://api.kimi.com/coding/v1/chat/completions"),
        )
    }

    @Test
    fun `full endpoint with trailing slash is trimmed`() {
        assertEquals(
            "https://api.kimi.com/coding/v1",
            ProviderUrlNormalizer.normalize("https://api.kimi.com/coding/v1/chat/completions/"),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "https://api.kimi.com/coding/v1",
            ProviderUrlNormalizer.normalize("  https://api.kimi.com/coding/v1  "),
        )
    }

    @Test
    fun `comma in url is rejected`() {
        assertNull(ProviderUrlNormalizer.normalize("https://api.kimi.com/coding/v1/chat,"))
    }

    @Test
    fun `chinese characters in url are rejected`() {
        assertNull(ProviderUrlNormalizer.normalize("https://api.kimi.com/v1/测试"))
    }

    @Test
    fun `space inside url is rejected`() {
        assertNull(ProviderUrlNormalizer.normalize("https://api.kimi.com/coding v1"))
    }

    @Test
    fun `root url without v1 is not auto-prefixed`() {
        assertEquals(
            "https://api.deepseek.com",
            ProviderUrlNormalizer.normalize("https://api.deepseek.com/"),
        )
    }

    @Test
    fun `empty string is rejected`() {
        assertNull(ProviderUrlNormalizer.normalize(""))
        assertNull(ProviderUrlNormalizer.normalize("   "))
    }

    @Test
    fun `query in url is rejected`() {
        // F22：带 query 的 baseUrl 硬拼 /chat/completions 会把追加路径吞进 query 恒 404
        assertNull(ProviderUrlNormalizer.normalize("https://h/v1?api-version=x"))
    }

    @Test
    fun `fragment in url is rejected`() {
        assertNull(ProviderUrlNormalizer.normalize("https://api.kimi.com/v1#frag"))
    }
}
