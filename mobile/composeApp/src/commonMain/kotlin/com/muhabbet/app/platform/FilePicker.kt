package com.muhabbet.app.platform

data class PickedFile(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
)

expect class FilePickerLauncher {
    fun launch()
}

@androidx.compose.runtime.Composable
expect fun rememberFilePickerLauncher(onResult: (PickedFile?) -> Unit): FilePickerLauncher
