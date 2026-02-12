package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberRestartApp(): () -> Unit {
    return { /* iOS: no-op for now */ }
}
