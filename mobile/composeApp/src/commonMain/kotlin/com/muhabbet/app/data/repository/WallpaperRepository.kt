package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage

/**
 * Manages wallpaper preferences locally via SharedPreferences/TokenStorage.
 * Wallpaper settings are device-local and not synced to the server.
 *
 * ### Why this stays local, and what that costs (#380)
 *
 * A `chat_wallpapers` table, `ChatWallpaperService` and `/api/v1/wallpapers`
 * (`ChatWallpaperController` — GET, global PUT, per-conversation PUT, DELETE) exist on the backend and
 * have no mobile callers. Pointing this repository at them instead of `TokenStorage` was the fix #380
 * originally proposed, and it was deliberately not done. The two things that would justify the round
 * trip are a second device and a new phone, and neither pays for itself yet:
 *
 * - **A second device cannot read it.** Companion-device linking ships default-OFF and is gated on the
 *   still-blocked libsignal rewrite (see CLAUDE.md), so there is no live second device to sync to.
 * - **A new phone could only get half of it back.** The endpoint stores a `wallpaperValue` — "colour
 *   hex or media URL" — so DEFAULT, SOLID and GRADIENT would restore, and CUSTOM would not: the photo
 *   lives in app-private storage, and syncing it means putting a user's picture through the media
 *   upload pipeline, which is a second vertical this fix does not touch. Restoring a CUSTOM preference
 *   whose file is not on the new device would point the chat at a path that does not exist.
 * - **Half-syncing is worse than not syncing.** Sending SOLID and GRADIENT to the server while CUSTOM
 *   stayed local would put one preference behind two sources of truth — the same shape as the two
 *   read-receipt switches that could show opposite answers before `PrivacySettingsController` became
 *   the single source (#377).
 *
 * **The cost is real and is not hidden:** a chat wallpaper does not follow the user to a new phone. If
 * that is judged worth a round trip, the work is a client for the endpoints and a decision about
 * CUSTOM — not a rewrite of this class.
 *
 * The backend vertical is not deleted, and it is no longer a trap either. It used to declare its own
 * `SetWallpaperRequest`/`WallpaperResponse` with fields named `type` and `value`, while the shared
 * DTOs a mobile client would serialise name them `wallpaperType` and `wallpaperValue` — so the first
 * call ever made to it would have deserialised into the `type = "DEFAULT"` default and silently wiped
 * the wallpaper it was sent to set. The controller now uses the shared DTOs, and `WallpaperType`
 * carries GRADIENT, so the two sides describe the same thing when someone does wire them.
 *
 * ### Per-chat or global (#380)
 *
 * Global only — the smaller, honest option. [resolveWallpaper] takes no `conversationId`, the picker is
 * reached from Settings rather than from any one chat, and the backend's own `conversationId` column
 * stays unused for the same reason: nothing in this app yet lets a user reach a per-chat picker, and
 * inventing that entry point was not part of this fix.
 */
class WallpaperRepository(
    private val tokenStorage: TokenStorage
) {

    companion object {
        /**
         * The stored `wallpaper_type` values, named once.
         *
         * They were bare literals in both this file and `WallpaperPickerScreen`, compared with `==`
         * in eight places across the two — a typo in any one of them would have silently selected
         * nothing and painted the theme default, which is indistinguishable from the bug #380 was
         * filed for. Public because the picker writes them; the sealed [ChatWallpaper] is what every
         * *reader* should branch on instead.
         *
         * The four storage keys that used to sit here were removed rather than extended: they were
         * private, unreferenced copies of strings that actually live in the `TokenStorage`
         * implementations, so editing one here would have changed nothing while looking decisive.
         */
        const val TYPE_DEFAULT = "DEFAULT"
        const val TYPE_SOLID = "SOLID"
        const val TYPE_GRADIENT = "GRADIENT"
        const val TYPE_CUSTOM = "CUSTOM"
    }

    fun getWallpaperType(): String {
        return tokenStorage.getWallpaperType() ?: TYPE_DEFAULT
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

    fun getGradientId(): String? {
        return tokenStorage.getWallpaperGradientId()
    }

    fun setGradientId(id: String?) {
        tokenStorage.setWallpaperGradientId(id)
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

        /**
         * A gradient from the design system's set, named by id.
         *
         * The id is carried rather than resolved here on purpose: the gradient's colours live in
         * `MuhabbetWallpaperGradients`, and a repository in the data layer has no business importing
         * the design system to look them up. The UI resolves it — and falls back to the theme's own
         * wallpaper when the id names nothing this build ships, exactly as [Solid] does with a hex it
         * cannot parse.
         */
        data class Gradient(val id: String) : ChatWallpaper()
        data class Custom(val path: String) : ChatWallpaper()
    }

    /**
     * Resolves the stored selection into what a chat screen should actually paint, given whether
     * it is currently rendering a dark theme.
     *
     * This is the reader half of the wallpaper feature (#380): `WallpaperPickerScreen` only ever
     * wrote through [setWallpaperType]/[setSolidColor]/[setCustomPath], and nothing consulted them
     * when drawing a chat. Resolving the stored type and its value — plus the dark-mode override — in
     * one place keeps that decision out of the composable and out of every future caller.
     *
     * When [isDarkTheme] is true and the user has not opted in via [getDarkModeWallpaperEnabled],
     * dark chats keep the theme's own wallpaper regardless of what is stored — matching the
     * "wallpaper_dark_mode" toggle's label ("Dark mode wallpaper") and the picker screen it controls.
     */
    fun resolveWallpaper(isDarkTheme: Boolean): ChatWallpaper {
        if (isDarkTheme && !getDarkModeWallpaperEnabled()) return ChatWallpaper.Default
        return when (getWallpaperType()) {
            TYPE_SOLID -> getSolidColor()?.let { ChatWallpaper.Solid(it) } ?: ChatWallpaper.Default
            TYPE_GRADIENT -> getGradientId()?.let { ChatWallpaper.Gradient(it) } ?: ChatWallpaper.Default
            TYPE_CUSTOM -> getCustomPath()?.let { ChatWallpaper.Custom(it) } ?: ChatWallpaper.Default
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
     * Derived from [resolveWallpaper] rather than re-reading the stored fields, so the answer here
     * and the pixels on the chat cannot drift: this is true exactly when a selection exists and the
     * dark-theme branch is what removed it.
     */
    fun isSelectionHiddenByDarkTheme(isDarkTheme: Boolean): Boolean =
        resolveWallpaper(isDarkTheme) == ChatWallpaper.Default &&
            resolveWallpaper(isDarkTheme = false) != ChatWallpaper.Default
}
