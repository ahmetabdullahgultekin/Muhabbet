package com.muhabbet.app.ui.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The profile's "Message" action (#806): it opens the direct conversation, never just pops. */
@OptIn(ExperimentalCoroutinesApi::class)
class DirectChatStoreTest {

    private class FakeOpener(
        private val answer: String = "dm-1",
        private val failure: Throwable? = null,
        private val gate: CompletableDeferred<Unit>? = null
    ) : DirectChatOpener {
        val requests = mutableListOf<String>()
        override suspend fun conversationWith(userId: String): String {
            requests += userId
            gate?.await()
            failure?.let { throw it }
            return answer
        }
    }

    private fun TestScope.store(opener: DirectChatOpener, known: String? = null) =
        DirectChatStore("u-1", known, opener, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun should_openTheKnownConversationWithoutARequest_when_profileCameFromTheChat() = runTest {
        val opener = FakeOpener()
        val store = store(opener, known = "dm-known")

        store.onEvent(DirectChatEvent.MessageClicked)

        assertEquals(DirectChatEffect.OpenChat("dm-known"), store.effects.first())
        assertTrue(opener.requests.isEmpty())
    }

    @Test
    fun should_resolveTheDirectConversation_when_profileCameFromAGroup() = runTest {
        val opener = FakeOpener(answer = "dm-42")
        val store = store(opener)

        store.onEvent(DirectChatEvent.MessageClicked)

        assertEquals(listOf("u-1"), opener.requests)
        assertEquals(DirectChatEffect.OpenChat("dm-42"), store.effects.first())
    }

    @Test
    fun should_reportFailure_when_theConversationCannotBeOpened() = runTest {
        val store = store(FakeOpener(failure = IllegalStateException("403")))

        store.onEvent(DirectChatEvent.MessageClicked)

        assertEquals(DirectChatEffect.Failed, store.effects.first())
        assertEquals(false, store.state.value.opening)
    }

    @Test
    fun should_requestOnce_when_tappedTwiceWhileOpening() = runTest {
        val gate = CompletableDeferred<Unit>()
        val opener = FakeOpener(gate = gate)
        val store = store(opener)

        store.onEvent(DirectChatEvent.MessageClicked)
        store.onEvent(DirectChatEvent.MessageClicked)
        gate.complete(Unit)

        assertEquals(1, opener.requests.size)
    }
}
