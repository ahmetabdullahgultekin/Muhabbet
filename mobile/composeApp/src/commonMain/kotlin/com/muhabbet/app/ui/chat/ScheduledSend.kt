package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/**
 * A message the user scheduled during this chat session that has not yet been delivered.
 * Tracked client-side so the user can review and cancel pending sends; cancellation reuses
 * the existing delete-message endpoint (the backend stores scheduled messages and a job
 * delivers them, so deleting before delivery cancels the send).
 */
data class PendingScheduledMessage(
    val messageId: String,
    val content: String,
    val scheduledAtMillis: Long
)

/**
 * Two-step date + time picker for scheduling an outgoing message.
 * Returns the chosen send time as epoch millis via [onConfirm]; only times strictly in the
 * future are accepted. Pure UI — no send logic lives here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSendDialog(
    onConfirm: (epochMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ScheduleStep.DATE) }
    val nowLocal = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = Clock.System.now().toEpochMilliseconds()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = nowLocal.hour,
        initialMinute = nowLocal.minute,
        is24Hour = true
    )

    val invalidPastText = stringResource(Res.string.schedule_error_past)
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (step == ScheduleStep.DATE) Res.string.schedule_pick_date
                    else Res.string.schedule_pick_time
                )
            )
        },
        text = {
            Column {
                when (step) {
                    ScheduleStep.DATE -> DatePicker(state = datePickerState, showModeToggle = false)
                    ScheduleStep.TIME -> Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { TimePicker(state = timePickerState) }
                }
                errorText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = MuhabbetSpacing.Small)
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                ScheduleStep.DATE -> TextButton(
                    onClick = { errorText = null; step = ScheduleStep.TIME },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text(stringResource(Res.string.schedule_next)) }

                ScheduleStep.TIME -> TextButton(
                    onClick = {
                        val dateMillis = datePickerState.selectedDateMillis ?: return@TextButton
                        val epochMillis = combineDateAndTime(
                            dateMillis = dateMillis,
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        if (epochMillis <= Clock.System.now().toEpochMilliseconds()) {
                            errorText = invalidPastText
                        } else {
                            onConfirm(epochMillis)
                        }
                    }
                ) { Text(stringResource(Res.string.schedule_confirm)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}

/**
 * Compact chip surfacing the count of pending scheduled messages; tapping opens the list.
 * Rendered above the input bar only when there is at least one pending message.
 */
@Composable
fun ScheduledMessagesChip(
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(MuhabbetSpacing.Large),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(
            horizontal = MuhabbetSpacing.Medium,
            vertical = MuhabbetSpacing.XSmall
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MuhabbetSpacing.Medium,
                vertical = MuhabbetSpacing.Small
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = stringResource(Res.string.schedule_pending_count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Bottom-sheet-style dialog listing pending scheduled messages with a cancel action per item.
 * Cancellation is delegated to the caller (which reuses the existing delete-message path).
 */
@Composable
fun ScheduledMessagesDialog(
    pending: List<PendingScheduledMessage>,
    onCancelScheduled: (PendingScheduledMessage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.schedule_list_title)) },
        text = {
            if (pending.isEmpty()) {
                Text(
                    text = stringResource(Res.string.schedule_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(pending, key = { it.messageId }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MuhabbetSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = DateTimeFormatter.formatFullTimestampMillis(item.scheduledAtMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onCancelScheduled(item) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(Res.string.schedule_cancel),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        }
    )
}

private enum class ScheduleStep { DATE, TIME }

/**
 * Combines a UTC-midnight date (as returned by Material3 [DatePicker]) with a wall-clock
 * hour/minute in the device's time zone, yielding the absolute send instant in epoch millis.
 */
private fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(dateMillis)
        .toLocalDateTime(TimeZone.UTC)
        .date
    val localDateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
    return localDateTime.toInstant(tz).toEpochMilliseconds()
}
