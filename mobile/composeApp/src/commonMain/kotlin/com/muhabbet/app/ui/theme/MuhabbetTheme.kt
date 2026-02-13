package com.muhabbet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal700 = Color(0xFF00796B)
private val Teal800 = Color(0xFF00695C)
private val Teal50 = Color(0xFFE0F2F1)
private val Teal100 = Color(0xFFB2DFDB)
private val Teal200 = Color(0xFF80CBC4)
private val Green600 = Color(0xFF43A047)
private val Green100 = Color(0xFFC8E6C9)
private val Green300 = Color(0xFF81C784)
private val Amber600 = Color(0xFFFFB300)
private val Amber100 = Color(0xFFFFECB3)
private val Red700 = Color(0xFFD32F2F)
private val Red400 = Color(0xFFEF5350)

val MuhabbetLightColorScheme = lightColorScheme(
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

val MuhabbetDarkColorScheme = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Teal100,
    secondary = Green300,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Green100,
    tertiary = Amber600,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Amber100,
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// OLED Black theme — true black backgrounds for AMOLED displays
val MuhabbetOledBlackColorScheme = darkColorScheme(
    primary = Teal200,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF003D36),
    onPrimaryContainer = Teal100,
    secondary = Green300,
    onSecondary = Color(0xFF003A08),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Green100,
    tertiary = Amber600,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Amber100,
    error = Red400,
    onError = Color(0xFF601410),
    surface = Color.Black,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF161618),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    background = Color.Black,
    onBackground = Color(0xFFE6E1E5)
)

@Composable
fun MuhabbetTheme(
    oledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = when {
        isDark && oledBlack -> MuhabbetOledBlackColorScheme
        isDark -> MuhabbetDarkColorScheme
        else -> MuhabbetLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
