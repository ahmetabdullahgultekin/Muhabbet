package com.muhabbet.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

actual fun compressImage(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return bytes

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

private fun calculateDimensions(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
    if (width <= maxDim && height <= maxDim) return width to height
    val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
    return (width * ratio).toInt() to (height * ratio).toInt()
}
