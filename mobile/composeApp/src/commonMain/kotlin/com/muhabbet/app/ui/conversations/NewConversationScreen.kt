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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.rememberContactsPermissionRequester
import com.muhabbet.app.util.sha256Hex
import com.muhabbet.shared.dto.MatchedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onConversationCreated: (id: String, name: String) -> Unit,
    onCreateGroup: () -> Unit = {},
    onBack: () -> Unit,
    conversationRepository: ConversationRepository = koinInject(),
    contactsProvider: ContactsProvider = koinInject()
) {
    var contacts by remember { mutableStateOf<List<MatchedContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(contactsProvider.hasPermission()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultChatName = stringResource(Res.string.chat_default_name)
    val errorMsg = stringResource(Res.string.error_generic)

    val requestPermission = rememberContactsPermissionRequester { granted ->
        hasPermission = granted
        if (!granted) permissionDenied = true
    }

    // Sync contacts when permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission && contacts.isEmpty()) {
            isSyncing = true
            try {
                val deviceContacts = withContext(Dispatchers.Default) {
                    contactsProvider.readContacts()
                }
                val hashes = deviceContacts.mapNotNull { contact ->
                    val digits = contact.phoneNumber.filter { c -> c.isDigit() || c == '+' }
                    normalizeToE164(digits)?.let { sha256Hex(it) }
                }
                if (hashes.isNotEmpty()) {
                    val result = conversationRepository.syncContacts(hashes)
                    contacts = result.matchedContacts
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(errorMsg)
            }
            isSyncing = false
        }
    }

    val filteredContacts = if (searchQuery.isBlank()) contacts
    else contacts.filter { (it.displayName ?: "").contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.new_conversation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                // Not yet granted: show permission prompt
                !hasPermission -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(Res.string.new_conversation_contacts_required),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (permissionDenied)
                                stringResource(Res.string.contacts_permission_denied)
                            else
                                stringResource(Res.string.new_conversation_contacts_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { requestPermission() }) {
                            Text(stringResource(Res.string.contacts_grant_access))
                        }
                    }
                }
                // Syncing contacts
                isSyncing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(Res.string.contacts_syncing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // No matched contacts
                contacts.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(Res.string.contacts_none_found),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                // Show contacts list
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(Res.string.contacts_search_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyColumn {
                            // "Yeni Grup" button at top
                            item(key = "create_group") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onCreateGroup)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Group,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(Res.string.new_conversation_new_group),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                HorizontalDivider()
                            }
                            items(filteredContacts, key = { it.userId }) { contact ->
                                ContactItem(
                                    contact = contact,
                                    defaultName = defaultChatName,
                                    onClick = {
                                        if (isCreating) return@ContactItem
                                        isCreating = true
                                        scope.launch {
                                            try {
                                                val conv = conversationRepository.createDirectConversation(contact.userId)
                                                onConversationCreated(conv.id, contact.displayName ?: defaultChatName)
                                            } catch (_: Exception) {
                                                snackbarHostState.showSnackbar(errorMsg)
                                                isCreating = false
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: MatchedContact,
    defaultName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (contact.displayName ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = contact.displayName ?: defaultName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Normalizes a phone number to E.164 format for Turkish numbers.
 * Handles: +905XX, 905XX, 05XX, 5XX → +90XXXXXXXXX
 * Returns null if the number doesn't look like a Turkish mobile number.
 */
private fun normalizeToE164(phone: String): String? {
    val digits = phone.removePrefix("+")
    return when {
        // Already E.164: +90XXXXXXXXX
        phone.startsWith("+90") && digits.length == 12 -> phone
        // 90XXXXXXXXX (missing +)
        digits.startsWith("90") && digits.length == 12 -> "+$digits"
        // 0XXXXXXXXX (national format with leading 0)
        digits.startsWith("0") && digits.length == 11 -> "+90${digits.drop(1)}"
        // XXXXXXXXX (just the subscriber number, 10 digits starting with 5)
        digits.startsWith("5") && digits.length == 10 -> "+90$digits"
        // Not a recognizable Turkish number — hash as-is (may match other countries)
        phone.startsWith("+") && digits.length >= 10 -> phone
        else -> null
    }
}
