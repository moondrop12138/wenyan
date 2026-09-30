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

        // M12: 应用 EXIF 旋转修正（竖拍照片方向正确），90/270 时宽高互换。
        // F17 修复：补齐镜像/转置类方向——EXIF 2/4（镜像）与 5/7（转置，部分前置相机真实产出）
        // 此前完全不修正（不旋转不镜像、宽高也不互换），压缩入库/发给视觉模型的方向是错的。
        // 旋转角度仍取 ImageSpec.exifOrientationDegrees（6/3/8），镜像/转置组合在本类补齐
        //（ImageSpec 为 shared 模块纯计算层，保持无 Android 依赖不动）
        val orientation = readExifOrientation(bytes)
        val oriented = transformForExif(sampled, orientation)
        val swap = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
            -> true
            else -> false
        }
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

    /**
     * M12: 按 EXIF 方向做显示修正；无需处理时原样返回。
     * F17 修复：在 ImageSpec.exifOrientationDegrees 的 6/3/8 纯旋转之外，补上规范定义的
     * 镜像/转置方向（androidx.exifinterface 常量）：
     * - FLIP_HORIZONTAL(2)：水平镜像；FLIP_VERTICAL(4)：垂直镜像；
     * - TRANSPOSE(5)：转置（顺时针 90° + 水平镜像）；TRANSVERSE(7)：反向转置（顺时针 270° + 水平镜像）。
     * Matrix.post* 链式拼接的生效顺序为先 postRotate 后 postScale（p' = S·(R·p)），
     * 与 EXIF 转置显示变换的标准配方一致。
     */
    private fun transformForExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    class ImageTooLargeException(message: String) : IOException(message)
}
