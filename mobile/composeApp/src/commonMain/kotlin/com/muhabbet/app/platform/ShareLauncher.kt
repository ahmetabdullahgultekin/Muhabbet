package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Returns a callback that opens the OS share sheet for a plain-text payload
 * (e.g. a group invite link). Android → `Intent.ACTION_SEND`; iOS → `UIActivityViewController`.
 *
 * Mirrors the existing `rememberRestartApp()` expect/actual pattern (platform behavior hoisted
 * to a Composable that captures the platform context). The text is the only payload — there is
 * no backend dependency; sharing is a pure client capability.
 */
@Composable
expect fun rememberShareLauncher(): (text: String) -> Unit
