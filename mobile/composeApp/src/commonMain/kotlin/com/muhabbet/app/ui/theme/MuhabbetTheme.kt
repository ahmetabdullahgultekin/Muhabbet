package com.muhabbet.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal700 = Color(0xFF00796B)
private val Teal800 = Color(0xFF00695C)
private val Teal50 = Color(0xFFE0F2F1)
private val Teal100 = Color(0xFFB2DFDB)
private val Green600 = Color(0xFF43A047)
private val Green100 = Color(0xFFC8E6C9)
private val Amber600 = Color(0xFFFFB300)
private val Amber100 = Color(0xFFFFECB3)
private val Red700 = Color(0xFFD32F2F)

private val MuhabbetColorScheme = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal800,
    secondary = Green600,
    onSecondary = Color.White,
    secondaryContainer = Green100,
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Amber600,
    onTertiary = Color.White,
    tertiaryContainer = Amber100,
    onTertiaryContainer = Color(0xFF7F6003),
    error = Red700,
    onError = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF0F4F3),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun MuhabbetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MuhabbetColorScheme,
        content = content
    )
}
