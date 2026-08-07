package com.wenyan.app.data.update

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 更新检查与下载（v1.7.3 T4）：
 * - check()：版本比较统一 versionName 段比较（不再混用 versionCode 两套刻度）；
 * - download()：OkHttp 下载 APK 到 cacheDir/downloads/wenyan-{version}.apk，返回 File/null。
 * 错误归一：UPDATE_NETWORK / UPDATE_PARSE / UPDATE_NO_ASSET / UPDATE_DOWNLOAD / UPDATE_INSTALL。
 */
class UpdateChecker(
    private val client: UpdateClient,
    private val currentVersionName: String,
    private val okHttp: OkHttpClient,
) {

    /** 版本比较：远端 versionName > 当前 → NewVersion；否则 UpToDate；拉取/解析失败 → Failed */
    suspend fun check(): UpdateCheckResult {
        val info = try {
            client.fetchLatest()
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("UPDATE_NETWORK", "网络异常，无法检查更新")
        }
        if (info == null) return UpdateCheckResult.Failed("UPDATE_NETWORK", "网络异常，无法检查更新")
        return if (isNewer(info)) {
            UpdateCheckResult.NewVersion(info)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * v1.7.3-fix：版本比较只走 versionName 段比较（远端 tag 刻度与本地 BuildConfig
     * VERSION_CODE 刻度不一致，混用会导致 public 后任何 release 都误报新版本）。
     */
    internal fun isNewer(info: UpdateInfo): Boolean =
        compareVersionNames(info.versionName, currentVersionName) > 0

    /**
     * "1.7.3" vs "1.7.2" → 1；"v" 前缀等价（"v1.7.3" vs "1.7.3" → 0）；
     * 非法段按 0 处理（internal 供 JVM 单测）。
     */
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

    /** "v1.7.3" / "1.7.3" → [1, 7, 3]；非法段按 0 */
    private fun normalize(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .split(".").map { it.toIntOrNull() ?: 0 }

    /**
     * 下载 APK 到 cacheDir/downloads/wenyan-{versionName}.apk（覆盖写，幂等）。
     * 失败返回 null（Log.w + 静默），由上层 Toast。
     */
    suspend fun download(info: UpdateInfo, cacheDir: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(cacheDir, "downloads").apply { mkdirs() }
            val target = File(dir, "wenyan-${info.versionName}.apk")
            val client = okHttp.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(info.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            target
        }.onFailure { Log.w("UpdateChecker", "apk download failed", it) }.getOrNull()
    }
}
