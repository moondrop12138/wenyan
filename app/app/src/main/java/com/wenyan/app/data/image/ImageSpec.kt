package com.wenyan.app.data.image

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 图片压缩纯计算逻辑（无 Android 依赖，可 JVM 单测）
 *
 * SPEC §5.3 / llm-contract §6：
 * - 原图 > 20MB 拒绝
 * - 最长边 ≤ 1568px
 * - JPEG 质量 85%
 * - token 估算 = ceil(宽 x 高 / 750)
 */
object ImageSpec {
    const val MAX_EDGE_PX = 1568
    const val JPEG_QUALITY = 85
    const val MAX_ORIGINAL_BYTES = 20L * 1024 * 1024 // 20MB
    const val IMAGE_TOO_LARGE_MESSAGE = "图片过大，请选择更小的图片"

    data class ResizePlan(
        val inSampleSize: Int,
        val targetWidth: Int,
        val targetHeight: Int,
    )

    /**
     * 解码前计算 inSampleSize（防 OOM）+ 缩放目标尺寸。
     * 规则：先按最长边压到 MAX_EDGE_PX 等比缩放；inSampleSize 取 2 的幂，
     * 使采样后最长边仍 ≥ MAX_EDGE_PX（再让 Bitmap 精确缩放，避免过度降质）。
     */
    fun planResize(originalWidth: Int, originalHeight: Int): ResizePlan {
        require(originalWidth > 0 && originalHeight > 0) { "invalid dimensions" }
        val longest = max(originalWidth, originalHeight)

        // 采样：使采样后最长边仍 ≥ MAX_EDGE_PX（取最大的 2 的幂）
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE_PX) {
            sample *= 2
        }

        val sampledW = originalWidth / sample
        val sampledH = originalHeight / sample
        val sampledLongest = max(sampledW, sampledH)
        val scale = if (sampledLongest > MAX_EDGE_PX) {
            MAX_EDGE_PX.toDouble() / sampledLongest
        } else 1.0
        val targetWidth = (sampledW * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (sampledH * scale).roundToInt().coerceAtLeast(1)
        return ResizePlan(sample, targetWidth, targetHeight)
    }

    /** 检查原图字节数是否超限 */
    fun isTooLarge(originalBytes: Long): Boolean = originalBytes > MAX_ORIGINAL_BYTES

    /**
     * Token 估算（OpenAI low-detail 近似）：
     * token = ceil(宽 x 高 / 750)，保守偏高
     */
    fun estimateTokens(width: Int, height: Int): Int =
        ceil(width.toDouble() * height / 750.0).toInt()
}
