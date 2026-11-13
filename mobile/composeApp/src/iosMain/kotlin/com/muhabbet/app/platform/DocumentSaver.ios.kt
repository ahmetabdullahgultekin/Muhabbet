package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.muhabbet.app.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

private const val TAG = "DocumentSaver"

/**
 * Writes to the temporary directory and presents the share sheet with the file, so "Save to Files"
 * (or AirDrop, Mail, ...) keeps it. The temporary copy is the OS's to purge.
 */
@Composable
actual fun rememberDocumentSaver(mimeType: String, onResult: (DocumentSaveResult) -> Unit): DocumentSaver {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        object : DocumentSaver {
            override fun save(fileName: String, content: ByteArray) {
                val url = writeTemporary(fileName, content)
                val root = UIApplication.sharedApplication.keyWindow?.rootViewController
                if (url == null || root == null) {
                    currentOnResult(DocumentSaveResult.FAILED)
                    return
                }
                val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
                controller.completionWithItemsHandler = { _, completed, _, _ ->
                    currentOnResult(if (completed) DocumentSaveResult.SAVED else DocumentSaveResult.CANCELLED)
                }
                root.presentViewController(controller, animated = true, completion = null)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeTemporary(fileName: String, content: ByteArray): NSURL? {
    if (content.isEmpty()) return null
    return try {
        val path = NSTemporaryDirectory() + fileName
        val data = content.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = content.size.toULong())
        }
        if (data.writeToFile(path, atomically = true)) NSURL.fileURLWithPath(path) else null
    } catch (e: Exception) {
        Log.e(TAG, "Writing the document failed", e)
        null
    }
}
