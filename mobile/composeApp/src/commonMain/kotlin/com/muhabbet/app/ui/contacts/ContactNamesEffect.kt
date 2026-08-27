package com.muhabbet.app.ui.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.muhabbet.app.data.local.ContactNameDirectory
import com.muhabbet.app.data.local.ContactsAccessController
import org.koin.compose.koinInject

/**
 * Keeps [ContactNameDirectory] in step with whether the app is still allowed to read the address
 * book.
 *
 * Draws nothing. Mounted once, beside [ContactsAccessRefreshEffect] — that one re-reads the OS
 * answer on every return to the foreground, this one acts on it. Keyed on the access flow rather
 * than on `Unit` for the reason #691 documents at length: a grant made in system settings tears
 * down no composition, so an effect keyed on `Unit` would read "denied" once, on the first
 * composition of the session, and never look again.
 *
 * One mount for the whole authenticated session, so every screen that names a person reads names
 * that are already there instead of running its own read. That is the point of the singleton.
 */
@Composable
fun ContactNamesEffect(
    directory: ContactNameDirectory = koinInject(),
    accessController: ContactsAccessController = koinInject()
) {
    val access by accessController.access.collectAsState()
    LaunchedEffect(access) { directory.refresh() }
}

/**
 * The address-book names, for any screen that has to put a name to a person.
 *
 * One line at the call site, which is the point: the alternative was threading the conversation
 * list's map down through the seven surfaces that resolve a name — the home search, the archived
 * list, starred messages, the forward picker, the message-search rows and the notification handler
 * — two of which are nowhere near it in the tree. Nothing here decides *which* name wins; that is
 * [com.muhabbet.app.ui.conversations.toChatTarget], and it stays the only place that decides it.
 *
 * Empty until [ContactNamesEffect] has run and found access, and empty again if access is revoked,
 * so every caller still needs the rest of the resolution behind it.
 */
@Composable
fun rememberContactNames(directory: ContactNameDirectory = koinInject()): Map<String, String> {
    val names by directory.names.collectAsState()
    return names
}
