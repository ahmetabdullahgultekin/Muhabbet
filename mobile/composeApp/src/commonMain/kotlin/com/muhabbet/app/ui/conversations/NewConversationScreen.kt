package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.KnownPerson
import com.muhabbet.app.data.repository.KnownPeopleSource
import com.muhabbet.app.ui.people.PeoplePickerActionRow
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.rememberContactsPermissionRequester
import kotlinx.coroutines.launch
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.designsystem.components.MuhabbetDialog
import kotlin.time.Clock
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onConversationCreated: (id: String, name: String) -> Unit,
    onCreateGroup: () -> Unit = {},
    onBack: () -> Unit,
    /**
     * When set, picking a contact hands the caller the contact instead of opening a conversation.
     * The Calls tab uses this: it had no way to reach a person at all, so calling was only possible
     * from inside an existing chat.
     */
    onContactPicked: ((userId: String, name: String?) -> Unit)? = null,
    conversationRepository: ConversationRepository = koinInject(),
    contactsProvider: ContactsProvider = koinInject(),
    knownPeopleSource: KnownPeopleSource = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var contacts by remember { mutableStateOf<List<KnownPerson>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(contactsProvider.hasPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showStartByNumber by remember { mutableStateOf(false) }

    // The Android READ_CONTACTS permission authorises reading contacts *on the device*. It says
    // nothing about sending anything derived from them to a server, and the people in the address
    // book are not users of this service and never agreed to anything. Until #425 this screen
    // uploaded the whole book the instant the OS permission was granted, while the KVKK texts
    // described contact matching as opt-in on explicit consent — a control that did not exist.
    var contactSyncConsented by remember { mutableStateOf(tokenStorage.getContactSyncConsentAt() != null) }
    var showConsentDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultChatName = stringResource(Res.string.chat_default_name)
    val errorMsg = stringResource(Res.string.error_generic)

    val requestPermission = rememberContactsPermissionRequester { granted ->
        hasPermission = granted
        if (!granted) permissionDenied = true
    }

    // Re-runnable on purpose. This used to be guarded by `contacts.isEmpty()` inside a
    // LaunchedEffect keyed only on `hasPermission`, which meant it ran at most once: after a sync
    // returned anyone, the guard was false forever, and a sync that matched nobody could not retry
    // because the key never changed. Someone who joined Muhabbet after your last sync stayed
    // invisible until the screen was destroyed and recreated, with no way to force it.
    suspend fun syncContacts() {
        if (!hasPermission || !contactSyncConsented || isSyncing) return
        isSyncing = true
        var syncFailed = false
        try {
            // Hashing and matching live in KnownPeopleSource, so this screen and the member pickers
            // cannot drift into two different notions of which numbers count as the same person.
            // An empty device address book is a real answer there, not a reason to skip the call: it
            // clears any stale matches instead of leaving the previous list on screen.
            contacts = knownPeopleSource.peopleFromDeviceContacts()
        } catch (_: Exception) {
            syncFailed = true
        }
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isSyncing = false
        if (syncFailed) snackbarHostState.showSnackbar(errorMsg)
    }

    LaunchedEffect(hasPermission, contactSyncConsented) {
        if (hasPermission && !contactSyncConsented) showConsentDialog = true else syncContacts()
    }

    if (showConsentDialog) {
        MuhabbetDialog(
            title = stringResource(Res.string.contacts_consent_title),
            onDismiss = { showConsentDialog = false },
            dismissLabel = stringResource(Res.string.contacts_consent_decline),
            confirmLabel = stringResource(Res.string.contacts_consent_accept),
            onConfirm = {
                tokenStorage.setContactSyncConsentAt(Clock.System.now().toString())
                contactSyncConsented = true
                showConsentDialog = false
            }
        ) {
            Text(stringResource(Res.string.contacts_consent_body))
        }
    }

    val filteredContacts = if (searchQuery.isBlank()) contacts
    else contacts.filter { (it.displayName ?: "").contains(searchQuery, ignoreCase = true) }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.new_conversation_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back),
                actions = {
                    MuhabbetIconButton(
                        icon = Muhabbet.icons.Refresh,
                        contentDescription = stringResource(Res.string.contacts_refresh),
                        onClick = { scope.launch { syncContacts() } },
                        enabled = hasPermission && !isSyncing
                    )
                }
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Above the `when`, not inside the contacts list, on purpose.
            //
            // This screen has four states and the list is only one of them. The other three —
            // permission not granted, sync running, nobody matched — are precisely when a user has
            // no other way to reach anybody, and an entry point that appears only once you already
            // have matched contacts would not have fixed #389 for the account that needs it. In
            // production that is every account: 3 users, 2 conversations.
            //
            // Hidden when the screen is picking a contact for the caller (the Calls tab): these rows
            // open a chat or a group, which on a "who do you want to call" surface would answer a
            // different question than the one asked.
            //
            // "Yeni Grup" joined the by-number row here for both of those reasons. It used to live
            // inside the contacts `LazyColumn`, which gave it the same two faults: it was invisible
            // in the three states where the list does not render, and on the Calls tab it rendered
            // anyway while doing nothing at all, because that call site never passed `onCreateGroup`
            // and the parameter defaults to an empty lambda. Group calls do not exist (#367–#373),
            // so there is no version of this row that belongs on the call surface — the fix is to
            // hide it there, not to pass the lambda through.
            if (onContactPicked == null) {
                PeoplePickerActionRow(
                    icon = Muhabbet.icons.GroupOutlined,
                    label = stringResource(Res.string.new_conversation_new_group),
                    onClick = onCreateGroup
                )
                HorizontalDivider()
                PeoplePickerActionRow(
                    icon = Muhabbet.icons.DialPad,
                    label = stringResource(Res.string.start_by_number_row),
                    onClick = { showStartByNumber = true }
                )
                HorizontalDivider()
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    // Not yet granted: show permission prompt
                    !hasPermission -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(MuhabbetSpacing.XLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Muhabbet.icons.Contact,
                                contentDescription = stringResource(Res.string.cd_contacts),
                                modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            Text(
                                stringResource(Res.string.new_conversation_contacts_required),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Small))
                            Text(
                                text = if (permissionDenied)
                                    stringResource(Res.string.contacts_permission_denied)
                                else
                                    stringResource(Res.string.new_conversation_contacts_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            MuhabbetButton(
                                text = stringResource(Res.string.contacts_grant_access),
                                onClick = { requestPermission() },
                                role = MuhabbetButtonRole.Primary
                            )
                        }
                    }
                    // Permission granted, consent refused. Without this the screen would sit on an
                    // empty list with no explanation and no way back — declining has to leave a
                    // usable app, not a dead end. Starting a chat by number still works from the
                    // row above, so nothing here is a hostage.
                    !contactSyncConsented -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(MuhabbetSpacing.XLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Muhabbet.icons.Contact,
                                contentDescription = stringResource(Res.string.cd_contacts),
                                modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            Text(
                                stringResource(Res.string.contacts_consent_declined),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            MuhabbetButton(
                                text = stringResource(Res.string.contacts_consent_review),
                                onClick = { showConsentDialog = true },
                                role = MuhabbetButtonRole.Primary
                            )
                        }
                    }
                    // Syncing contacts
                    isSyncing -> MuhabbetLoadingState(
                        label = stringResource(Res.string.contacts_syncing)
                    )
                    // No matched contacts
                    contacts.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(MuhabbetSpacing.XLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Muhabbet.icons.Contact,
                                contentDescription = stringResource(Res.string.cd_contacts),
                                modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            Text(
                                stringResource(Res.string.contacts_none_found),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    // Show contacts list
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            MuhabbetTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
                                placeholder = stringResource(Res.string.contacts_search_placeholder),
                                singleLine = true
                            )
                            LazyColumn {
                                items(filteredContacts, key = { it.userId }) { contact ->
                                    ContactItem(
                                        contact = contact,
                                        defaultName = defaultChatName,
                                        onClick = {
                                            if (isCreating) return@ContactItem
                                            if (onContactPicked != null) {
                                                onContactPicked(contact.userId, contact.displayName)
                                                return@ContactItem
                                            }
                                            isCreating = true
                                            scope.launch {
                                                try {
                                                    val conv = conversationRepository.createDirectConversation(contact.userId)
                                                    onConversationCreated(conv.id, contact.displayName ?: defaultChatName)
                                                } catch (_: Exception) {
                                                    // Clear the spinner BEFORE reporting —
                                                    // showSnackbar suspends until dismissed (~4s).
                                                    isCreating = false
                                                    snackbarHostState.showSnackbar(errorMsg)
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                if (isCreating) {
                    MuhabbetLoadingState()
                }
            }
        }

        if (showStartByNumber) {
            StartChatByNumberSheet(
                onDismiss = { showStartByNumber = false },
                onConversationOpened = onConversationCreated
            )
        }
    }
}

@Composable
private fun ContactItem(
    contact: KnownPerson,
    defaultName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = contact.avatarUrl,
            displayName = contact.displayName ?: defaultName,
            size = MuhabbetSizes.AvatarSmall
        )

        Spacer(Modifier.width(MuhabbetSpacing.Medium))

        Text(
            text = contact.displayName ?: defaultName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

// normalizeToE164 imported from com.muhabbet.app.util.PhoneNormalization
