package com.muhabbet.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.date_weekday_friday
import com.muhabbet.composeapp.generated.resources.date_weekday_monday
import com.muhabbet.composeapp.generated.resources.date_weekday_saturday
import com.muhabbet.composeapp.generated.resources.date_weekday_sunday
import com.muhabbet.composeapp.generated.resources.date_weekday_thursday
import com.muhabbet.composeapp.generated.resources.date_weekday_tuesday
import com.muhabbet.composeapp.generated.resources.date_weekday_wednesday
import com.muhabbet.composeapp.generated.resources.date_yesterday
import org.jetbrains.compose.resources.stringResource

/**
 * The localized day names [DateTimeFormatter.formatConversationTimestamp] needs.
 *
 * One composable rather than eight `stringResource` calls at each site, so that "Monday is index 0"
 * is stated once. Getting that order wrong shows a plausible wrong weekday, which is the kind of bug
 * nobody reports because it looks like a value, not an error.
 */
@Composable
fun rememberRelativeDayLabels(): DateTimeFormatter.RelativeDayLabels {
    val yesterday = stringResource(Res.string.date_yesterday)
    val monday = stringResource(Res.string.date_weekday_monday)
    val tuesday = stringResource(Res.string.date_weekday_tuesday)
    val wednesday = stringResource(Res.string.date_weekday_wednesday)
    val thursday = stringResource(Res.string.date_weekday_thursday)
    val friday = stringResource(Res.string.date_weekday_friday)
    val saturday = stringResource(Res.string.date_weekday_saturday)
    val sunday = stringResource(Res.string.date_weekday_sunday)

    // Keyed on the strings themselves, so switching language in Settings rebuilds the list. Keyed on
    // Unit it would keep the previous language's day names until the process restarted.
    return remember(yesterday, monday, tuesday, wednesday, thursday, friday, saturday, sunday) {
        DateTimeFormatter.RelativeDayLabels(
            yesterday = yesterday,
            weekdays = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)
        )
    }
}
