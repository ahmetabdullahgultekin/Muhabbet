package com.muhabbet.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.config.AppLockTimeout
import com.muhabbet.app.data.local.AppLockController
import com.muhabbet.app.platform.rememberAppLockCapability
import com.muhabbet.app.platform.rememberAppLockLauncher
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetSwitch

/**
 * Settings screen for App Lock (#378) — the writer half of the feature; `AppLockGate.kt` is the
 * reader + mechanism that actually enforces what this screen sets.
 *
 * Turning the lock ON demands a successful platform authentication first (`rememberAppLockLauncher`)
 * — this is the one place in the app that both writes [AppLockController.setEnabled] AND proves the
 * credential it is about to require actually works, rather than trusting the toggle. Turning it OFF
 * needs no such proof: leaving a lock is never the security-sensitive direction.
 *
 * The enable row is disabled — not hidden — when [rememberAppLockCapability] is false, with
 * [Res.string.app_lock_unavailable] explaining why, per #545: "a phone with no enrolled fingerprint
 * must not be offered a lock it cannot open."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    onBack: () -> Unit,
    controller: AppLockController = koinInject()
) {
    val enabled by controller.enabled.collectAsState()
    val timeout by controller.timeout.collectAsState()
    val capability = rememberAppLockCapability()

    var authFailed by remember { mutableStateOf(false) }
    val promptTitle = stringResource(Res.string.app_lock_title)
    val promptSubtitle = stringResource(Res.string.app_lock_locked_subtitle)
    val launchAuth = rememberAppLockLauncher(title = promptTitle, subtitle = promptSubtitle) { success ->
        authFailed = !success
        if (success) controller.setEnabled(true)
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.app_lock_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MuhabbetSpacing.XLarge)
        ) {
            Text(
                text = stringResource(Res.string.app_lock_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!capability) {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.app_lock_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            // Enable/disable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.app_lock_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                MuhabbetSwitch(
                    checked = enabled,
                    enabled = capability,
                    onCheckedChange = { turnOn ->
                        if (turnOn) {
                            // Not persisted here — see launchAuth's onResult above. A toggle that
                            // remembered its position before the credential was proven to work is
                            // exactly the trap #378 warns against: it would look armed and not be.
                            authFailed = false
                            launchAuth()
                        } else {
                            controller.setEnabled(false)
                        }
                    }
                )
            }

            if (authFailed) {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.app_lock_auth_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (enabled) {
                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                Text(
                    text = stringResource(Res.string.app_lock_timeout),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                val timeoutOptions = listOf(
                    AppLockTimeout.IMMEDIATELY to stringResource(Res.string.app_lock_immediately),
                    AppLockTimeout.ONE_MINUTE to stringResource(Res.string.app_lock_1_minute),
                    AppLockTimeout.THIRTY_MINUTES to stringResource(Res.string.app_lock_30_minutes)
                )

                timeoutOptions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MuhabbetSizes.MinTouchTarget)
                            .clickable { controller.setTimeout(key) }
                            .padding(vertical = MuhabbetSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                    ) {
                        RadioButton(
                            selected = timeout == key,
                            onClick = { controller.setTimeout(key) }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
