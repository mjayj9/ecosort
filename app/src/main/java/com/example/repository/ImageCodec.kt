package com.example.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageCodec {
    const val MAX_DIMENSION = 1024
    const val MAX_BYTES = 1500 * 1024
    fun read(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 100_000_000) {
            "읽을 수 없는 사진이거나 사진 해상도가 너무 큽니다. 다른 사진을 선택해 주세요."
        }
        options.inSampleSize = 1
        while (max(options.outWidth, options.outHeight) / options.inSampleSize > MAX_DIMENSION * 2) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("사진을 읽을 수 없습니다. 다시 선택해 주세요.")
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val scaled = resize(rotated)
        if (decoded !== rotated && decoded !== scaled) decoded.recycle()
        if (rotated !== scaled) rotated.recycle()
        return scaled
    }
    fun resize(bitmap: Bitmap): Bitmap {
        val scale = minOf(1f, MAX_DIMENSION.toFloat() / max(bitmap.width, bitmap.height))
        return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).toInt()), max(1, (bitmap.height * scale).toInt()), true) else bitmap
    }
    fun encode(bitmap: Bitmap): String {
        val small = resize(bitmap)
        try {
            val out = ByteArrayOutputStream()
            var quality = 82
            do {
                out.reset()
                check(small.compress(Bitmap.CompressFormat.JPEG, quality, out))
                quality -= 10
            } while (out.size() > MAX_BYTES && quality >= 42)
            require(out.size() <= MAX_BYTES) { "사진 용량이 너무 큽니다. 가까이서 다시 촬영해 주세요." }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally { if (small !== bitmap) small.recycle() }
    }
}
