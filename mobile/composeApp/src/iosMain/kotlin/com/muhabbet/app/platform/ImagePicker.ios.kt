package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class ImagePickerLauncher(
    private val onResult: (PickedImage?) -> Unit
) {
    actual fun launch() {
        // iOS stub — not implemented yet
        onResult(null)
    }
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (PickedImage?) -> Unit): ImagePickerLauncher {
    return remember { ImagePickerLauncher(onResult) }
}
