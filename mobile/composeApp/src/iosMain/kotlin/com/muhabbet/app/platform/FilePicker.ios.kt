package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class FilePickerLauncher(
    private val onResult: (PickedFile?) -> Unit
) {
    actual fun launch() {
        // iOS stub — not implemented yet
        onResult(null)
    }
}

@Composable
actual fun rememberFilePickerLauncher(onResult: (PickedFile?) -> Unit): FilePickerLauncher {
    return remember { FilePickerLauncher(onResult) }
}
