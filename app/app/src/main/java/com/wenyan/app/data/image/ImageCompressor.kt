package com.wenyan.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
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

        // M12: 应用 EXIF 旋转修正（竖拍照片方向正确），90/270 时宽高互换
        val orientation = readExifOrientation(bytes)
        val oriented = rotateForExif(sampled, orientation)
        val swap = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
        val targetW = if (swap) plan.targetHeight else plan.targetWidth
        val targetH = if (swap) plan.targetWidth else plan.targetHeight

        val scaled = if (oriented.width == targetW && oriented.height == targetH) {
            oriented
        } else {
            val resized = Bitmap.createScaledBitmap(oriented, targetW, targetH, true)
            if (resized !== oriented) oriented.recycle()
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

    /** M12: 读取 EXIF 方向（读取失败/无 EXIF 按 NORMAL） */
    private fun readExifOrientation(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** M12: 按 EXIF 方向旋转；无需旋转时原样返回 */
    private fun rotateForExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = ImageSpec.exifOrientationDegrees(orientation)
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    class ImageTooLargeException(message: String) : IOException(message)
}
