package com.muhabbet.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * No-op on iOS.
 *
 * UIKit resolves the status-bar style from the hosting view controller's
 * `preferredStatusBarStyle`, not from a window-level flag, so the fix belongs in the iOS app shell
 * rather than here. Compose Multiplatform's `UIViewController` host already reports a style derived
 * from the system appearance, which is correct for the System theme mode and wrong only when the
 * user has overridden it in-app. Left deliberately empty until the iOS shell is built out; a stub
 * that guessed at a UIKit traversal would be worse than an honest no-op.
 */
@Composable
actual fun SystemBarsEffect(lightIcons: Boolean) {
    // Intentionally empty — see KDoc.
}
