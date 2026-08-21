package com.muhabbet.app.ui.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muhabbet.app.data.local.ContactsAccessController
import com.muhabbet.app.platform.AppVisibility
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

/**
 * Keeps the app's one contacts-access answer honest while the app is running.
 *
 * Draws nothing. Mounted once, next to the other app-wide gates in `RootContent`.
 *
 * **Why it has to exist.** Contacts access can change while the app is not looking at it, and the
 * app's own copy tells the user to make it change that way — `contacts_permission_denied` reads
 * *"Kişilerinizi görmek için ayarlardan izin verin."* Leaving the app for system settings and
 * coming back pauses the Activity and tears down no composition, so nothing keyed on `Unit`
 * re-runs and nothing held in `remember` is re-read. That is the whole of #691: five separate
 * copies of the answer, none of which could be told that the user had just granted the thing they
 * were complaining about.
 *
 * [AppVisibility] is the signal for this and already exists for the same class of problem (#478).
 * The `filter { it }` has no `drop(1)` on purpose: the flow starts `true`, so the replayed value
 * gives a refresh on mount, and every genuine background→foreground transition gives another. A
 * `StateFlow` conflates repeats, so a pause and resume the collector never runs between costs
 * nothing, and re-reading is a single `checkSelfPermission`.
 */
@Composable
fun ContactsAccessRefreshEffect(
    controller: ContactsAccessController = koinInject(),
    appVisibility: AppVisibility = koinInject()
) {
    LaunchedEffect(Unit) {
        appVisibility.isForeground
            .filter { it }
            .collect { controller.refresh() }
    }
}
