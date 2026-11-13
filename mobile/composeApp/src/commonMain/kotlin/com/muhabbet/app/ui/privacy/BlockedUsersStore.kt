package com.muhabbet.app.ui.privacy

import com.muhabbet.app.data.repository.BlockList
import com.muhabbet.app.presentation.Store
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.dto.BlockedUserResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** What the list area shows. Three states, so a sealed type rather than two booleans that can disagree. */
sealed interface BlockedUsersContent {
    data object Loading : BlockedUsersContent
    data object Failed : BlockedUsersContent
    /** An empty [users] is a real answer — nobody is blocked — and renders the empty state. */
    data class Loaded(val users: List<BlockedUserResponse>) : BlockedUsersContent
}

data class BlockedUsersUiState(
    val content: BlockedUsersContent = BlockedUsersContent.Loading,
    /** The row whose unblock is awaiting confirmation; non-null means the dialog is open. */
    val pendingUnblock: BlockedUserResponse? = null
)

sealed interface BlockedUsersEvent {
    data object Retry : BlockedUsersEvent
    data class UnblockRequested(val user: BlockedUserResponse) : BlockedUsersEvent
    data object UnblockConfirmed : BlockedUsersEvent
    data object UnblockDismissed : BlockedUsersEvent
}

sealed interface BlockedUsersEffect {
    data object LoadFailed : BlockedUsersEffect
    data object Unblocked : BlockedUsersEffect
    data object UnblockFailed : BlockedUsersEffect
}

/**
 * The Blocked Users screen's state (#613), retained across rotation (ADR 0010).
 *
 * The first reference migration: the screen used to hold five `mutableStateOf`s, a `retryKey`
 * counter and a `rememberCoroutineScope`, so rotating the phone re-fetched the list and an unblock in
 * flight was cancelled with the composition that started it.
 *
 * @param restoredPendingUnblock a confirmation that was open when the process was killed, restored
 *   from the component's `StateKeeper` so the user returns to the question they were answering.
 */
class BlockedUsersStore(
    private val blockList: BlockList,
    restoredPendingUnblock: BlockedUserResponse? = null,
    context: CoroutineContext = Dispatchers.Main.immediate
) : Store<BlockedUsersUiState, BlockedUsersEvent, BlockedUsersEffect>(
    initialState = BlockedUsersUiState(pendingUnblock = restoredPendingUnblock),
    context = context
) {

    private var loadJob: Job? = null

    init {
        load()
    }

    override fun onEvent(event: BlockedUsersEvent) {
        when (event) {
            BlockedUsersEvent.Retry -> load()
            is BlockedUsersEvent.UnblockRequested -> updateState { it.copy(pendingUnblock = event.user) }
            BlockedUsersEvent.UnblockDismissed -> updateState { it.copy(pendingUnblock = null) }
            BlockedUsersEvent.UnblockConfirmed -> confirmUnblock()
        }
    }

    private fun load() {
        // A retry tapped twice must not race two loads into the same state.
        if (loadJob?.isActive == true) return
        updateState { it.copy(content = BlockedUsersContent.Loading) }
        loadJob = scope.launch {
            runCatchingCancellable { blockList.getBlockedUsers() }
                .onSuccess { users -> updateState { it.copy(content = BlockedUsersContent.Loaded(users)) } }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load blocked users", e)
                    updateState { it.copy(content = BlockedUsersContent.Failed) }
                    emit(BlockedUsersEffect.LoadFailed)
                }
        }
    }

    private fun confirmUnblock() {
        val target = currentState.pendingUnblock ?: return
        updateState { it.copy(pendingUnblock = null) }
        scope.launch {
            runCatchingCancellable { blockList.unblockUser(target.userId) }
                .onSuccess {
                    // Drop the row locally rather than re-fetching the list — the server has nothing
                    // more to say about a block that no longer exists.
                    updateState { state ->
                        val content = state.content
                        if (content is BlockedUsersContent.Loaded) {
                            state.copy(content = content.copy(users = content.users.filterNot { it.userId == target.userId }))
                        } else {
                            state
                        }
                    }
                    emit(BlockedUsersEffect.Unblocked)
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to unblock ${target.userId}", e)
                    emit(BlockedUsersEffect.UnblockFailed)
                }
        }
    }

    private companion object {
        const val TAG = "BlockedUsersStore"
    }
}
