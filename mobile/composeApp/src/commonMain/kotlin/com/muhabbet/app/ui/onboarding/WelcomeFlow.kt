package com.muhabbet.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.data.local.ContactsAccess
import com.muhabbet.app.data.local.ContactsAccessController
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.rememberContactsPermissionRequester
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.contacts_consent_accept
import com.muhabbet.composeapp.generated.resources.contacts_open_settings
import com.muhabbet.composeapp.generated.resources.welcome_contacts_action
import com.muhabbet.composeapp.generated.resources.welcome_contacts_skip
import com.muhabbet.composeapp.generated.resources.welcome_next
import com.muhabbet.composeapp.generated.resources.welcome_ready_action
import com.muhabbet.composeapp.generated.resources.welcome_skip
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetStepRail
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Test tag on the flow's root, so an instrumented test can assert it appears exactly once. */
const val WelcomeFlowTag = "welcome_flow"

/** The steps, in order. Which of them are used is decided once, at the start — see [WelcomeFlow]. */
private enum class WelcomeStep { Intro, Contacts, Ready }

/**
 * The first-run welcome flow (#692).
 *
 * ## What it is for
 *
 * Two complaints, one screen. Contacts access was never asked for on its own initiative: the only
 * two places that requested it were a button in the middle of a full-screen wall inside "Yeni
 * Sohbet" (`NewConversationScreen`) and a row inside a member picker — both of which you have to
 * find first, which is the thing a user without contacts cannot do. And nothing anywhere told a new
 * account what to do first; it landed on an empty list and stayed there.
 *
 * So the ask becomes a **step of a flow that states its reason before the system dialog appears**,
 * and the flow ends by naming the first thing to do — which, if contacts were declined, is the
 * by-number route (#389) that works without any permission at all.
 *
 * ## Once, and skippable
 *
 * The pattern is `TestBuildNoticeDialog`'s: the flag is read into state once, at first composition,
 * and written when the user finishes **or skips**. Both are the user saying they are done. Unlike
 * that notice the flag is a plain boolean rather than a version, because an app that reintroduces
 * itself after every update is an app that has not understood the introduction was the point.
 *
 * Existing accounts will see it once on the update that ships it. That is deliberate: they are the
 * accounts the complaint came from. Anyone who already has both contacts access and the #425
 * consent gets a two-step flow rather than three — see [includeContactsStep] below.
 *
 * ## The contacts step is reactive, and that is the fix on display
 *
 * A user who has already refused cannot be shown the system dialog again — Android stops offering
 * it after two denials — so they are sent to system settings instead. When they come back, nothing
 * in this composition has been torn down, and before #691 that meant the screen would still be
 * insisting they had no permission. It now reads [ContactsAccessController.access], which
 * `ContactsAccessRefreshEffect` re-reads on every return to the foreground, so the step has already
 * moved on by the time they look at it.
 */
