package com.muhabbet.app.ui.privacy

import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.muhabbet.app.data.repository.BlockList
import com.muhabbet.app.presentation.TestComponentContext
import com.muhabbet.shared.dto.BlockedUserResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The first reference migration of ADR 0010: a screen's state and I/O moved out of the composable
 * into a retained, JVM-testable Store. Each test here is behaviour that, before the migration,
 * existed only inside `BlockedUsersScreen` and could not be tested at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockedUsersStoreTest {

    private val ayse = BlockedUserResponse(userId = "u-ayse", displayName = "Ayşe", blockedAt = "2026-11-01T10:00:00Z")
    private val mehmet = BlockedUserResponse(userId = "u-mehmet", displayName = "Mehmet", blockedAt = "2026-11-02T10:00:00Z")

    private class FakeBlockList(
        var users: List<BlockedUserResponse> = emptyList(),
        var loadFailure: Throwable? = null,
        var unblockFailure: Throwable? = null,
        val unblockGate: CompletableDeferred<Unit>? = null
    ) : BlockList {
        var loads = 0
        val unblocked = mutableListOf<String>()

        override suspend fun getBlockedUsers(): List<BlockedUserResponse> {
            loads++
            loadFailure?.let { throw it }
            return users
        }

        override suspend fun unblockUser(userId: String) {
            unblockGate?.await()
            unblockFailure?.let { throw it }
            unblocked += userId
        }
    }

    private fun TestScope.store(blockList: BlockList, pending: BlockedUserResponse? = null) =
        BlockedUsersStore(blockList, pending, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun should_showLoadedList_when_loadSucceeds() = runTest {
        val store = store(FakeBlockList(users = listOf(ayse, mehmet)))

        assertEquals(BlockedUsersContent.Loaded(listOf(ayse, mehmet)), store.state.value.content)
    }

    @Test
    fun should_showFailedAndReport_when_loadFails() = runTest {
        val store = store(FakeBlockList(loadFailure = IllegalStateException("503")))

        assertEquals(BlockedUsersContent.Failed, store.state.value.content)
        assertEquals(BlockedUsersEffect.LoadFailed, store.effects.first())
    }

    @Test
    fun should_reload_when_retryAfterFailure() = runTest {
        val blockList = FakeBlockList(loadFailure = IllegalStateException("503"))
        val store = store(blockList)
        blockList.loadFailure = null
        blockList.users = listOf(ayse)

        store.onEvent(BlockedUsersEvent.Retry)

        assertEquals(2, blockList.loads)
        assertEquals(BlockedUsersContent.Loaded(listOf(ayse)), store.state.value.content)
    }

    @Test
    fun should_notUnblock_when_onlyRequested() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse))
        val store = store(blockList)

        store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))

        assertEquals(ayse, store.state.value.pendingUnblock)
        assertTrue(blockList.unblocked.isEmpty(), "a request opens the confirmation; it must not act")
    }

    @Test
    fun should_dropRowAndConfirm_when_unblockConfirmedAndSucceeds() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse, mehmet))
        val store = store(blockList)

        store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))
        store.onEvent(BlockedUsersEvent.UnblockConfirmed)

        assertEquals(listOf("u-ayse"), blockList.unblocked)
        assertNull(store.state.value.pendingUnblock)
        assertEquals(BlockedUsersContent.Loaded(listOf(mehmet)), store.state.value.content)
        assertEquals(BlockedUsersEffect.Unblocked, store.effects.first())
        assertEquals(1, blockList.loads, "a successful unblock must not re-fetch the list")
    }

    @Test
    fun should_keepRowAndReport_when_unblockFails() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse), unblockFailure = IllegalStateException("500"))
        val store = store(blockList)

        store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))
        store.onEvent(BlockedUsersEvent.UnblockConfirmed)

        assertEquals(BlockedUsersContent.Loaded(listOf(ayse)), store.state.value.content)
        assertEquals(BlockedUsersEffect.UnblockFailed, store.effects.first())
    }

    @Test
    fun should_closeConfirmationWithoutActing_when_dismissed() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse))
        val store = store(blockList)

        store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))
        store.onEvent(BlockedUsersEvent.UnblockDismissed)
        store.onEvent(BlockedUsersEvent.UnblockConfirmed)

        assertNull(store.state.value.pendingUnblock)
        assertTrue(blockList.unblocked.isEmpty())
    }

    @Test
    fun should_abandonInFlightWork_when_destroyed() = runTest {
        val gate = CompletableDeferred<Unit>()
        val blockList = FakeBlockList(users = listOf(ayse), unblockGate = gate)
        val store = store(blockList)
        store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))
        store.onEvent(BlockedUsersEvent.UnblockConfirmed)

        store.onDestroy()
        gate.complete(Unit)

        assertTrue(blockList.unblocked.isEmpty(), "work owned by a destroyed entry must be cancelled with it")
    }

    @Test
    fun should_keepTheSameStore_when_componentIsRecreatedWithRetainedInstances() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse))
        val first = TestComponentContext()
        val before = BlockedUsersComponent(first.context, { store(blockList, it) }, onBack = {})

        // A configuration change: a new context, the same InstanceKeeper.
        val second = TestComponentContext(instanceKeeper = first.instanceKeeper)
        val after = BlockedUsersComponent(second.context, { store(blockList, it) }, onBack = {})

        assertSame(before.store, after.store)
        assertEquals(1, blockList.loads, "rotation must not re-fetch the list")
    }

    @Test
    fun should_restoreOpenConfirmation_when_processWasKilled() = runTest {
        val blockList = FakeBlockList(users = listOf(ayse))
        val first = TestComponentContext()
        val before = BlockedUsersComponent(first.context, { store(blockList, it) }, onBack = {})
        before.store.onEvent(BlockedUsersEvent.UnblockRequested(ayse))

        val saved: SerializableContainer = first.stateKeeper.save()
        // Process death: nothing retained, only the saved state comes back.
        val restored = TestComponentContext(savedState = saved)
        val after = BlockedUsersComponent(restored.context, { store(blockList, it) }, onBack = {})

        assertEquals(ayse, after.store.state.value.pendingUnblock)
    }
}
