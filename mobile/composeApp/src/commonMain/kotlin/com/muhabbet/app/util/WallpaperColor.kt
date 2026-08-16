package com.muhabbet.app.util

import androidx.compose.ui.graphics.Color

/**
 * Parses a `#RRGGBB` hex string (the format [WallpaperPickerScreen] writes via its own
 * `colorToHex`) back into a [Color]. Returns null on anything malformed rather than throwing, so a
 * corrupted or hand-edited preference value falls back to the theme default instead of crashing the
 * chat screen.
 */
fun String.hexToColorOrNull(): Color? {
    val hex = removePrefix("#")
    if (hex.length != 6) return null
    val value = hex.toIntOrNull(16) ?: return null
    return Color(
        red = (value shr 16) and 0xFF,
        green = (value shr 8) and 0xFF,
        blue = value and 0xFF
    )
}
