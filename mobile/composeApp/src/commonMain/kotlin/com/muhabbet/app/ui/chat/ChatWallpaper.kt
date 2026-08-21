package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.muhabbet.app.data.repository.WallpaperRepository
import com.muhabbet.app.util.hexToColorOrNull
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.LocalThemeMode
import com.muhabbet.designsystem.theme.ResolvedThemeMode
import com.muhabbet.designsystem.theme.muhabbetWallpaperGradient
import org.koin.compose.koinInject

/**
 * The chat surface's background, filling whatever box it is placed in.
 *
 * Hoisted out of [ChatMessageList] so that the loading skeleton and the messages that replace it are
 * painted on the *same* backdrop. While this lived inside the message list, everything shown before
 * the messages arrived — spinner, and now skeleton — sat on the plain theme surface, so a chat with
 * a custom wallpaper visibly changed colour at the moment content landed. That flash is the exact
 * thing a skeleton exists to remove, and it was being reintroduced one layer up.
 *
 * The reader half of #380 lives here: `WallpaperPickerScreen` persists a selection, and this is what
 * consults it. Re-read on each fresh composition of the chat, same as the picker does on open.
 */
@Composable
internal fun ChatWallpaper(modifier: Modifier = Modifier) {
    val wallpaperRepository: WallpaperRepository = koinInject()
    val isDarkTheme = LocalThemeMode.current != ResolvedThemeMode.Light
    val wallpaper = remember(isDarkTheme) { wallpaperRepository.resolveWallpaper(isDarkTheme) }
    // `.container` since #536: every semantic ground now ships with the foreground that is legible
    // on it. Only the ground is wanted here — nothing is drawn *on* the wallpaper by this composable.
    val defaultWallpaperColor = LocalSemanticColors.current.chatWallpaper.container

    when (wallpaper) {
        // A photo is arbitrary user media, so nothing drawn over it can promise contrast against a
        // theme token — the same category #586/#589 found unreadable in the status viewer. It is
        // painted here at full fidelity rather than under a tint, because the one surface a chat
        // draws straight onto the wallpaper (the date-separator pill) now carries its own opacity
        // floor: see MuhabbetAlphas.ChatOverlaySurface, which is measured against a pure-white and
        // a pure-black photo, not just against the palette. Washing every user's picture to protect
        // one pill would have been the wrong end of the problem — and it would not have worked
        // anyway: the other translucent surface a chat could draw here, the half-opacity
        // deleted-message bubble, failed just as badly over a light *swatch*, which no tint on a
        // photo reaches. That one was fixed where it belonged, on the bubble: #678 gave it an opaque
        // ground of its own, so the pill is once again the only surface the wallpaper reaches.
        is WallpaperRepository.ChatWallpaper.Custom -> AsyncImage(
            model = "file://${wallpaper.path}",
            // Decorative background, not content — a screen reader has nothing useful to
            // announce about a chat's wallpaper.
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            // If the file was removed from under the app (cleared storage, restored backup),
            // fall back to the same default the Default branch below paints, rather than a
            // blank or broken image.
            error = ColorPainter(defaultWallpaperColor)
        )
        is WallpaperRepository.ChatWallpaper.Solid -> Box(
            modifier = modifier.fillMaxSize()
                .background(wallpaper.hexColor.hexToColorOrNull() ?: defaultWallpaperColor)
        )
        is WallpaperRepository.ChatWallpaper.Gradient -> {
            // The stored preference is an id, never the colours — the design system owns those, so a
            // palette revision reaches every device that already picked one. An id this build does
            // not ship resolves to null and falls back to the theme's own wallpaper, which is the
            // same contract the Solid branch above gives an unparseable hex.
            val brush = remember(wallpaper.id, defaultWallpaperColor) {
                muhabbetWallpaperGradient(wallpaper.id)?.brush ?: SolidColor(defaultWallpaperColor)
            }
            Box(modifier = modifier.fillMaxSize().background(brush))
        }
        WallpaperRepository.ChatWallpaper.Default -> Box(
            modifier = modifier.fillMaxSize().background(defaultWallpaperColor)
        )
    }
}
