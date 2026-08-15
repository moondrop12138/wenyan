package com.wenyan.app.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * v1.7.3 T4 UpdateChecker 版本比较纯逻辑测试（v1.7.3-fix 统一 versionName 段比较）：
 * - compareVersionNames："1.7.3" > "1.7.2"、非法段按 0、"v" 前缀等价、相等返回 0；
 * - isNewer：更高 → true（NewVersion）、同版本/更低 → false（UpToDate）；
 *   不再混用 versionCode 刻度（修复 public 后任何 release 都误报新版本的问题）。
 * （check() 网络路径依赖 GitHub Releases，联调用 MockWebServer 或实机验证。）
 */
class UpdateCheckerTest {

    private fun checker(currentName: String = "1.7.2") =
        UpdateChecker(
            client = UpdateClient(OkHttpClient()),
            currentVersionName = currentName,
            okHttp = OkHttpClient(),
        )

    private fun info(versionName: String) = UpdateInfo(
        versionName = versionName,
        apkUrl = "https://example.com/wenyan-$versionName.apk",
        notes = "",
    )

    @Test
    fun `compareVersionNames newer wins on minor`() {
        val c = checker()
        assertTrue(c.compareVersionNames("1.7.3", "1.7.2") > 0)
        assertTrue(c.compareVersionNames("1.7.2", "1.7.3") < 0)
    }

    @Test
    fun `compareVersionNames equal returns zero`() {
        val c = checker()
        assertEquals(0, c.compareVersionNames("1.7.2", "1.7.2"))
    }

    @Test
    fun `compareVersionNames major wins over minor`() {
        val c = checker()
        assertTrue(c.compareVersionNames("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun `compareVersionNames invalid segments treated as zero`() {
        val c = checker()
        assertTrue(c.compareVersionNames("1.7", "1.7.0") == 0)
        assertTrue(c.compareVersionNames("1.7.x", "1.7.0") == 0)
    }

    @Test
    fun `compareVersionNames longer with zero tail equal`() {
        val c = checker()
        assertEquals(0, c.compareVersionNames("1.7.2.0", "1.7.2"))
    }

    @Test
    fun `compareVersionNames v prefix equivalent`() {
        val c = checker()
        // 远端 tag "v1.7.3" 与本地 BuildConfig "1.7.3" 等价（刻度统一后不误报）
        assertEquals(0, c.compareVersionNames("v1.7.3", "1.7.3"))
        assertEquals(0, c.compareVersionNames("1.7.3", "v1.7.3"))
        assertTrue(c.compareVersionNames("v1.7.3", "1.7.2") > 0)
    }

    // ===== v1.7.3-fix：isNewer 决策（同版本→UpToDate、更高→NewVersion、更低→UpToDate） =====

    @Test
    fun `isNewer higher version returns true NewVersion`() {
        val c = checker(currentName = "1.7.2")
        assertTrue(c.isNewer(info("1.7.3")))
    }

    @Test
    fun `isNewer same version returns false UpToDate`() {
        val c = checker(currentName = "1.7.2")
        assertFalse(c.isNewer(info("1.7.2")))
        // "v" 前缀等价：远端 tag 与本地版本号同版本仍 UpToDate
        assertFalse(c.isNewer(info("v1.7.2")))
    }

    @Test
    fun `isNewer lower version returns false UpToDate`() {
        val c = checker(currentName = "1.7.3")
        assertFalse(c.isNewer(info("1.7.2")))
        assertFalse(c.isNewer(info("1.7.3")))
    }

    @Test
    fun `isNewer invalid remote segment treated as zero not newer`() {
        val c = checker(currentName = "1.7.3")
        assertFalse(c.isNewer(info("1.7.x")))
        assertFalse(c.isNewer(info("bad")))
    }

    // ===== QA 独立补充：版本比较纯逻辑边界（2026-08-07） =====

    @Test
    fun `compareVersionNames numeric segments not lexicographic`() {
        // 数字段按数值比较：1.10 > 1.9（字典序会误判 1.10 < 1.9）
        val c = checker()
        assertTrue(c.compareVersionNames("1.10.0", "1.9.9") > 0)
        assertTrue(c.compareVersionNames("1.9.9", "1.10.0") < 0)
    }

    @Test
    fun `compareVersionNames major boundary zero-nine nine`() {
        val c = checker()
        assertTrue(c.compareVersionNames("0.9.9", "1.0.0") < 0)
        assertTrue(c.compareVersionNames("1.0.0", "0.9.9") > 0)
    }

    @Test
    fun `compareVersionNames same version different trailing zeros equal`() {
        val c = checker()
        assertEquals(0, c.compareVersionNames("1.7.3", "1.7.3.0.0"))
    }

    // ===== M9: 文件名清洗 + 下载完整性校验 =====

    @Test
    fun `sanitizeFileName keeps plain version`() {
        assertEquals("1.7.3", sanitizeFileName("1.7.3"))
        assertEquals("1.7.3-rc.1", sanitizeFileName("1.7.3-rc.1"))
    }

    @Test
    fun `sanitizeFileName neutralizes path traversal`() {
        assertFalse(sanitizeFileName("../../evil").contains("/"))
        assertFalse(sanitizeFileName("../../evil").contains("\\"))
    }

    @Test
    fun `sanitizeFileName empty falls back to unknown`() {
        assertEquals("unknown", sanitizeFileName(""))
    }

    @Test
    fun `download rejects content length mismatch`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("fake-apk"))
            val tmp = Files.createTempDirectory("wenyan").toFile()
            val result = checker().download(
                UpdateInfo("1.7.3", server.url("/a.apk").toString(), "", size = 999),
                tmp,
            )
            assertNull(result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download rejects sha256 digest mismatch`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("fake-apk-content"))
            val tmp = Files.createTempDirectory("wenyan").toFile()
            val result = checker().download(
                UpdateInfo(
                    "1.7.3", server.url("/a.apk").toString(), "",
                    size = 16,
                    digest = "sha256:" + "0".repeat(64),
                ),
                tmp,
            )
            assertNull(result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download succeeds with sanitized name`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("fake-apk-content"))
            val tmp = Files.createTempDirectory("wenyan").toFile()
            val result = checker().download(
                UpdateInfo("1.7.3", server.url("/a.apk").toString(), "", size = 16),
                tmp,
            )
            assertNotNull(result)
            assertEquals("wenyan-1.7.3.apk", result?.name)
            assertEquals("fake-apk-content", result?.readText())
        } finally {
            server.shutdown()
        }
    }
}
