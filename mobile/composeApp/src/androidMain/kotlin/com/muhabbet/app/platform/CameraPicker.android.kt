package com.muhabbet.app.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

actual class CameraPickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() = launcher()
}

@Composable
actual fun rememberCameraPickerLauncher(onResult: (PickedImage?) -> Unit): CameraPickerLauncher {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && photoUri != null) {
            val picked = readCameraImage(context, photoUri!!)
            onResult(picked)
        } else {
            onResult(null)
        }
    }

    return remember(launcher) {
        CameraPickerLauncher {
            val uri = createTempImageUri(context)
            photoUri = uri
            launcher.launch(uri)
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun readCameraImage(context: Context, uri: Uri): PickedImage? {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
        val fileName = "camera_${System.currentTimeMillis()}.jpg"
        PickedImage(bytes = bytes, mimeType = "image/jpeg", fileName = fileName)
    } catch (_: Exception) {
        null
    }
}
