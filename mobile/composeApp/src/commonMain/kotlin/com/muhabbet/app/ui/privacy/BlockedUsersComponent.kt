package com.muhabbet.app.ui.privacy

import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.presentation.retainedStore
import com.muhabbet.app.presentation.saved
import com.muhabbet.shared.dto.BlockedUserResponse

/**
 * The Blocked Users destination (ADR 0010): owns the retained [store] and the one navigation output.
 *
 * The open confirmation survives process death through `StateKeeper`; everything else is cheap to
 * re-fetch and is deliberately not saved.
 */
class BlockedUsersComponent(
    componentContext: ComponentContext,
    storeFactory: (restoredPendingUnblock: BlockedUserResponse?) -> BlockedUsersStore,
    val onBack: () -> Unit
) : ComponentContext by componentContext {

    // The supplier runs at save time, long after construction, so reading [store] from it is safe.
    private val restoredPending: BlockedUserResponse? =
        stateKeeper.saved(PENDING_UNBLOCK_KEY, BlockedUserResponse.serializer()) {
            store.state.value.pendingUnblock
        }

    val store: BlockedUsersStore = retainedStore { storeFactory(restoredPending) }

    private companion object {
        const val PENDING_UNBLOCK_KEY = "blockedUsers.pendingUnblock"
    }
}
