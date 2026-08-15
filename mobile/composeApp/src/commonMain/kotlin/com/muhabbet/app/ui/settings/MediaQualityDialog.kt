package com.muhabbet.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.SettingsRadioRow

@Composable
fun MediaQualityDialog(
    onDismiss: () -> Unit,
    tokenStorage: TokenStorage = koinInject()
) {
    var selectedQuality by remember { mutableStateOf(tokenStorage.getMediaQuality() ?: "standard") }
    val qualityOptions = listOf(
        "standard" to stringResource(Res.string.media_quality_standard),
        "hd" to stringResource(Res.string.media_quality_hd)
    )

    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.media_quality_title),
        // The close button was in `confirmButton`, which put it on the opposite side from every
        // other dialog's. Nothing here is confirmed — the choice is saved on selection.
        dismissLabel = stringResource(Res.string.action_close),
        content = {
            Column {
                Text(
                    text = stringResource(Res.string.media_quality_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                qualityOptions.forEach { (key, label) ->
                    SettingsRadioRow(
                        title = label,
                        selected = selectedQuality == key,
                        onSelect = {
                            selectedQuality = key
                            tokenStorage.setMediaQuality(key)
                        }
                    )
                }
            }
        }
    )
}
