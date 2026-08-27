package com.muhabbet.app.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.contacts.ContactNamesEffect
import com.muhabbet.app.ui.contacts.ContactsAccessRefreshEffect
import com.muhabbet.app.ui.notice.TestBuildNoticeDialog
import com.muhabbet.app.ui.notification.NotificationPermissionGate
import com.muhabbet.app.ui.whatsnew.WhatsNewSheet
import org.koin.compose.koinInject

/**
 * Everything the app says to a user in their first seconds after signing in, in an order.
 *
 * There are four of these now and they used to be two, mounted side by side in `RootContent` with
 * nothing deciding which came first. That was already a little uncomfortable — the notification
 * permission dialog is documented as being allowed to land on top of the test-build notice — and
 * adding a full-screen welcome flow to the same pile would have made it genuinely bad: a system
 * dialog appearing over step one of an introduction the user has not read yet.
 *
 * So the welcome flow (#692) runs alone and to completion. Only when it is finished or skipped do
 * the rest appear, in the order they always had. The user gets one thing at a time, and the one
 * that explains the app comes before the one that asks for something.
 *
 * The release-notes sheet (#672) is last, and queues behind the test-build notice rather than
 * stacking on it: an update that brings new release notes is by definition a new build number, so
 * both fall due on the same launch. The notice sets the expectation that this is a build under
 * test, and only then does the sheet say what changed. The other way round, the caveat arrives as
 * an afterthought to a celebration, which is how a warning stops being read.
 *
 * [ContactsAccessRefreshEffect] and [ContactNamesEffect] are mounted unconditionally and outside
 * that sequencing: they draw nothing and must keep working for the whole session, not just during
 * onboarding — the round trip through system settings that #691 is about happens most often long
 * after the welcome flow has been dismissed, from `NewConversationScreen`.
 */
@Composable
fun FirstRunSurfaces(noticePending: Boolean, tokenStorage: TokenStorage = koinInject()) {
    ContactsAccessRefreshEffect()
    // Mounted next to it and for the same reason: the address-book names every screen resolves a
    // person's name from (#549) are read here, once, rather than by each screen that shows a name.
    ContactNamesEffect()

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

    // Whether the notice is still owed is read at RootComponent construction and passed in, because
    // that is the last moment it is still true: the dialog writes its acknowledgement the instant it
    // is dismissed, so asking storage from here would always answer "no".
    var noticeStillUp by remember { mutableStateOf(noticePending) }

    TestBuildNoticeDialog(onDismissed = { noticeStillUp = false })
    NotificationPermissionGate()

    if (!noticeStillUp) {
        WhatsNewSheet()
    }
}