@Composable
fun WelcomeFlow(
    onFinished: () -> Unit,
    controller: ContactsAccessController = koinInject(),
    contactsProvider: ContactsProvider = koinInject()
) {
    val access by controller.access.collectAsState()
    val syncConsented by controller.syncConsented.collectAsState()

    // Fixed at the first composition on purpose. If this recomputed, granting the permission would
    // delete a segment from the rail underneath the user's finger, and the flow would appear to
    // have got shorter because they said yes — which reads as the app having lost its place.
    val steps = remember {
        val needsContacts = access != ContactsAccess.Granted || !syncConsented
        if (needsContacts) WelcomeStep.entries else listOf(WelcomeStep.Intro, WelcomeStep.Ready)
    }
    var index by remember { mutableStateOf(0) }
    val step = steps[index]
    val advance: () -> Unit = { if (index < steps.lastIndex) index++ else onFinished() }

    val requestPermission = rememberContactsPermissionRequester {
        // The reported result is deliberately ignored in favour of re-reading the OS: that read is
        // the one every other surface in the app makes, so there is exactly one answer rather than
        // one answer and one rumour. Moving on either way — a refusal is an answer, and the last
        // step already knows how to say "you can start with a number instead".
        controller.refresh()
        advance()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WelcomeFlowTag)
            // Swallows taps rather than letting them reach the conversation list drawn underneath.
            // An opaque Surface does not block pointer input on its own, and a tap that opened a
            // chat behind the welcome screen would be indistinguishable from the app misbehaving.
            // The changes are consumed explicitly: merely awaiting an event observes it, it does
            // not stop it reaching what is below.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(MuhabbetSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                // No skip on the last step: its primary button already ends the flow, and two
                // controls that do the same thing invite the user to wonder what the difference is.
                if (step != WelcomeStep.Ready) {
                    MuhabbetButton(
                        text = stringResource(Res.string.welcome_skip),
                        onClick = onFinished,
                        role = MuhabbetButtonRole.Text
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = step,
                    // The app's own shared enter/exit pair, so a step change moves like every other
                    // arriving surface in the app rather than like a stock Compose crossfade.
                    transitionSpec = { MuhabbetMotion.enterFadeUp togetherWith MuhabbetMotion.exitFadeDown },
                    label = "welcomeStep"
                ) { current ->
                    when (current) {
                        WelcomeStep.Intro -> WelcomeIntroStep()
                        WelcomeStep.Contacts -> WelcomeContactsStep(access = access)
                        WelcomeStep.Ready -> WelcomeReadyStep(
                            contactsAvailable = access == ContactsAccess.Granted && syncConsented
                        )
                    }
                }
            }

            MuhabbetStepRail(current = index + 1, total = steps.size)
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            WelcomeActions(
                step = step,
                access = access,
                onAdvance = advance,
                onFinished = onFinished,
                onConsentAndRequest = {
                    // Consent first, then the OS. They answer different questions — may we match
                    // your address book, and may we read it — and #425 is the one the user has just
                    // read the explanation for. Recording it before a refusal that may never come
                    // means a later grant from system settings simply starts working, with nothing
                    // left to re-ask.
                    controller.grantSyncConsent()
                    controller.onPermissionRequested()
                    requestPermission()
                },
                onConsentOnly = {
                    controller.grantSyncConsent()
                    advance()
                },
                onOpenSettings = {
                    // No consent recorded here. The button says "open settings", and a button's
                    // label is the whole of what the user agreed to by pressing it.
                    contactsProvider.openSystemSettings()
                }
            )
        }
    }
}

/**
 * The one or two buttons under the rail.
 *
 * Extracted so [WelcomeFlow] stays about sequencing: every branch below is a question of which
 * action is honest in which state, and there are more of them than the flow itself has.
 */
@Composable
private fun WelcomeActions(
    step: WelcomeStep,
    access: ContactsAccess,
    onAdvance: () -> Unit,
    onFinished: () -> Unit,
    onConsentAndRequest: () -> Unit,
    onConsentOnly: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when (step) {
        WelcomeStep.Intro -> MuhabbetButton(
            text = stringResource(Res.string.welcome_next),
            onClick = onAdvance,
            modifier = Modifier.fillMaxWidth()
        )

        WelcomeStep.Contacts -> {
            MuhabbetButton(
                text = when (access) {
                    // Nothing left to ask the OS for, so the button offers the only thing still
                    // outstanding — and says so, rather than promising a dialog that will not open.
                    ContactsAccess.Granted -> stringResource(Res.string.contacts_consent_accept)
                    // The dialog is very likely gone for good. Sending them somewhere that works
                    // beats a button that silently does nothing.
                    ContactsAccess.Denied -> stringResource(Res.string.contacts_open_settings)
                    ContactsAccess.NotAsked -> stringResource(Res.string.welcome_contacts_action)
                },
                onClick = when (access) {
                    ContactsAccess.Granted -> onConsentOnly
                    ContactsAccess.Denied -> onOpenSettings
                    ContactsAccess.NotAsked -> onConsentAndRequest
                },
                modifier = Modifier.fillMaxWidth()
            )
            MuhabbetButton(
                text = stringResource(Res.string.welcome_contacts_skip),
                onClick = onAdvance,
                role = MuhabbetButtonRole.Text
            )
        }

        WelcomeStep.Ready -> MuhabbetButton(
            text = stringResource(Res.string.welcome_ready_action),
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
