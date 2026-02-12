package com.muhabbet.app.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class ImagePickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() = launcher()
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickedImage?) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val picked = readImageFromUri(context, uri)
        onResult(picked)
    }

    return remember(launcher) {
        ImagePickerLauncher {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

private fun readImageFromUri(context: Context, uri: Uri): PickedImage? {
    return try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = "image_${System.currentTimeMillis()}.${mimeType.substringAfter("/")}"
        PickedImage(bytes = bytes, mimeType = mimeType, fileName = fileName)
    } catch (e: Exception) {
        null
    }
}
