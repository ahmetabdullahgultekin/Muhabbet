package com.muhabbet.app.platform

expect class CameraPickerLauncher {
    fun launch()
}

@androidx.compose.runtime.Composable
expect fun rememberCameraPickerLauncher(onResult: (PickedImage?) -> Unit): CameraPickerLauncher
