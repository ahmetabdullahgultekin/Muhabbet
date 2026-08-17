package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage

/**
 * Manages wallpaper preferences locally via SharedPreferences/TokenStorage.
 * Wallpaper settings are device-local and not synced to the server.
 */
class WallpaperRepository(
    private val tokenStorage: TokenStorage
) {

    companion object {
        private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
        private const val KEY_SOLID_COLOR = "wallpaper_solid_color"
        private const val KEY_CUSTOM_PATH = "wallpaper_custom_path"
        private const val KEY_DARK_MODE_ENABLED = "wallpaper_dark_mode"
        private const val DEFAULT_TYPE = "DEFAULT"
    }

    fun getWallpaperType(): String {
        return tokenStorage.getWallpaperType() ?: DEFAULT_TYPE
    }

    fun setWallpaperType(type: String) {
        tokenStorage.setWallpaperType(type)
    }

    fun getSolidColor(): String? {
        return tokenStorage.getSolidColor()
    }

    fun setSolidColor(color: String?) {
        tokenStorage.setSolidColor(color)
    }

    fun getCustomPath(): String? {
        return tokenStorage.getCustomWallpaperPath()
    }

    fun setCustomPath(path: String?) {
        tokenStorage.setCustomWallpaperPath(path)
    }

    fun getDarkModeWallpaperEnabled(): Boolean {
        return tokenStorage.getDarkModeWallpaperEnabled()
    }

    fun setDarkModeWallpaperEnabled(enabled: Boolean) {
        tokenStorage.setDarkModeWallpaperEnabled(enabled)
    }

    /** What a chat screen should actually paint behind its messages. See [resolveWallpaper]. */
    sealed class ChatWallpaper {
        data object Default : ChatWallpaper()
        data class Solid(val hexColor: String) : ChatWallpaper()
        data class Custom(val path: String) : ChatWallpaper()
    }

    /**
     * Resolves the stored selection into what a chat screen should actually paint, given whether
     * it is currently rendering a dark theme.
     *
     * This is the reader half of the wallpaper feature (#380): `WallpaperPickerScreen` only ever
     * wrote through [setWallpaperType]/[setSolidColor]/[setCustomPath], and nothing consulted them
     * when drawing a chat. Resolving the three stored fields — plus the dark-mode override — in one
     * place keeps that decision out of the composable and out of every future caller.
     *
     * When [isDarkTheme] is true and the user has not opted in via [getDarkModeWallpaperEnabled],
     * dark chats keep the theme's own wallpaper regardless of what is stored — matching the
     * "wallpaper_dark_mode" toggle's label ("Dark mode wallpaper") and the picker screen it controls.
     */
    fun resolveWallpaper(isDarkTheme: Boolean): ChatWallpaper {
        if (isDarkTheme && !getDarkModeWallpaperEnabled()) return ChatWallpaper.Default
        return when (getWallpaperType()) {
            "SOLID" -> getSolidColor()?.let { ChatWallpaper.Solid(it) } ?: ChatWallpaper.Default
            "CUSTOM" -> getCustomPath()?.let { ChatWallpaper.Custom(it) } ?: ChatWallpaper.Default
            else -> ChatWallpaper.Default
        }
    }

    /**
     * Whether there is a chosen wallpaper that [resolveWallpaper] is currently refusing to paint.
     *
     * The suppression above is deliberate and, since #548, is the whole of the "the wallpaper
     * disappeared when I switched to OLED" report: OLED is a dark theme, the dark-mode toggle
     * defaults to off, so the selection is dropped. Nothing said so. The picker went on showing the
     * chosen swatch as chosen while the chat painted the theme default, which is the same shape of
     * defect as a language radio naming a language the app is not rendering.
     *
     * Derived from [resolveWallpaper] rather than re-reading the three fields, so the answer here
     * and the pixels on the chat cannot drift: this is true exactly when a selection exists and the
     * dark-theme branch is what removed it.
     */
    fun isSelectionHiddenByDarkTheme(isDarkTheme: Boolean): Boolean =
        resolveWallpaper(isDarkTheme) == ChatWallpaper.Default &&
            resolveWallpaper(isDarkTheme = false) != ChatWallpaper.Default
}
