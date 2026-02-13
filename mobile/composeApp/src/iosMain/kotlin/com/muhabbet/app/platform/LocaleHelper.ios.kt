package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue

@Composable
actual fun rememberRestartApp(): () -> Unit {
    return {
        // On iOS, changing locale requires setting AppleLanguages preference
        // and restarting the app. The language preference is read by CMP
        // on next app launch via NSUserDefaults "AppleLanguages" key.
        val language = NSUserDefaults.standardUserDefaults.stringForKey("app_language") ?: "tr"
        val appleLanguage = when (language) {
            "en" -> "en"
            else -> "tr"
        }
        NSUserDefaults.standardUserDefaults.setObject(listOf(appleLanguage), forKey = "AppleLanguages")
        NSUserDefaults.standardUserDefaults.synchronize()
        NSLog("LocaleHelper: language set to $appleLanguage — restart required")

        // Force terminate and relaunch (standard iOS pattern for language change)
        platform.darwin.exit(0)
    }
}
