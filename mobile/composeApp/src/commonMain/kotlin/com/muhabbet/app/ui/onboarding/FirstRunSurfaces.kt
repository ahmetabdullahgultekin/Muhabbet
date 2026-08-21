package com.muhabbet.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.contacts.ContactsAccessRefreshEffect
import com.muhabbet.app.ui.notice.TestBuildNoticeDialog
import com.muhabbet.app.ui.notification.NotificationPermissionGate
import org.koin.compose.koinInject

/**
 * Everything the app says to a user in their first seconds after signing in, in an order.
 *
 * There are three of these now and they used to be two, mounted side by side in `RootContent` with
 * nothing deciding which came first. That was already a little uncomfortable — the notification
 * permission dialog is documented as being allowed to land on top of the test-build notice — and
 * adding a full-screen welcome flow to the same pile would have made it genuinely bad: a system
 * dialog appearing over step one of an introduction the user has not read yet.
 *
 * So the welcome flow (#692) runs alone and to completion. Only when it is finished or skipped do
 * the other two appear, in the order they always had. The user gets one thing at a time, and the
 * one that explains the app comes before the one that asks for something.
 *
 * [ContactsAccessRefreshEffect] is mounted unconditionally and outside that sequencing: it draws
 * nothing and it must keep working for the whole session, not just during onboarding — the round
 * trip through system settings that #691 is about happens most often long after the welcome flow
 * has been dismissed, from `NewConversationScreen`.
 */
@Composable
fun FirstRunSurfaces(tokenStorage: TokenStorage = koinInject()) {
    ContactsAccessRefreshEffect()

    // Read once into state, following TestBuildNoticeDialog: re-reading storage on every
    // recomposition would put a synchronous preferences read on every frame of the navigation host,
    // and would read back the newly written flag in the frame between "finished" and "gone".
    var welcomeDone by remember { mutableStateOf(tokenStorage.getWelcomeSeen()) }

    if (!welcomeDone) {
        WelcomeFlow(
            onFinished = {
                tokenStorage.setWelcomeSeen()
                welcomeDone = true
            }
        )
        return
    }

    TestBuildNoticeDialog()
    NotificationPermissionGate()
}
