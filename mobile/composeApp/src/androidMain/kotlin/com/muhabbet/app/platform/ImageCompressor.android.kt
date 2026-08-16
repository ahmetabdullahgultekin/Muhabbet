package com.muhabbet.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

actual fun compressImage(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return bytes

    // BitmapFactory.decodeByteArray ignores the EXIF Orientation tag entirely — it hands back the
    // pixels exactly as the sensor wrote them. Read the tag ourselves and apply the correction
    // before we scale/encode, otherwise the rotation is lost the moment we re-encode (#408): the
    // JPEG we write below carries no EXIF at all, so there is nothing left to correct it with later.
    val transform = transformForExifOrientation(readExifOrientation(bytes))
    val original = applyOrientation(decoded, transform)

    val (newWidth, newHeight) = calculateDimensions(original.width, original.height, maxDimension)

    val scaled = if (newWidth != original.width || newHeight != original.height) {
        Bitmap.createScaledBitmap(original, newWidth, newHeight, true).also {
            if (it != original) original.recycle()
        }
    } else {
        original
    }

    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
    scaled.recycle()

    return output.toByteArray()
}

private fun readExifOrientation(bytes: ByteArray): Int =
    try {
        ByteArrayInputStream(bytes).use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (e: IOException) {
        // Not every source (a screenshot, a re-saved image) carries EXIF. No tag means no rotation
        // to correct, same as ORIENTATION_NORMAL.
        ExifInterface.ORIENTATION_NORMAL
    }

private fun applyOrientation(bitmap: Bitmap, transform: ImageOrientationTransform): Bitmap {
    if (transform == ImageOrientationTransform.IDENTITY) return bitmap

    val matrix = Matrix().apply {
        setRotate(transform.rotationDegrees.toFloat())
        if (transform.flipHorizontal) postScale(-1f, 1f)
    }
    // This overload maps the transformed bounds into a correctly-sized, non-negative bitmap itself
    // (including the width/height swap a 90°/270° rotation needs) — no manual canvas/translate math.
    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented != bitmap) bitmap.recycle()
    return oriented
}

private fun calculateDimensions(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
    if (width <= maxDim && height <= maxDim) return width to height
    val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
    return (width * ratio).toInt() to (height * ratio).toInt()
}
