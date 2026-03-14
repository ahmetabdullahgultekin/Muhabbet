package com.muhabbet.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun MediaQualityDialog(
    onDismiss: () -> Unit,
    tokenStorage: TokenStorage = koinInject()
) {
    var selectedQuality by remember { mutableStateOf(tokenStorage.getMediaQuality() ?: "standard") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.media_quality_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.media_quality_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedQuality = "standard"
                            tokenStorage.setMediaQuality("standard")
                        }
                        .padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    RadioButton(
                        selected = selectedQuality == "standard",
                        onClick = {
                            selectedQuality = "standard"
                            tokenStorage.setMediaQuality("standard")
                        }
                    )
                    Text(
                        text = stringResource(Res.string.media_quality_standard),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedQuality = "hd"
                            tokenStorage.setMediaQuality("hd")
                        }
                        .padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    RadioButton(
                        selected = selectedQuality == "hd",
                        onClick = {
                            selectedQuality = "hd"
                            tokenStorage.setMediaQuality("hd")
                        }
                    )
                    Text(
                        text = stringResource(Res.string.media_quality_hd),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_close))
            }
        }
    )
}
