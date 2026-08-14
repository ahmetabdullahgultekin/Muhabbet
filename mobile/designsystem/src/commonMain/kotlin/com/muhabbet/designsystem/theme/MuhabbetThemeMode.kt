package com.muhabbet.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The theme the user has chosen, as opposed to the one currently rendering.
 *
 * [storageKey] is the value persisted by `TokenStorage.setTheme`. It lives on the enum so that the
 * four keys are declared once: before this existed, `ThemeSection` spelled them out as string
 * literals in its option list *and* again in a `?: "system"` fallback, with nothing tying either to
 * what the theme actually understood.
 */
enum class MuhabbetThemeMode(val storageKey: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    Oled("oled");

    companion object {
        /** Unknown or absent keys fall back to [System] rather than failing — the value is user data. */
        fun fromStorageKey(key: String?): MuhabbetThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: System
    }
}

/**
 * The variant actually rendering, after [MuhabbetThemeMode.System] has been resolved against the OS.
 *
 * Distinct from [MuhabbetThemeMode] because consumers ask two different questions. Settings asks
 * "what did the user pick?" (System is a valid answer); the depth system, the system-bar icon
 * polarity and the "no shadows on OLED" rule ask "what am I drawing on?" (System is not an answer).
 */
enum class ResolvedThemeMode { Light, Dark, Oled }

/**
 * The rendering variant, for code that must branch on it.
 *
 * [LocalSemanticColors] deliberately cannot answer this: two variants can share a colour and still
 * need different treatment — a shadow is correct on Dark and invisible on OLED even where both
 * paint the same surface.
 */
val LocalThemeMode = staticCompositionLocalOf { ResolvedThemeMode.Light }
