package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.muhabbet.app.data.repository.WallpaperRepository
import com.muhabbet.app.util.hexToColorOrNull
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.LocalThemeMode
import com.muhabbet.designsystem.theme.ResolvedThemeMode
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
        WallpaperRepository.ChatWallpaper.Default -> Box(
            modifier = modifier.fillMaxSize().background(defaultWallpaperColor)
        )
    }
}
