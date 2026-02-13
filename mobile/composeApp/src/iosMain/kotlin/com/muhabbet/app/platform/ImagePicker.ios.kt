package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

actual class ImagePickerLauncher(
    private val onResult: (PickedImage?) -> Unit
) {
    actual fun launch() {
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration = configuration)
        val delegate = ImagePickerDelegate(onResult)
        delegateRef = delegate
        picker.delegate = delegate

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(picker, animated = true, completion = null)
    }

    companion object {
        private var delegateRef: ImagePickerDelegate? = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ImagePickerDelegate(
    private val onResult: (PickedImage?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onResult(null)
            return
        }

        val provider = result.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(UTTypeImage.identifier)) {
            onResult(null)
            return
        }

        provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
            if (data == null || error != null) {
                onResult(null)
                return@loadDataRepresentationForTypeIdentifier
            }

            val bytes = data.toByteArray()
            val mimeType = provider.suggestedName?.let { name ->
                when {
                    name.endsWith(".png", ignoreCase = true) -> "image/png"
                    name.endsWith(".gif", ignoreCase = true) -> "image/gif"
                    name.endsWith(".heic", ignoreCase = true) -> "image/heic"
                    else -> "image/jpeg"
                }
            } ?: "image/jpeg"
            val fileName = provider.suggestedName ?: "image_${NSUUID().UUIDString}.jpg"

            onResult(PickedImage(bytes = bytes, mimeType = mimeType, fileName = fileName))
        }
    }
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

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickedImage?) -> Unit): ImagePickerLauncher {
    return remember { ImagePickerLauncher(onResult) }
}
