package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.app.data.repository.DeviceLinkRepository
import com.muhabbet.app.multidevice.MultiDeviceConfig
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.DeviceLinkQrPayload
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Device-linking transport/UX scaffolding (Tier 2, NON-CRYPTO slice).
 *
 * On the PRIMARY device this screen opens a link session and shows the QR payload that a companion
 * scans. Rendering the token into an actual QR bitmap, and scanning one on the companion, are
 * delegated to a platform seam ([DeviceLinkQrRenderer] / camera scanner — see the inline notes):
 * those are platform-specific (Android CameraX/ML-Kit, iOS AVFoundation) and intentionally left as
 * an `expect`/adapter boundary so this common screen compiles and is reviewable now. No crypto runs
 * here — the QR carries only the public link token (see [DeviceLinkQrPayload]).
 *
 * Gated by [MultiDeviceConfig.ENABLED]; callers must not navigate here when the flag is OFF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDeviceScreen(
    apiBaseUrl: String,
    onBack: () -> Unit,
    repository: DeviceLinkRepository = koinInject()
) {
    var qrPayloadJson by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val title = stringResource(Res.string.link_device_show_qr_title)
    val instructions = stringResource(Res.string.link_device_show_qr_instructions)
    val cryptoPending = stringResource(Res.string.link_device_crypto_pending)
    val failedText = stringResource(Res.string.link_device_failed)

    LaunchedEffect(Unit) {
        if (!MultiDeviceConfig.ENABLED) {
            isLoading = false
            return@LaunchedEffect
        }
        runCatching { repository.beginLink() }
            .onSuccess { begin ->
                // The QR carries ONLY the public token + the API base, never key material.
                qrPayloadJson = buildQrPayloadJson(
                    DeviceLinkQrPayload(linkToken = begin.linkToken, apiBaseUrl = apiBaseUrl)
                )
            }
            .onFailure { error = failedText }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val currentError = error
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                currentError != null -> Text(currentError, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(MuhabbetSpacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // QR render seam: a platform DeviceLinkQrRenderer (expect/actual) will draw
                    // qrPayloadJson into an Image here. Until then we show the encoded payload so
                    // the flow is reviewable + testable end-to-end without a QR dependency.
                    Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                        Box(modifier = Modifier.fillMaxSize().padding(MuhabbetSpacing.Large), contentAlignment = Alignment.Center) {
                            Text(
                                text = qrPayloadJson ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(MuhabbetSpacing.Large))
                    Text(instructions, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(MuhabbetSpacing.Medium))
                    // Honest disclosure: registration works, but cross-device E2E sync is pending
                    // the (blocked) crypto slice. Never imply encryption that isn't there.
                    Text(
                        cryptoPending,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Serialize the QR payload to the compact JSON the companion scanner decodes. */
private fun buildQrPayloadJson(payload: DeviceLinkQrPayload): String =
    kotlinx.serialization.json.Json.encodeToString(DeviceLinkQrPayload.serializer(), payload)
