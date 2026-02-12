package com.muhabbet.app.platform

data class PickedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

expect class ImagePickerLauncher {
    fun launch()
}

@androidx.compose.runtime.Composable
expect fun rememberImagePickerLauncher(onResult: (PickedImage?) -> Unit): ImagePickerLauncher
