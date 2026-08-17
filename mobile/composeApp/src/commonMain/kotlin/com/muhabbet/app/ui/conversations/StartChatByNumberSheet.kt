package com.muhabbet.app.ui.conversations

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.muhabbet.app.data.repository.PhoneLookupResult
import com.muhabbet.app.data.repository.PhoneNumberLookup
import com.muhabbet.app.ui.people.PersonByNumberSheet
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Reaching a person by typing their phone number, and opening the chat with them (#389).
 *
 * All the chrome — field, validation, busy label, invite offer, own-number and rejected-lookup
 * endings — is [PersonByNumberSheet]. What is left here is the one thing that makes this sheet
 * different from the group picker's: a hit becomes a direct conversation and a navigation.
 *
 * **Dismisses before navigating.** `onConversationOpened` pushes a screen, which takes the host out
 * of composition and cancels the sheet's coroutine scope — anything queued after it would never run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartChatByNumberSheet(
    onDismiss: () -> Unit,
    onConversationOpened: (id: String, name: String) -> Unit,
    phoneNumberLookup: PhoneNumberLookup = koinInject(),
) {
    // Resolved here rather than inside the suspend lambda: stringResource is @Composable.
    val defaultChatName = stringResource(Res.string.chat_default_name)

    PersonByNumberSheet(
        title = stringResource(Res.string.start_by_number_title),
        actionLabel = stringResource(Res.string.start_by_number_action),
        onDismiss = onDismiss,
        onSubmit = { number ->
            phoneNumberLookup.startChatWith(number).also { result ->
                if (result is PhoneLookupResult.Opened) {
                    onDismiss()
                    onConversationOpened(result.conversationId, result.displayName ?: defaultChatName)
                }
            }
        },
    )
}
