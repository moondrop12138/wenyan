package com.wenyan.app.data.update

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub Releases 最新版本信息（v1.7.3 T4 更新检查）。
 * GET https://api.github.com/repos/moondrop12138/wenyan/releases/latest
 * 解析：tag_name → versionName（去 "v" 前缀）；body → notes；
 * assets[].browser_download_url（.apk 结尾）→ apkUrl。
 * 失败返回 null（runCatching + 超时），由 UpdateChecker 归一为错误。
 * v1.7.3-fix：删除 versionCode 字段——版本比较统一走 versionName 段比较，
 * 避免 tag 刻度（10703）与 BuildConfig.VERSION_CODE 本地刻度（28）两套刻度混用误报。
 */
data class UpdateInfo(
    val versionName: String,   // 如 "1.7.3"
    val apkUrl: String,
    val notes: String,
)

/** 更新检查结果（错误码约定：UPDATE_NETWORK / UPDATE_PARSE / UPDATE_NO_ASSET / UPDATE_DOWNLOAD / UPDATE_INSTALL） */
sealed interface UpdateCheckResult {
    data class NewVersion(val info: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val code: String, val message: String) : UpdateCheckResult
}

/**
 * GitHub Releases API 客户端（v1.7.3 T4）。
 * 只读版本元数据（不采集用户任何信息）；请求 10s 超时。
 */
class UpdateClient(
    private val okHttp: OkHttpClient,
    private val repo: String = "moondrop12138/wenyan",
) {
    /** 拉取最新 Release；网络/解析失败返回 null */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val client = okHttp.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                parseRelease(JSONObject(body))
            }
        }.getOrNull()
    }

    /** 防御解析：tag_name/body/assets 缺失或非法 → null（绝不抛异常） */
    private fun parseRelease(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name", "").trim()
        if (tag.isEmpty()) return null
        val versionName = tag.removePrefix("v")
        if (versionName.isEmpty()) return null
        val apkUrl = parseApkUrl(json.optJSONArray("assets"))
        if (apkUrl == null) return null
        return UpdateInfo(
            versionName = versionName,
            apkUrl = apkUrl,
            notes = json.optString("body", "").trim(),
        )
    }

    /** assets 中第一个 .apk 结尾的 browser_download_url */
    private fun parseApkUrl(assets: org.json.JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val obj = assets.optJSONObject(i) ?: continue
            val url = obj.optString("browser_download_url", "")
            if (url.endsWith(".apk", ignoreCase = true)) return url
        }
        return null
    }
}
