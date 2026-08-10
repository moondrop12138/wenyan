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
 *
 * tag 前缀隔离：桌面版 Release 使用 desktop-vX.Y.Z（与手机版 vX.Y.Z 隔离），
 * 遍历 releases 列表取第一个 desktop- 前缀的 Release——两端共用 releases/latest
 * 会互相误报新版本（手机版发 v1.9.0 桌面版误报、桌面版发 desktop-v1.8.2 手机版误报）。
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
        val latest = tag.removePrefix("desktop-").removePrefix("v").removePrefix("V")
        // 优先直链该版本 exe 资产；无资产时跳 Release 页
        val url = firstAssetUrl(json)
            ?: "https://github.com/$REPO/releases/tag/$tag"
        val notes = json.optString("body", "").trim()
        return@withContext if (compareVersionNames(latest, currentVersion) > 0) {
            Result("new", currentVersion, latest, notes, url)
        } else {
            Result("latest", currentVersion, latest)
        }
    }

    /** 遍历 releases 列表，返回第一个 desktop- 前缀的 Release（发布时间降序） */
    private fun fetchLatestJson(): JSONObject? = runCatching {
        val conn = URI("https://api.github.com/repos/$REPO/releases?per_page=100")
            .toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        // 代理：Main 启动时开启 java.net.useSystemProxies=true，
        // openConnection() 自动走系统代理（本机 Git 代理 127.0.0.1:7890 场景）
        try {
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = org.json.JSONArray(text)
            for (i in 0 until releases.length()) {
                val obj = releases.optJSONObject(i) ?: continue
                val tag = obj.optString("tag_name", "").trim()
                if (tag.startsWith("desktop-")) return obj
            }
            null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** assets 中第一个非空 browser_download_url（桌面版 exe） */
    private fun firstAssetUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val obj = assets.optJSONObject(i) ?: continue
            val url = obj.optString("browser_download_url", "").trim()
            if (url.isNotEmpty()) return url
        }
        return null
    }

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
