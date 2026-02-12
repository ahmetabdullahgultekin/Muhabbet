package com.muhabbet.app.platform

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberRestartApp(): () -> Unit {
    val context = LocalContext.current
    return {
        (context as? Activity)?.let { activity ->
            val intent = activity.intent
            activity.finish()
            context.startActivity(intent)
        }
    }
}
