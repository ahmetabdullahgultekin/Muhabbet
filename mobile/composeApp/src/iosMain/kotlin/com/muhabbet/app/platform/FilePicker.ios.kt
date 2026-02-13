package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

actual class FilePickerLauncher(
    private val onResult: (PickedFile?) -> Unit
) {
    actual fun launch() {
        val types = listOf(UTTypeData)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.allowsMultipleSelection = false
        val delegate = FilePickerDelegate(onResult)
        delegateRef = delegate
        picker.delegate = delegate

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(picker, animated = true, completion = null)
    }

    companion object {
        private var delegateRef: FilePickerDelegate? = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FilePickerDelegate(
    private val onResult: (PickedFile?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(null)
            return
        }

        val accessing = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url)
            if (data == null) {
                onResult(null)
                return
            }

            val bytes = data.toByteArray()
            val fileName = url.lastPathComponent ?: "file_${NSUUID().UUIDString}"
            val mimeType = mimeTypeFromExtension(url.pathExtension ?: "")

            onResult(PickedFile(bytes = bytes, mimeType = mimeType, fileName = fileName))
        } finally {
            if (accessing) url.stopAccessingSecurityScopedResource()
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}

private fun mimeTypeFromExtension(ext: String): String = when (ext.lowercase()) {
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    else -> "application/octet-stream"
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
actual fun rememberFilePickerLauncher(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    return remember { FilePickerLauncher(onResult) }
}
