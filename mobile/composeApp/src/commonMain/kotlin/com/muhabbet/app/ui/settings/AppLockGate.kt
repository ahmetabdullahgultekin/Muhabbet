package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.PrivacyModeRepository
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.mahrem_pin_enter_title
import com.muhabbet.composeapp.generated.resources.mahrem_pin_label
import com.muhabbet.composeapp.generated.resources.mahrem_pin_wrong
import com.muhabbet.composeapp.generated.resources.mahrem_title
import com.muhabbet.composeapp.generated.resources.mahrem_unlock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Full-screen PIN gate for **Mahrem Mod** app-lock. Rendered over the app content when the lock is
 * armed; calls [onUnlocked] once the user enters the correct PIN (verified against the salted hash in
 * [PrivacyModeRepository]). No "forgot PIN" path in S1 — the user can disable the lock from Settings
 * once unlocked.
 */
@Composable
fun AppLockGate(
    onUnlocked: () -> Unit,
    privacyMode: PrivacyModeRepository = koinInject()
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val wrongPinMsg = stringResource(Res.string.mahrem_pin_wrong)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(Res.string.mahrem_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.mahrem_pin_enter_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= PrivacyModeRepository.PIN_MAX_LENGTH && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = false
                    }
                },
                label = { Text(stringResource(Res.string.mahrem_pin_label)) },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text(wrongPinMsg, color = MaterialTheme.colorScheme.error) }
                } else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (privacyMode.verifyPin(pin)) {
                        onUnlocked()
                    } else {
                        error = true
                        pin = ""
                    }
                },
                enabled = pin.length >= PrivacyModeRepository.PIN_MIN_LENGTH,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.mahrem_unlock))
            }
        }
    }
}
