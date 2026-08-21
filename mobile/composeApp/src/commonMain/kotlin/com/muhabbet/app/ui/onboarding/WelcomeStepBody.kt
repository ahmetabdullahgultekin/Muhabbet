package com.muhabbet.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.app.data.local.ContactsAccess
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.contacts_consent_body
import com.muhabbet.composeapp.generated.resources.welcome_contacts_already_body
import com.muhabbet.composeapp.generated.resources.welcome_contacts_denied_note
import com.muhabbet.composeapp.generated.resources.welcome_contacts_title
import com.muhabbet.composeapp.generated.resources.welcome_intro_body
import com.muhabbet.composeapp.generated.resources.welcome_intro_title
import com.muhabbet.composeapp.generated.resources.welcome_ready_body_contacts
import com.muhabbet.composeapp.generated.resources.welcome_ready_body_no_contacts
import com.muhabbet.composeapp.generated.resources.welcome_ready_title_contacts
import com.muhabbet.composeapp.generated.resources.welcome_ready_title_no_contacts
import com.muhabbet.designsystem.components.ChatStartIllustration
import com.muhabbet.designsystem.components.ContactsRingIllustration
import com.muhabbet.designsystem.components.MuhabbetBrandMark
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * What each step of the welcome flow (#692) shows. The container in `WelcomeFlow.kt` owns the
 * buttons, the rail and the sequencing; this file owns only what is being said.
 *
 * Split out for the 300-line rule, and because the split falls on a real seam: every branch here is
 * about copy and artwork, and every decision there is about state.
 */

/**
 * The shape all three steps share: artwork, a title, a paragraph, and optionally one more line in a
 * quieter colour.
 *
 * One layout rather than three, so the steps cannot drift into three different spacings the way this
 * app's top bars once drifted into three different colours.
 */
@Composable
private fun WelcomeStepBody(
    title: String,
    body: String,
    note: String? = null,
    illustration: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        illustration()
        Spacer(Modifier.height(MuhabbetSpacing.XLarge))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MuhabbetSpacing.Medium))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            // onSurfaceVariant, not a faded onSurface: the variant role is the one whose contrast is
            // measured against every surface in all three schemes by SemanticColorContrastTest, and
            // an alpha applied to onSurface is exactly the shape that test cannot see through.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (note != null) {
            Spacer(Modifier.height(MuhabbetSpacing.Medium))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Step one: what this app is, and that there is barely any of this to get through. */
@Composable
internal fun WelcomeIntroStep() {
    WelcomeStepBody(
        title = stringResource(Res.string.welcome_intro_title),
        body = stringResource(Res.string.welcome_intro_body)
    ) {
        MuhabbetBrandMark(size = MuhabbetSizes.OnboardingIllustration)
    }
}

/**
 * Step two: the contacts ask, with the reason stated before the system dialog appears.
 *
 * The body **is** the #425 consent text, reused rather than paraphrased. Two copies of a legal
 * explanation is how the two ends of it drift apart, and this screen is a better place to read it
 * than the dialog it also appears in: there is nothing else on screen competing with it.
 *
 * Reactive on [access] on purpose, and this is where the single-source-of-truth fix (#691) becomes
 * visible to the user: someone who has already refused is sent to system settings, and when they
 * come back having granted it, this step has already changed underneath them — no re-ask, no wall.
 */
@Composable
internal fun WelcomeContactsStep(access: ContactsAccess) {
    WelcomeStepBody(
        title = stringResource(Res.string.welcome_contacts_title),
        body = when (access) {
            // Permission already held: the OS question is settled and only the consent is left, so
            // repeating "we will ask your phone for permission" would describe something that is
            // not about to happen.
            ContactsAccess.Granted -> stringResource(Res.string.welcome_contacts_already_body)
            else -> stringResource(Res.string.contacts_consent_body)
        },
        note = when (access) {
            // Said out loud because Android does not: after the second refusal the dialog never
            // appears again, and a user who does not know that taps the button and sees nothing
            // happen. Naming it is the difference between a broken button and an explained one.
            ContactsAccess.Denied -> stringResource(Res.string.welcome_contacts_denied_note)
            else -> null
        }
    ) {
        ContactsRingIllustration()
    }
}

/**
 * Step three: what to do first — the half of #692 that is not about permissions at all.
 *
 * Two versions, because the honest instruction differs. With contacts matched, the answer is "your
 * people are in the list". Without, the answer is the by-number route (#389), which works with no
 * permission at all and which nobody finds on their own.
 */
@Composable
internal fun WelcomeReadyStep(contactsAvailable: Boolean) {
    WelcomeStepBody(
        title = stringResource(
            if (contactsAvailable) Res.string.welcome_ready_title_contacts
            else Res.string.welcome_ready_title_no_contacts
        ),
        body = stringResource(
            if (contactsAvailable) Res.string.welcome_ready_body_contacts
            else Res.string.welcome_ready_body_no_contacts
        )
    ) {
        ChatStartIllustration()
    }
}
