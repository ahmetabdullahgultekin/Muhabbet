package com.muhabbet.app.navigation

import com.muhabbet.app.ui.profile.DirectChatStore
import com.muhabbet.app.ui.privacy.BlockedUsersStore
import com.muhabbet.app.ui.settings.AppLockSettingsStore
import com.muhabbet.shared.dto.BlockedUserResponse
import com.muhabbet.shared.dto.ConversationResponse

/** Finds a conversation this device can see, or null when it cannot (deleted, left, too far back). */
fun interface ConversationLookup {
    suspend fun find(conversationId: String): ConversationResponse?
}

/**
 * Everything [MainComponent] needs to build its destinations (ADR 0010).
 *
 * Store factories rather than Stores: each destination creates its Store once, through its own
 * `InstanceKeeper`, and a factory is how the component asks for it without knowing how it is wired.
 * Provided by Koin in production (`AppModule`) and by hand in tests, which is the point — the
 * navigation is unit-tested without a DI container.
 */
class MainDependencies(
    val pendingChatOpen: PendingChatOpen,
    val conversationLookup: ConversationLookup,
    val blockedUsersStore: (restoredPendingUnblock: BlockedUserResponse?) -> BlockedUsersStore,
    val appLockSettingsStore: () -> AppLockSettingsStore,
    val directChatStore: (userId: String, knownConversationId: String?) -> DirectChatStore
)
