package com.muhabbet.app.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class FilePickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() = launcher()
}

@Composable
actual fun rememberFilePickerLauncher(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val picked = readFileFromUri(context, uri)
        onResult(picked)
    }

    return remember(launcher) {
        FilePickerLauncher {
            launcher.launch(arrayOf("*/*"))
        }
    }
}

private fun readFileFromUri(context: Context, uri: Uri): PickedFile? {
    return try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
        PickedFile(bytes = bytes, mimeType = mimeType, fileName = fileName)
    } catch (_: Exception) {
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    return cursor.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        } else null
    }
}
