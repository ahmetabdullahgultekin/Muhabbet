package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/** How a "save as" ended. Reported once per [DocumentSaver.save]. */
enum class DocumentSaveResult { SAVED, CANCELLED, FAILED }

/**
 * Hands a finished file to the user to keep, wherever they choose (#780 — the data export used to
 * fetch the user's data and then throw it away).
 *
 * Android opens the system "save as" (`ACTION_CREATE_DOCUMENT`), so the user picks the folder and
 * the app needs no storage permission. iOS writes the file to the app's temporary directory and
 * presents the share sheet, whose "Save to Files" does the same job.
 */
interface DocumentSaver {
    fun save(fileName: String, content: ByteArray)
}

/**
 * @param mimeType the type of every file this saver writes (the Android contract fixes it up front).
 * @param onResult called on the main thread when the user finishes, cancels, or the write fails.
 */
@Composable
expect fun rememberDocumentSaver(mimeType: String, onResult: (DocumentSaveResult) -> Unit): DocumentSaver
