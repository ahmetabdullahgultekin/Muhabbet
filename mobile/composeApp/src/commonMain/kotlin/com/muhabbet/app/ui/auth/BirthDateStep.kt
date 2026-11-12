package com.muhabbet.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.util.BIRTH_DAY_DIGITS
import com.muhabbet.app.util.BIRTH_MONTH_DIGITS
import com.muhabbet.app.util.BIRTH_YEAR_DIGITS
import com.muhabbet.app.util.BirthDateCheck
import com.muhabbet.app.util.checkBirthDate
import com.muhabbet.app.util.todayForAgeRule
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.birth_date_day
import com.muhabbet.composeapp.generated.resources.birth_date_error_below_minimum
import com.muhabbet.composeapp.generated.resources.birth_date_error_invalid
import com.muhabbet.composeapp.generated.resources.birth_date_month
import com.muhabbet.composeapp.generated.resources.birth_date_subtitle
import com.muhabbet.composeapp.generated.resources.birth_date_title
import com.muhabbet.composeapp.generated.resources.birth_date_year
import com.muhabbet.composeapp.generated.resources.phone_continue
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.validation.ValidationRules
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The date-of-birth step of sign-in (#751), shown only when the server has asked for it: for a
 * number with no account yet (`AUTH_AGE_REQUIRED`), or for an account from before the age gate
 * (`ageVerificationRequired`).
 *
 * Says what is asked and why, and nothing about legal compliance — whether a self-declared date
 * satisfies the law is an open counsel question (Q-L3).
 *
 * @param sendBelowMinimum whether a date under the minimum age still goes to the server. True when
 *   declaring for an existing account, so the server records it and a second try with another year
 *   is refused too; false at registration, where the server would refuse it and record nothing, so
 *   saying so here saves a round trip and an OTP attempt.
 * @param secondaryAction a way out (change number, sign out), drawn under the main button.
 */
@Composable
fun BirthDateStep(
    isLoading: Boolean,
    error: String?,
    sendBelowMinimum: Boolean,
    onSubmit: (LocalDate) -> Unit,
    secondaryActionLabel: String,
    onSecondaryAction: () -> Unit
) {
    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val invalidMsg = stringResource(Res.string.birth_date_error_invalid)
    val belowMinimumMsg = stringResource(Res.string.birth_date_error_below_minimum, ValidationRules.MIN_AGE_YEARS)
    val check = checkBirthDate(day, month, year, todayForAgeRule())

    val submit: () -> Unit = {
        when (check) {
            BirthDateCheck.Incomplete -> {}
            BirthDateCheck.Invalid -> {
                localError = invalidMsg
            }
            is BirthDateCheck.BelowMinimum -> {
                if (sendBelowMinimum) onSubmit(check.date) else localError = belowMinimumMsg
            }
            is BirthDateCheck.Valid -> onSubmit(check.date)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuhabbetGradients.brandBackdrop)
            .safeDrawingPadding()
            .padding(MuhabbetSpacing.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.birth_date_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = stringResource(Res.string.birth_date_subtitle, ValidationRules.MIN_AGE_YEARS),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        // Three fields in the order Turkish and most of Europe write a date. Digits only, capped at
        // each part's width, so the only invalid input left is a date the calendar does not have.
        Row(horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)) {
            DatePartField(
                value = day,
                onValueChange = { day = it; localError = null },
                label = stringResource(Res.string.birth_date_day),
                maxDigits = BIRTH_DAY_DIGITS,
                tag = "birth_day_input",
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            )
            DatePartField(
                value = month,
                onValueChange = { month = it; localError = null },
                label = stringResource(Res.string.birth_date_month),
                maxDigits = BIRTH_MONTH_DIGITS,
                tag = "birth_month_input",
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            )
            DatePartField(
                value = year,
                onValueChange = { year = it; localError = null },
                label = stringResource(Res.string.birth_date_year),
                maxDigits = BIRTH_YEAR_DIGITS,
                tag = "birth_year_input",
                enabled = !isLoading,
                modifier = Modifier.weight(YEAR_FIELD_WEIGHT)
            )
        }

        (localError ?: error)?.let {
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        MuhabbetButton(
            text = stringResource(Res.string.phone_continue),
            onClick = submit,
            enabled = !isLoading && check != BirthDateCheck.Incomplete,
            modifier = Modifier.fillMaxWidth().testTag("birth_date_submit")
        )

        Spacer(Modifier.height(MuhabbetSpacing.Medium))

        MuhabbetButton(
            text = secondaryActionLabel,
            onClick = onSecondaryAction,
            role = MuhabbetButtonRole.Text
        )
    }
}

/** The year holds twice the digits of the other two, so it gets a wider share of the row. */
private const val YEAR_FIELD_WEIGHT = 1.5f

@Composable
private fun DatePartField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxDigits: Int,
    tag: String,
    enabled: Boolean,
    modifier: Modifier
) {
    MuhabbetTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter { it.isDigit() }.take(maxDigits)) },
        modifier = modifier.testTag(tag),
        label = label,
        enabled = enabled,
        keyboardType = KeyboardType.Number
    )
}

/** The server's refusals for a date of birth, in the device's language; null for anything else. */
internal fun birthDateErrorFor(e: Throwable, invalid: String, belowMinimum: String): String? =
    when ((e as? ApiException)?.code) {
        "AUTH_BIRTH_DATE_INVALID" -> invalid
        "AUTH_AGE_BELOW_MINIMUM" -> belowMinimum
        else -> null
    }

/** Whether [e] means "this number has no account yet and needs a date of birth to get one" (#751). */
internal fun requiresBirthDate(e: Throwable): Boolean =
    (e as? ApiException)?.code == "AUTH_AGE_REQUIRED"
