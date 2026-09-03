package com.muhabbet.app.ui.share

import androidx.compose.runtime.Composable
import com.muhabbet.app.platform.rememberShareLauncher
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.share_app_text
import org.jetbrains.compose.resources.stringResource

/**
 * Where "share Muhabbet" points (#591).
 *
 * Google Play, not `muhabbet.rollingcatsoftware.com`. The site is live and would be the better
 * destination the moment it can route a visitor to the right store — but there is no iOS build
 * published and `docs/site/` has no download section at all, so a landing page today would either
 * offer an iOS button that goes nowhere or be a redirect with an extra hop. Linking straight to the
 * store the app is actually in is the honest answer until that changes; swapping this one constant
 * is the whole migration.
 *
 * Not in `strings.xml`. A URL is not translated, and holding it in both locale files is how the two
 * copies drift — one gets updated, the other keeps sending people to the old address, and nothing
 * fails to build.
 */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.muhabbet.app"

/**
 * Opens the platform share sheet with the invite text and the store link.
 *
 * A `@Composable` factory rather than a plain function because both halves have to be resolved
 * during composition: `rememberShareLauncher` captures the platform context, and `stringResource`
 * cannot be called from the click handler it ends up inside. Returning the ready-made lambda is the
 * shape every call site needs and keeps the two rules from being rediscovered at each one.
 *
 * The share text lives in `values/` and `values-en/` like every other user-visible sentence — this
 * is the most-forwarded line the app will ever produce, so it is the last one that should be
 * hardcoded Turkish in a Kotlin file.
 */
@Composable
fun rememberShareMuhabbetAction(): () -> Unit {
    val shareLauncher = rememberShareLauncher()
    val inviteText = stringResource(Res.string.share_app_text, PLAY_STORE_URL)
    return { shareLauncher(inviteText) }
}
