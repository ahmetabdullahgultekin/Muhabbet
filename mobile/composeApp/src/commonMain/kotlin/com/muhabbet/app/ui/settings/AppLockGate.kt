package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.config.AppLockTimeout
import com.muhabbet.app.data.local.AppLockController
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.SecureScreenEffect
import com.muhabbet.app.platform.rememberAppLockCapability
import com.muhabbet.app.platform.rememberAppLockLauncher
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_lock_auth_failed
import com.muhabbet.composeapp.generated.resources.app_lock_locked_subtitle
import com.muhabbet.composeapp.generated.resources.app_lock_locked_title
import com.muhabbet.composeapp.generated.resources.app_lock_title
import com.muhabbet.composeapp.generated.resources.app_lock_unlock_action
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.TimeSource

/**
 * App Lock's lifecycle gate (#378) — the piece the audit found entirely missing.
 *
 * Mounted once in `RootComponent.RootContent`, inside the same `Box` as the authenticated app's
 * `Children`, so it draws on top of whatever screen is underneath rather than as a separate window
 * (which would sit outside the `SecureScreenEffect`-protected Activity window — see `SecureScreen.kt`).
 * It renders nothing, and demands nothing, when App Lock is off or the device cannot open it — see
 * [rememberAppLockCapability]'s doc for why a lost capability fails OPEN rather than stranding the
 * user.
 *
 * ## The mechanism
 *
 * `isLocked` starts equal to whether App Lock is enabled — read synchronously at first composition,
 * not flipped true after some effect runs — so there is no frame where an enabled lock has not yet
 * armed and chat content is briefly visible underneath. Re-arming on background/foreground is driven
 * by [AppVisibility.isForeground], which `App.kt` already republishes from the real Decompose/Activity
 * lifecycle (#478's fix, reused rather than re-invented): the moment the app leaves the foreground,
 * a monotonic time mark is recorded; the moment it returns, elapsed time is compared against
 * [AppLockTimeout.graceFor] the stored timeout, and only a background longer than the grace period
 * re-locks. `TimeSource.Monotonic` on purpose, not a wall-clock timestamp — it cannot be fooled by
 * the user changing the system clock while the app is backgrounded, and nothing here needs to
 * survive process death (a killed-and-relaunched process already starts `isLocked = true`).
 */
@Composable
fun AppLockGate(
    controller: AppLockController = koinInject(),
    appVisibility: AppVisibility = koinInject()
) {
    val enabled by controller.enabled.collectAsState()
    val timeout by controller.timeout.collectAsState()
    val capability = rememberAppLockCapability()

    // See rememberAppLockCapability's KDoc: a lock the device can no longer open is treated as off,
    // not as a permanent lockout.
    val armed = enabled && capability

    // Tied to `enabled` alone (not `armed`) is deliberately wrong here — see SecureScreenEffect's
    // own KDoc for why the window must stay secure for the whole time the *setting* is on, not only
    // while a lock cover happens to be showing.
    SecureScreenEffect(enabled = enabled)

    if (!armed) return

    var isLocked by remember { mutableStateOf(true) }
    var backgroundedAt by remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }
    val isForeground by appVisibility.isForeground.collectAsState()

    LaunchedEffect(isForeground) {
        if (isForeground) {
            val elapsed = backgroundedAt
            if (elapsed == null || elapsed.elapsedNow() >= AppLockTimeout.graceFor(timeout)) {
                isLocked = true
            }
            backgroundedAt = null
        } else {
            backgroundedAt = TimeSource.Monotonic.markNow()
        }
    }

    if (!isLocked) return

    var authFailed by remember { mutableStateOf(false) }
    val promptTitle = stringResource(Res.string.app_lock_title)
    val promptSubtitle = stringResource(Res.string.app_lock_locked_subtitle)
    val launchAuth = rememberAppLockLauncher(title = promptTitle, subtitle = promptSubtitle) { success ->
        if (success) {
            isLocked = false
            authFailed = false
        } else {
            authFailed = true
        }
    }

    // Auto-prompt the instant the cover appears, rather than making the user tap "Unlock" first —
    // re-runs every time `isLocked` flips back to true, so a re-arm after backgrounding prompts
    // immediately too, not only the very first lock of the process.
    LaunchedEffect(isLocked) {
        if (isLocked) launchAuth()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(MuhabbetSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Decorative next to the visible title below it — contentDescription is intentionally
            // null so a screen reader announces the title once, not the icon and the title both.
            Icon(
                imageVector = Muhabbet.icons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(MuhabbetSizes.IconEmptyState)
            )
            Spacer(Modifier.height(MuhabbetSpacing.Large))
            Text(
                text = stringResource(Res.string.app_lock_locked_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Text(
                text = promptSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (authFailed) {
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                Text(
                    text = stringResource(Res.string.app_lock_auth_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(MuhabbetSpacing.XLarge))
            MuhabbetButton(
                text = stringResource(Res.string.app_lock_unlock_action),
                onClick = launchAuth,
                modifier = Modifier.fillMaxWidth().height(MuhabbetSizes.MinTouchTarget)
            )
        }
    }
}
