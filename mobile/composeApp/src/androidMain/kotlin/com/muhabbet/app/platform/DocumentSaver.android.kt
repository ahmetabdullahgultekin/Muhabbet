package com.muhabbet.app.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.muhabbet.app.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DocumentSaver"

@Composable
actual fun rememberDocumentSaver(mimeType: String, onResult: (DocumentSaveResult) -> Unit): DocumentSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    // The bytes wait here while the system picker is open. Lost if the process dies behind the
    // picker; the result is then FAILED and the user can export again.
    val pending = remember { arrayOfNulls<ByteArray>(1) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri: Uri? ->
        val content = pending[0]
        pending[0] = null
        if (uri == null) {
            currentOnResult(DocumentSaveResult.CANCELLED)
            return@rememberLauncherForActivityResult
        }
        if (content == null) {
            currentOnResult(DocumentSaveResult.FAILED)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content) } != null
                } catch (e: Exception) {
                    Log.e(TAG, "Writing the document failed", e)
                    false
                }
            }
            currentOnResult(if (written) DocumentSaveResult.SAVED else DocumentSaveResult.FAILED)
        }
    }

    return remember(launcher) {
        object : DocumentSaver {
            override fun save(fileName: String, content: ByteArray) {
                pending[0] = content
                launcher.launch(fileName)
            }
        }
    }
}
