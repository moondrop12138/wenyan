package com.wenyan.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * 桌面版更新检查：直连 GitHub Releases（与手机版 UpdateClient/UpdateChecker 同语义）。
 *
 * 与手机版一致的三条纪律：
 *  1. 版本比较只走 versionName 段比较（不混用任何整数刻度）；
 *  2. 防御解析：任何字段缺失/非法 → null/Failed，绝不抛异常；
 *  3. 只读版本元数据，不采集用户信息，10s 超时。
 *
 * 桌面版没有 APK 资产语义：assets 取第一个 browser_download_url（exe/zip 均可），
 * 无资产时仍返回版本信息（downloadUrl 为空串，前端只展示"去 Release 页"）。
 */
object DesktopUpdateChecker {

    private const val REPO = "moondrop12138/wenyan"

    data class Result(
        val status: String,          // new / latest / failed
        val current: String,
        val latest: String = "",
        val notes: String = "",
        val downloadUrl: String = "",
        val error: String = "",
    )

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        val json = fetchLatestJson()
            ?: return@withContext Result("failed", currentVersion, error = "网络异常，无法检查更新")
        val tag = json.optString("tag_name", "").trim()
        if (tag.isEmpty()) return@withContext Result("failed", currentVersion, error = "Release 解析失败")
        val latest = tag.removePrefix("v").removePrefix("V")
        // 桌面版不直链资产（手机 Release 挂的是 APK）：统一跳该版本 Release 页
        val url = "https://github.com/$REPO/releases/tag/$tag"
        val notes = json.optString("body", "").trim()
        return@withContext if (compareVersionNames(latest, currentVersion) > 0) {
            Result("new", currentVersion, latest, notes, url)
        } else {
            Result("latest", currentVersion, latest)
        }
    }

    private fun fetchLatestJson(): JSONObject? = runCatching {
        val conn = URI("https://api.github.com/repos/$REPO/releases/latest")
            .toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** "1.7.3" vs "1.7.2" → 1；"v" 前缀等价；非法段按 0（与手机版 UpdateChecker 逐字一致） */
    internal fun compareVersionNames(a: String, b: String): Int {
        val pa = normalize(a)
        val pb = normalize(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun normalize(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(".").map { it.toIntOrNull() ?: 0 }
}
