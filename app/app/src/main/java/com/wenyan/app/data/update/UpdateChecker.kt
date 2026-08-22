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
 * L27 修复：runCatching 会吞掉 CancellationException——协程取消后代码继续跑完并返回
 * 「失败」，取消语义被破坏（结构化并发泄漏/重复下载）。此变体把 CE 原样重抛。
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

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
     * 下载 APK 到 filesDir/downloads/wenyan-{versionName}.apk（覆盖写，幂等）。
     * M9：文件名清洗（防路径穿越）+ Content-Length 校验 + SHA256 digest 校验（GitHub asset 元数据）。
     * 失败返回 null（Log.w + 静默），由上层 Toast。
     */
    suspend fun download(info: UpdateInfo, filesDir: File): File? = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val dir = File(filesDir, "downloads").apply { mkdirs() }
            val target = File(dir, "wenyan-${sanitizeFileName(info.versionName)}.apk")
            val client = okHttp.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(info.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatchingCancellable null
                val body = response.body ?: return@runCatchingCancellable null
                // M9: Content-Length 与 GitHub asset size 对齐（防截断/半包）
                val contentLength = body.contentLength()
                if (info.size > 0 && contentLength >= 0 && contentLength != info.size) {
                    Log.w("UpdateChecker", "apk size mismatch: expected ${info.size} bytes, got $contentLength")
                    return@runCatchingCancellable null
                }
                // M9/L25: 流式写盘 + 同步计算 SHA-256。
                // L25 修复：直接写目标文件——中途被杀留下半截 APK，FileProvider 安装失败且
                // 下次可能误用。改写 .tmp，校验通过后 renameTo 原子落位；异常路径清 .tmp。
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                val tmp = java.io.File(target.parentFile, target.name + ".tmp")
                try {
                    body.byteStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                            }
                        }
                    }
                    val expectedDigest = info.digest?.removePrefix("sha256:")?.lowercase()
                    if (expectedDigest != null) {
                        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        if (actual != expectedDigest) {
                            tmp.delete()
                            Log.w("UpdateChecker", "apk digest mismatch")
                            return@runCatchingCancellable null
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        Log.w("UpdateChecker", "apk rename failed")
                        return@runCatchingCancellable null
                    }
                } catch (e: Exception) {
                    tmp.delete()
                    throw e
                }
            }
            target
        }.onFailure { Log.w("UpdateChecker", "apk download failed", it) }.getOrNull()
    }
}

/** M9: 版本号 → 安全文件名段，仅保留 [A-Za-z0-9_.-]，其余替换为下划线（防恶意 tag 路径穿越） */
internal fun sanitizeFileName(versionName: String): String =
    versionName.replace(Regex("[^\\w.-]"), "_").ifBlank { "unknown" }
