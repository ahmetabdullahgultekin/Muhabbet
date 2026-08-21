package com.muhabbet.app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.muhabbet.app.data.local.ContactsAccess
import com.muhabbet.app.data.local.ContactsAccessController
import com.muhabbet.app.data.repository.KnownPerson
import com.muhabbet.app.data.repository.KnownPeopleSource
import com.muhabbet.app.data.repository.PhoneLookupResult
import com.muhabbet.app.data.repository.PhoneNumberLookup
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.rememberContactsPermissionRequester
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Choosing people, without demanding the address book first.
 *
 * Every member picker in the app used to render exactly one control when `READ_CONTACTS` was not
 * granted: a button that uploads your contacts. On an account with two open conversations that was
 * a dead end — the two people it already knew about, whose user ids it already held and whose rows
 * it already drew in the conversation list, could not be put into a group (#520).
 *
 * Three ways in, in order of how much they cost the user:
 *
 * 1. **People you already have a direct conversation with.** Free, local, and the common case.
 *    Backed by [KnownPeopleSource]; see there for why co-members of a group are excluded.
 * 2. **A typed phone number** — the same lookup `Numara ile sohbet başlat` performs (#389), reusing
 *    the same sheet rather than a second implementation.
 * 3. **The address book**, offered as one more row rather than as a wall, and only ever read after
 *    both the OS permission and the explicit #425 consent. Declining leaves 1 and 2 working, which
 *    is the whole point: consent that costs you group chat is not consent.
 *
 * Failures are reported **inline**, not through a snackbar, because this composable is used inside a
 * `ModalBottomSheet` as well as on a screen, and a snackbar raised from inside a sheet renders
 * behind its scrim.
 *
 * @param onSelectionChanged called with the person's id and whether they are now selected. Not a
 *   toggle: the by-number path must *select*, and a toggle would silently unselect somebody the user
 *   had already picked and then looked up again.
 * @param excludeUserIds people the caller cannot accept — existing group members, above all. Applied
 *   to every source, so a by-number lookup of an existing member cannot slip a duplicate in.
 * @param listMaxHeight caps the scrolling list. Leave null on a screen, where the list should take
 *   the space it is given; set it inside a bottom sheet, where an uncapped `LazyColumn` expands to
 *   full height and pushes the confirm button out of reach.
 */
@Composable
fun PeoplePicker(
    selectedUserIds: Set<String>,
    onSelectionChanged: (userId: String, selected: Boolean) -> Unit,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    excludeUserIds: Set<String> = emptySet(),
    listMaxHeight: Dp? = null,
    knownPeopleSource: KnownPeopleSource = koinInject(),
    phoneNumberLookup: PhoneNumberLookup = koinInject(),
    contactsProvider: ContactsProvider = koinInject(),
    contactsAccessController: ContactsAccessController = koinInject(),
) {
    var fromConversations by remember { mutableStateOf<List<KnownPerson>>(emptyList()) }
    var fromContacts by remember { mutableStateOf<List<KnownPerson>>(emptyList()) }
    // Kept apart from the other two and listed first: somebody the user has just typed a number for
    // has to appear immediately, and must not vanish when a contact sync later replaces its list.
    var fromNumber by remember { mutableStateOf<List<KnownPerson>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showByNumber by remember { mutableStateOf(false) }
    // Something only this picker knows about a person the lookup found — currently just "already in
    // this group". Lives here rather than in the sheet because the sheet has no idea who the caller
    // can accept.
    var byNumberNotice by remember { mutableStateOf<String?>(null) }

    // Both of these came out of this composable's own `remember` until #691, which meant a picker
    // that happened to be composed when the answer changed elsewhere never learned about it — and,
    // worse, that the consent flag had two independent copies, so this picker and
    // `NewConversationScreen` could give different answers to a question with legal weight.
    val access by contactsAccessController.access.collectAsState()
    val hasPermission = access == ContactsAccess.Granted
    // Two separate gates, and both are required before a single hash leaves the phone. The OS
    // permission authorises *reading* the address book; it says nothing about sending anything
    // derived from it to a server, and the people in it are not users of this service (#425).
    // CreateGroupScreen used to check only the first — it synced on permission alone.
    val contactSyncConsented by contactsAccessController.syncConsented.collectAsState()
    var showConsentDialog by remember { mutableStateOf(false) }

    val requestPermission = rememberContactsPermissionRequester { contactsAccessController.refresh() }

    LaunchedEffect(Unit) {
        val loaded = runCatchingCancellable { knownPeopleSource.peopleWithDirectConversations() }
        isLoading = false
        loaded
            .onSuccess { fromConversations = it }
            .onFailure { e ->
                // An empty picker and a failed load must not look the same — "you have nobody to
                // add" is a real answer, and a swallowed error reads as one.
                Log.e(TAG, "Could not load people from conversations", e)
                loadFailed = true
            }
    }

    LaunchedEffect(hasPermission, contactSyncConsented) {
        if (!hasPermission) return@LaunchedEffect
        if (!contactSyncConsented) {
            showConsentDialog = true
            return@LaunchedEffect
        }
        isSyncing = true
        val synced = runCatchingCancellable { knownPeopleSource.peopleFromDeviceContacts() }
        isSyncing = false
        synced
            .onSuccess { fromContacts = it }
            .onFailure { e ->
                Log.e(TAG, "Contact sync failed", e)
                loadFailed = true
            }
    }

    // Concatenated in order of how sure we are the user meant this person — just typed, then talked
    // to, then merely in the address book — with each block already alphabetical. Sorting the whole
    // thing by name instead would bury a number the user typed one second ago somewhere under "S".
    // `distinctBy` keeps the first occurrence, so the strongest source wins a duplicate.
    val people = remember(fromNumber, fromConversations, fromContacts, excludeUserIds) {
        (fromNumber + fromConversations + fromContacts)
            .filter { it.userId !in excludeUserIds }
            .distinctBy { it.userId }
    }
    val visiblePeople = if (searchQuery.isBlank()) {
        people
    } else {
        people.filter { (it.displayName ?: "").contains(searchQuery, ignoreCase = true) }
    }

    if (showConsentDialog) {
        MuhabbetDialog(
            title = stringResource(Res.string.contacts_consent_title),
            onDismiss = { showConsentDialog = false },
            dismissLabel = stringResource(Res.string.contacts_consent_decline),
            confirmLabel = stringResource(Res.string.contacts_consent_accept),
            onConfirm = {
                contactsAccessController.grantSyncConsent()
                showConsentDialog = false
            }
        ) {
            Text(stringResource(Res.string.contacts_consent_body))
        }
    }

    if (showByNumber) {
        val alreadyPresentMsg = stringResource(Res.string.people_picker_already_present)
        PersonByNumberSheet(
            title = stringResource(Res.string.people_picker_by_number_title),
            actionLabel = stringResource(Res.string.people_picker_by_number_action),
            onDismiss = { showByNumber = false },
            notice = byNumberNotice,
            onInputChanged = { byNumberNotice = null },
            onSubmit = { number ->
                byNumberNotice = null
                phoneNumberLookup.findByNumber(number).also { result ->
                    if (result !is PhoneLookupResult.Found) return@also
                    if (result.userId in excludeUserIds) {
                        // Found, and unusable. Saying so beats both a silent tap and "not on
                        // Muhabbet", which would be a lie about somebody standing in the group.
                        byNumberNotice = alreadyPresentMsg
                        return@also
                    }
                    fromNumber = listOf(
                        KnownPerson(result.userId, result.displayName, result.avatarUrl)
                    ) + fromNumber
                    onSelectionChanged(result.userId, true)
                    showByNumber = false
                }
            },
        )
    }

    Column(modifier = modifier) {
        PeoplePickerActionRow(
            icon = Muhabbet.icons.DialPad,
            label = stringResource(Res.string.people_picker_by_number_row),
            onClick = { showByNumber = true }
        )
        HorizontalDivider()

        // Last, not first, and only while there is something left to grant. It is an offer to widen
        // the list, not the price of admission.
        if (!hasPermission || !contactSyncConsented) {
            PeoplePickerActionRow(
                icon = Muhabbet.icons.Contact,
                // Names where the row goes once asking is no longer possible, rather than keeping
                // one label for two destinations. Android stops showing the permission dialog after
                // two refusals, and a row that promises contacts and silently does nothing is worse
                // than no row (#692).
                label = if (access == ContactsAccess.Denied) {
                    stringResource(Res.string.contacts_open_settings)
                } else {
                    stringResource(Res.string.people_picker_find_more_contacts)
                },
                onClick = {
                    when (access) {
                        ContactsAccess.Denied -> contactsProvider.openSystemSettings()
                        ContactsAccess.NotAsked -> {
                            contactsAccessController.onPermissionRequested()
                            requestPermission()
                        }
                        ContactsAccess.Granted -> showConsentDialog = true
                    }
                }
            )
            HorizontalDivider()
        }

        if (people.isNotEmpty()) {
            MuhabbetTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
                placeholder = stringResource(Res.string.contacts_search_placeholder),
                singleLine = true
            )
        }

        PeoplePickerBody(
            people = visiblePeople,
            selectedUserIds = selectedUserIds,
            onSelectionChanged = onSelectionChanged,
            // A spinner only while there is nothing to show. Once the conversation list has
            // arrived, a background contact sync must not blank the rows the user is looking at.
            isLoading = (isLoading || isSyncing) && people.isEmpty(),
            loadFailed = loadFailed && people.isEmpty(),
            emptyLabel = emptyLabel,
            modifier = listMaxHeight
                ?.let { Modifier.fillMaxWidth().heightIn(max = it) }
                ?: Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
private fun PeoplePickerBody(
    people: List<KnownPerson>,
    selectedUserIds: Set<String>,
    onSelectionChanged: (userId: String, selected: Boolean) -> Unit,
    isLoading: Boolean,
    loadFailed: Boolean,
    emptyLabel: String,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> MuhabbetLoadingState()

            loadFailed -> PickerMessage(stringResource(Res.string.error_load_failed))

            people.isEmpty() -> PickerMessage(emptyLabel)

            else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(people, key = { it.userId }) { person ->
                    val isSelected = person.userId in selectedUserIds
                    SelectablePersonRow(
                        person = person,
                        isSelected = isSelected,
                        onToggle = { onSelectionChanged(person.userId, !isSelected) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(MuhabbetSpacing.XLarge)
    )
}

@Composable
private fun SelectablePersonRow(
    person: KnownPerson,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val fallbackName = stringResource(Res.string.unknown)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            // Null: the whole row is the control, and a separately clickable checkbox makes one
            // person read as two targets to a screen reader.
            onCheckedChange = null
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        UserAvatar(
            avatarUrl = person.avatarUrl,
            displayName = person.displayName ?: fallbackName,
            size = MuhabbetSizes.AvatarSmall
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = person.displayName ?: fallbackName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * One tappable "start something new" row — a tinted circular glyph and a label.
 *
 * Shared with `NewConversationScreen`, which is where it started: the number entry, "Yeni Grup" and
 * the contacts offer are the same row three times, and a second copy of the block was how they would
 * have drifted into three different circle sizes.
 */
@Composable
fun PeoplePickerActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(MuhabbetSizes.AvatarSmall).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    // Null: the label beside it already names the action, and announcing it twice
                    // makes the row read as two controls to a screen reader.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(MuhabbetSizes.IconLarge)
                )
            }
        }
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

private const val TAG = "PeoplePicker"
