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

        val original = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("无法识别的图片格式")
        val plan = ImageSpec.planResize(original.width, original.height)

        val scaled = if (plan.targetWidth == original.width && plan.targetHeight == original.height) {
            original
        } else {
            val target = java.awt.image.BufferedImage(
                plan.targetWidth, plan.targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB,
            )
            val g = target.createGraphics()
            g.setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            g.drawImage(original, 0, 0, plan.targetWidth, plan.targetHeight, null)
            g.dispose()
            target
        }

        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = ImageSpec.JPEG_QUALITY / 100f
            }
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(scaled, null, null), param)
            }
        } finally {
            writer.dispose()
        }

        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
