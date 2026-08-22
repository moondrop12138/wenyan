package com.wenyan.app.data.image

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * 桌面版图片压缩（ImageIO 实现，与 Android ImageCompressor 同契约）：
 * - 原图 > 20MB 拒绝（ImageSpec.isTooLarge）
 * - 最长边 ≤ 1568px（ImageSpec.planResize）
 * - JPEG 质量 85% 重编码
 * - 输出 data:image/jpeg;base64,... 直供 ChatRequest.imageDataUrls
 */
object DesktopImageCompressor {

    class ImageTooLargeException : Exception(ImageSpec.IMAGE_TOO_LARGE_MESSAGE)

    /**
     * 压缩并转 data url。
     * @throws ImageTooLargeException 原图超 20MB
     * @throws IllegalArgumentException 无法解码为图片
     */
    fun compressToDataUrl(bytes: ByteArray): String {
        if (ImageSpec.isTooLarge(bytes.size.toLong())) throw ImageTooLargeException()

        // 每次解码前重扫插件：TwelveMonkeys/webp-imageio 经 SPI 注册，installDist 的 -cp 启动
        // 下首次 ImageIO 调用可能发生在插件 jar 可见之前，需显式 scanForPlugins 兜底
        ImageIO.scanForPlugins()

        // L16 修复①：planResize 算出的降采样目标未用于解码阶段——20MB 大图全量解码易 OOM。
        // 改用 ImageReader 先读尺寸，再按比例 subsampling 解码（不支持的格式 reader 自动忽略）。
        val iis = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("无法识别的图片格式（支持 JPEG/PNG/GIF/BMP/WebP）")
        val readers = ImageIO.getImageReaders(iis)
        if (!readers.hasNext()) {
            runCatching { iis.close() }
            throw IllegalArgumentException("无法识别的图片格式（支持 JPEG/PNG/GIF/BMP/WebP）")
        }
        val reader = readers.next()
        reader.input = iis
        val original = try {
            val w = reader.getWidth(0)
            val h = reader.getHeight(0)
            val plan = ImageSpec.planResize(w, h)
            var step = 1
            while (w / (step + 1) >= plan.targetWidth && h / (step + 1) >= plan.targetHeight) step++
            val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            reader.read(0, param)
        } finally {
            runCatching { reader.dispose() }
            runCatching { iis.close() }
        } ?: throw IllegalArgumentException("无法识别的图片格式（支持 JPEG/PNG/GIF/BMP/WebP）")

        val plan = ImageSpec.planResize(original.width, original.height)

        // L16 修复②：透明图按 alpha 走白底合成——原固定 TYPE_INT_RGB 把 GIF/PNG 透明区画成黑底。
        // drawImage(x,y,w,h,bgColor,observer) 一调用同时完成缩放与白底填充。
        val hasAlpha = original.colorModel.hasAlpha()
        val scaled = if (plan.targetWidth == original.width && plan.targetHeight == original.height) {
            original
        } else {
            val targetType =
                if (hasAlpha) java.awt.image.BufferedImage.TYPE_INT_ARGB
                else java.awt.image.BufferedImage.TYPE_INT_RGB
            val target = java.awt.image.BufferedImage(plan.targetWidth, plan.targetHeight, targetType)
            val g = target.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            if (hasAlpha) {
                g.drawImage(original, 0, 0, plan.targetWidth, plan.targetHeight, java.awt.Color.WHITE, null)
            } else {
                g.drawImage(original, 0, 0, plan.targetWidth, plan.targetHeight, null)
            }
            g.dispose()
            target
        }

        // JPEG 不带 alpha：ARGB 中间结果拍平到白底 RGB 再编码，防透明区变黑
        val flat = if (scaled.colorModel.hasAlpha()) {
            java.awt.image.BufferedImage(scaled.width, scaled.height, java.awt.image.BufferedImage.TYPE_INT_RGB).also {
                val g2 = it.createGraphics()
                g2.drawImage(scaled, 0, 0, java.awt.Color.WHITE, null)
                g2.dispose()
            }
        } else {
            scaled
        }

        return encodeJpeg(flat)
    }

    /** JPEG 85% 编码 → data url（L16 重构抽出，透明图拍平路径复用） */
    private fun encodeJpeg(image: java.awt.image.BufferedImage): String {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = ImageSpec.JPEG_QUALITY / 100f
            }
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }

        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
