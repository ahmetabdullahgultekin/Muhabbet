package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Matches the system bars' icon polarity to the rendered theme.
 *
 * The status and navigation bars are drawn by the OS, outside the Compose tree, so they do not
 * inherit the colour scheme. Since `targetSdk = 36` the app is edge-to-edge whether it asks to be or
 * not — content draws behind those bars — and on API 35+ the `android:statusBarColor` that used to
 * paper over this is ignored outright. Without this, dark icons sit on a dark background.
 *
 * @param lightIcons true to paint bar icons light (for dark surfaces), false for dark icons.
 */
@Composable
expect fun SystemBarsEffect(lightIcons: Boolean)
