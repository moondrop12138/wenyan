package com.wenyan.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 图片压缩管线（llm-contract §6）
 * 输入：原始图片字节；输出：data:image/jpeg;base64,{body}
 * 历史仅保留压缩图（db-schema §2.4），原图不落盘。
 */
class ImageCompressor {

    /**
     * 压缩图片并返回 data URL（通道 A 注入 image_url）
     * @param bytes 原始图片字节
     * @throws ImageTooLargeException 原图 > 20MB
     * @throws IOException 解码失败
     */
    @Throws(ImageTooLargeException::class, IOException::class)
    fun compressToDataUrl(bytes: ByteArray): String {
        if (ImageSpec.isTooLarge(bytes.size.toLong())) {
            throw ImageTooLargeException(ImageSpec.IMAGE_TOO_LARGE_MESSAGE)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("cannot decode image")
        }

        val plan = ImageSpec.planResize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = plan.inSampleSize
        }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("cannot decode image")

        val scaled = if (sampled.width == plan.targetWidth && sampled.height == plan.targetHeight) {
            sampled
        } else {
            val resized = Bitmap.createScaledBitmap(
                sampled, plan.targetWidth, plan.targetHeight, true
            )
            if (resized !== sampled) sampled.recycle()
            resized
        }

        val output = ByteArrayOutputStream()
        try {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, ImageSpec.JPEG_QUALITY, output)) {
                throw IOException("jpeg compress failed")
            }
            val body = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            return "data:image/jpeg;base64,$body"
        } finally {
            scaled.recycle()
            output.close()
        }
    }

    class ImageTooLargeException(message: String) : IOException(message)
}
