package com.muhabbet.designsystem.platform

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun SystemBarsEffect(lightIcons: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current
    // isInEditMode guards @Preview, where there is no Activity and no real window.
    if (view.isInEditMode || activity == null) return

    // SideEffect, not LaunchedEffect: this writes to a platform object that lives outside the
    // composition, so it must run after every successful recomposition rather than once per key.
    SideEffect {
        WindowInsetsControllerCompat(activity.window, view).apply {
            // The flag names the *background*, not the icons: light bars carry dark icons.
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
