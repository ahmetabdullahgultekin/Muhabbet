package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutePickerDialog(
    onDismiss: () -> Unit,
    onMuteDuration: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    val options = listOf(
        "8h" to stringResource(Res.string.mute_8_hours),
        "1w" to stringResource(Res.string.mute_1_week),
        "always" to stringResource(Res.string.mute_always)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(MuhabbetSpacing.XLarge)
        ) {
            Text(
                text = stringResource(Res.string.mute_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            options.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onMuteDuration(key)
                            onDismiss()
                        }
                        .padding(vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = false,
                        onClick = {
                            onMuteDuration(key)
                            onDismiss()
                        }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = MuhabbetSpacing.Small)
                    )
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Large))
        }
    }
}
