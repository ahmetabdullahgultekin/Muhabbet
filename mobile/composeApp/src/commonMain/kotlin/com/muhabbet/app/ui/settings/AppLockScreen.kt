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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.config.AppLockTimeout
import com.muhabbet.app.platform.rememberAppLockCapability
import com.muhabbet.app.platform.rememberAppLockLauncher
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetSwitch

/**
 * Settings screen for App Lock (#378) — the writer half of the feature; `AppLockGate.kt` is the
 * reader + mechanism that actually enforces what this screen sets.
 *
 * Turning the lock ON demands a successful platform authentication first (`rememberAppLockLauncher`)
 * — [AppLockSettingsStore] calls `AppLockController.setEnabled` only after the platform proves the
 * credential it is about to require actually works, rather than trusting the toggle. Turning it OFF
 * needs no such proof: leaving a lock is never the security-sensitive direction.
 *
 * The enable row is disabled — not hidden — when [rememberAppLockCapability] is false, with
 * [Res.string.app_lock_unavailable] explaining why, per #545: "a phone with no enrolled fingerprint
 * must not be offered a lock it cannot open."
 */
@Composable
fun AppLockScreen(component: AppLockSettingsComponent) {
    val store = component.store
    val state by store.state.collectAsStateWithLifecycle()
    val promptTitle = stringResource(Res.string.app_lock_title)
    val promptSubtitle = stringResource(Res.string.app_lock_locked_subtitle)
    // The platform prompt is a composable-scoped launcher, so it is performed here, as an effect the
    // Store asked for; the Store decides what its result means.
    val launchAuth = rememberAppLockLauncher(title = promptTitle, subtitle = promptSubtitle) { success ->
        store.onEvent(AppLockSettingsEvent.AuthenticationFinished(success))
    }
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                AppLockSettingsEffect.Authenticate -> launchAuth()
            }
        }
    }
    AppLockScreen(
        state = state,
        capability = rememberAppLockCapability(),
        onEvent = store::onEvent,
        onBack = component.onBack
    )
}

/** A pure renderer of [AppLockSettingsUiState]. [capability] is whether this device can open a lock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    state: AppLockSettingsUiState,
    capability: Boolean,
    onEvent: (AppLockSettingsEvent) -> Unit,
    onBack: () -> Unit
) {
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
                    checked = state.enabled,
                    enabled = capability,
                    // Not persisted here: AppLockSettingsStore arms the lock only after the
                    // platform proves the credential works (#378).
                    onCheckedChange = { turnOn -> onEvent(AppLockSettingsEvent.ToggleRequested(turnOn)) }
                )
            }

            if (state.authFailed) {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.app_lock_auth_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.enabled) {
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
                            .clickable { onEvent(AppLockSettingsEvent.TimeoutSelected(key)) }
                            .padding(vertical = MuhabbetSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                    ) {
                        RadioButton(
                            selected = state.timeout == key,
                            onClick = { onEvent(AppLockSettingsEvent.TimeoutSelected(key)) }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
