package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareLauncher(): (text: String) -> Unit {
    return { text ->
        val activityController = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(activityController, animated = true, completion = null)
    }
}
