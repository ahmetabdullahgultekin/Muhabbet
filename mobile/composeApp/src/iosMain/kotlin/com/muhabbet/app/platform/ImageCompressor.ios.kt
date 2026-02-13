package com.muhabbet.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

@OptIn(ExperimentalForeignApi::class)
actual fun compressImage(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray {
    val nsData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val image = UIImage.imageWithData(nsData) ?: return bytes

    val originalWidth = image.size.useContents { width }.toInt()
    val originalHeight = image.size.useContents { height }.toInt()

    val (newWidth, newHeight) = calculateDimensions(originalWidth, originalHeight, maxDimension)

    val resizedImage = if (newWidth != originalWidth || newHeight != originalHeight) {
        resizeImage(image, newWidth, newHeight) ?: image
    } else {
        image
    }

    val compressionQuality = quality.toDouble() / 100.0
    val jpegData = UIImageJPEGRepresentation(resizedImage, compressionQuality) ?: return bytes
    return jpegData.toByteArray()
}

private fun calculateDimensions(width: Int, height: Int, maxDim: Int): Pair<Int, Int> {
    if (width <= maxDim && height <= maxDim) return width to height
    val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
    return (width * ratio).toInt() to (height * ratio).toInt()
}

@OptIn(ExperimentalForeignApi::class)
private fun resizeImage(image: UIImage, newWidth: Int, newHeight: Int): UIImage? {
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context = CGBitmapContextCreate(
        data = null,
        width = newWidth.toULong(),
        height = newHeight.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = (4 * newWidth).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
    ) ?: return null

    val cgImage = image.CGImage ?: return null
    CGContextDrawImage(context, CGRectMake(0.0, 0.0, newWidth.toDouble(), newHeight.toDouble()), cgImage)
    val resizedCGImage = CGBitmapContextCreateImage(context) ?: return null
    return UIImage.imageWithCGImage(resizedCGImage)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}
