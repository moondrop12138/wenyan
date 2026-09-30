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
 * - 按 EXIF orientation 转正竖拍照片（JPEG APP1 解析，安卓 ImageCompressor M12 同语义）
 * - JPEG 质量 85% 重编码（透明区先合成到白底）
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

        // F128（与安卓 ImageCompressor M12 同契约）：ImageIO 的 JPEG reader 只按存储像素解码、
        // 不应用 EXIF orientation，且下方 JPEG 重编码不写 EXIF（write 的 metadata 传 null）——
        // 竖拍手机照片（orientation 6/8）原样发模型即横置 90° 且无法自纠。此处按 EXIF 角度
        // 转正，90/270 时显示宽高互换（对齐安卓 rotateForExif + 目标宽高交换）。
        val exifDegrees = readExifOrientationDegrees(bytes)
        val swapDims = exifDegrees == 90 || exifDegrees == 270
        val targetWidth = if (swapDims) plan.targetHeight else plan.targetWidth
        val targetHeight = if (swapDims) plan.targetWidth else plan.targetHeight

        val scaled = if (exifDegrees == 0 && targetWidth == original.width && targetHeight == original.height) {
            original
        } else {
            // F129+F128：直接建 TYPE_INT_RGB 白底目标，一步完成 缩放 + EXIF 旋转 + 白底合成
            //（原带透明+需缩放路径先画进全尺寸 ARGB 白底——结果已全不透明——再全量重画到
            // RGB，白白多一张位图分配与一次全量 drawImage）
            val target = java.awt.image.BufferedImage(targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = target.createGraphics()
            try {
                g.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                // 先铺白底再 src-over 合成：透明/半透明区与白底混合，防 JPEG 编码后透明区变黑
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, targetWidth, targetHeight)
                // 以目标中心为轴：先按 EXIF 角度顺时针旋转（Java2D rotate 正角即顺时针，
                // 与安卓 Matrix.postRotate 同向），再把旋转后内容等比映射到目标矩形
                val rotatedWidth = if (swapDims) original.height else original.width
                val rotatedHeight = if (swapDims) original.width else original.height
                g.translate(targetWidth / 2.0, targetHeight / 2.0)
                g.rotate(Math.toRadians(exifDegrees.toDouble()))
                g.scale(targetWidth.toDouble() / rotatedWidth, targetHeight.toDouble() / rotatedHeight)
                g.drawImage(original, -(original.width / 2), -(original.height / 2), null)
            } finally {
                g.dispose()
            }
            target
        }

        // JPEG 不带 alpha：无缩放/无旋转路径的透明原图仍需拍平到白底 RGB 再编码，防透明区变黑
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

    /** F128: EXIF orientation → 顺时针角度（复用共享 ImageSpec.exifOrientationDegrees）；非 JPEG/无 EXIF/解析失败 → 0 */
    private fun readExifOrientationDegrees(bytes: ByteArray): Int =
        ImageSpec.exifOrientationDegrees(runCatching { parseJpegExifOrientation(bytes) }.getOrDefault(1))

    /** 解析 JPEG SOI 后首个 APP1 "Exif\0\0" 段内 TIFF IFD0 的 orientation（tag 0x0112，1-8）；结构不符返回 1（NORMAL） */
    private fun parseJpegExifOrientation(bytes: ByteArray): Int {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return 1
        var off = 2
        while (off + 4 <= bytes.size) {
            if (bytes[off] != 0xFF.toByte()) return 1
            val marker = bytes[off + 1].toInt() and 0xFF
            // 无长度字段的独立标记（TEM/RSTn 等）：跳过继续扫
            if (marker == 0x01 || marker in 0xD0..0xD9) {
                off += 2
                continue
            }
            if (marker == 0xDA) return 1   // SOS：EXIF APP1 必在扫描数据之前，放弃
            val segLen = ((bytes[off + 2].toInt() and 0xFF) shl 8) or (bytes[off + 3].toInt() and 0xFF)
            if (segLen < 2) return 1
            val segEnd = off + 2 + segLen
            if (segEnd > bytes.size) return 1
            if (marker == 0xE1 && off + 10 <= segEnd &&
                bytes[off + 4] == 0x45.toByte() && bytes[off + 5] == 0x78.toByte() &&   // "Exif\0\0"
                bytes[off + 6] == 0x69.toByte() && bytes[off + 7] == 0x66.toByte() &&
                bytes[off + 8] == 0x00.toByte() && bytes[off + 9] == 0x00.toByte()
            ) {
                return parseTiffOrientation(bytes, off + 10, segEnd)
            }
            off = segEnd
        }
        return 1
    }

    /** TIFF 头（II/MM）→ IFD0 → orientation tag；越界/不符返回 1 */
    private fun parseTiffOrientation(bytes: ByteArray, start: Int, end: Int): Int {
        if (start + 8 > end) return 1
        val bigEndian = when {
            bytes[start] == 0x4D.toByte() && bytes[start + 1] == 0x4D.toByte() -> true    // "MM"
            bytes[start] == 0x49.toByte() && bytes[start + 1] == 0x49.toByte() -> false   // "II"
            else -> return 1
        }
        fun u16(p: Int): Int =
            if (p < 0 || p + 2 > end) 0
            else if (bigEndian) ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
            else ((bytes[p + 1].toInt() and 0xFF) shl 8) or (bytes[p].toInt() and 0xFF)

        fun u32(p: Int): Int =
            if (p < 0 || p + 4 > end) 0
            else if (bigEndian) ((bytes[p].toInt() and 0xFF) shl 24) or ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
            else ((bytes[p + 3].toInt() and 0xFF) shl 24) or ((bytes[p + 2].toInt() and 0xFF) shl 16) or
                ((bytes[p + 1].toInt() and 0xFF) shl 8) or (bytes[p].toInt() and 0xFF)

        val ifd = start + u32(start + 4)   // IFD0 偏移相对 TIFF 头
        if (ifd < start || ifd + 2 > end) return 1
        val entryCount = u16(ifd)
        for (i in 0 until entryCount) {
            val entry = ifd + 2 + i * 12
            if (entry + 12 > end) return 1
            if (u16(entry) == 0x0112) return u16(entry + 8).coerceIn(1, 8)   // SHORT 值左对齐存于 value 字段前两字节
        }
        return 1
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
