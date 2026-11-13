package com.muhabbet.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.containerColor

/**
 * A moment the user picked: a calendar date and a wall-clock time.
 *
 * Deliberately *not* an instant. [dateUtcMillis] is the UTC midnight M3's date picker reports for
 * the chosen day, and [hour]/[minute] are wall-clock in whatever zone the caller means. Turning the
 * three into an absolute instant needs a time zone, and a time zone is the caller's business — this
 * module has no `kotlinx-datetime`, and a component that guessed the zone would be wrong for every
 * user who travels.
 */
@Immutable
data class MuhabbetDateTime(
    val dateUtcMillis: Long,
    val hour: Int,
    val minute: Int
)

/** Every word the picker shows, supplied by the caller — the design system carries no strings. */
@Immutable
data class MuhabbetDateTimeLabels(
    val dateTitle: String,
    val timeTitle: String,
    val next: String,
    val back: String,
    val confirm: String
)

/** The two steps of a date-then-time pick. */
enum class MuhabbetDateTimeStep { Date, Time }

/**
 * A date-then-time picker in a bottom sheet.
 *
 * The scheduled-send flow drew this as an `AlertDialog` holding a full M3 `DatePicker`, which on a
 * phone is a dialog taller than the screen with its confirm button scrolled out of reach, and which
 * sat on `surfaceContainerHigh` while every other modal surface in the app sits on the Overlay
 * depth. A sheet is where a picker of this size belongs; this one takes the shared
 * [MuhabbetBottomSheet] chrome and runs the two steps inside it — see [MuhabbetDateTimePicker].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetDateTimeSheet(
    labels: MuhabbetDateTimeLabels,
    onConfirm: (MuhabbetDateTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialDateUtcMillis: Long? = null,
    initialHour: Int = 0,
    initialMinute: Int = 0,
    is24Hour: Boolean = true,
    minDateUtcMillis: Long? = null,
    error: String? = null
) {
    MuhabbetBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        skipPartiallyExpanded = true,
        contentPadding = PaddingValues(bottom = MuhabbetSpacing.Large)
    ) {
        MuhabbetDateTimePicker(
            labels = labels,
            onConfirm = onConfirm,
            initialDateUtcMillis = initialDateUtcMillis,
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = is24Hour,
            minDateUtcMillis = minDateUtcMillis,
            error = error
        )
    }
}

/**
 * The two-step body of [MuhabbetDateTimeSheet], usable on its own wherever a sheet is wrong.
 *
 * Step 1 is the calendar; "next" is enabled only once a day is chosen. Step 2 is the clock, with a
 * way back to the calendar. The steps slide on the shared enter/exit transitions, and advancing
 * ticks `SegmentAdvanced` — the same feel as the sign-up rail, because it is the same idea.
 *
 * @param minDateUtcMillis days before this one cannot be picked (by calendar day, so "today" stays
 *   pickable whatever the time). Null allows any day.
 * @param error shown under the active step — the caller's own validation, such as "that time has
 *   passed", which only the caller can judge because only it knows the zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetDateTimePicker(
    labels: MuhabbetDateTimeLabels,
    onConfirm: (MuhabbetDateTime) -> Unit,
    modifier: Modifier = Modifier,
    initialDateUtcMillis: Long? = null,
    initialHour: Int = 0,
    initialMinute: Int = 0,
    is24Hour: Boolean = true,
    minDateUtcMillis: Long? = null,
    error: String? = null
) {
    val haptics = LocalHaptics.current
    var step by rememberSaveable { mutableStateOf(MuhabbetDateTimeStep.Date) }
    val selectable = remember(minDateUtcMillis) { selectableFrom(minDateUtcMillis) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateUtcMillis,
        initialDisplayedMonthMillis = initialDateUtcMillis,
        selectableDates = selectable
    )
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )
    val container = MuhabbetDepth.Overlay.containerColor()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (step == MuhabbetDateTimeStep.Date) labels.dateTitle else labels.timeTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Small)
                .semantics { heading() }
        )

        AnimatedContent(
            targetState = step,
            transitionSpec = { MuhabbetMotion.enterFadeUp togetherWith MuhabbetMotion.exitFadeDown },
            label = "dateTimeStep"
        ) { shown ->
            when (shown) {
                MuhabbetDateTimeStep.Date -> DatePicker(
                    state = dateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(containerColor = container)
                )

                MuhabbetDateTimeStep.Time -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Large),
                    contentAlignment = Alignment.Center
                ) { TimePicker(state = timeState) }
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = MuhabbetSpacing.XLarge)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small, Alignment.End)
        ) {
            if (step == MuhabbetDateTimeStep.Time) {
                MuhabbetButton(
                    text = labels.back,
                    onClick = { step = MuhabbetDateTimeStep.Date },
                    role = MuhabbetButtonRole.Text
                )
            }
            val selectedDate = dateState.selectedDateMillis
            if (step == MuhabbetDateTimeStep.Date) {
                MuhabbetButton(
                    text = labels.next,
                    enabled = selectedDate != null,
                    onClick = {
                        haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
                        step = MuhabbetDateTimeStep.Time
                    }
                )
            } else {
                MuhabbetButton(
                    text = labels.confirm,
                    enabled = selectedDate != null,
                    onClick = {
                        selectedDate?.let {
                            onConfirm(MuhabbetDateTime(it, timeState.hour, timeState.minute))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Days on or after the calendar day containing [minDateUtcMillis]. Compared by day, not by
 * instant: M3 hands `isSelectableDate` the UTC midnight of each cell, so comparing against the raw
 * minimum would make today unpickable for the rest of the day.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun selectableFrom(minDateUtcMillis: Long?): SelectableDates {
    if (minDateUtcMillis == null) return AllDates
    val minDay = minDateUtcMillis.floorDiv(MillisPerDay)
    val minYear = yearOfUtcDay(minDay)
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            utcTimeMillis.floorDiv(MillisPerDay) >= minDay

        override fun isSelectableYear(year: Int): Boolean = year >= minYear
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val AllDates = object : SelectableDates {}

private const val MillisPerDay: Long = 24L * 60 * 60 * 1000

/** Gregorian year of a day count since 1970-01-01 (Howard Hinnant's civil-from-days). */
internal fun yearOfUtcDay(epochDay: Long): Int {
    val z = epochDay + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val month = if (mp < 10) mp + 3 else mp - 9
    return (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
}
