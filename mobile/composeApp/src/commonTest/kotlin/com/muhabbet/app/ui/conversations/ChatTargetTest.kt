package com.muhabbet.app.ui.conversations

import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a conversation is called, and whose face it wears (#543).
 *
 * The bug was not a crash either: opening a chat from Starred Messages landed on the right
 * conversation with an empty title, a "?" avatar and a dead tap where the person should be, because
 * the caller passed `""` for the name and let `otherUserId`, `isGroup` and `avatarUrl` default.
 * `ChatScreen` renders what it is given and has no fallback of its own, so an empty string reaches
 * the user as an empty title bar.
 *
 * Four screens each had their own copy of this resolution and the copies did not agree — two ended
 * `?: ""`, one had no phone-number fallback, and one skipped it entirely. They now share one rule,
 * and this is that rule. **The invariant worth guarding: [ChatTarget.name] is never blank.**
 */
class ChatTargetTest {

    private companion object {
        const val ME = "user-me"
        const val FALLBACK = "Sohbet"
    }

    private fun participant(
        id: String,
        displayName: String? = null,
        phoneNumber: String? = null,
        avatarUrl: String? = null,
    ) = ParticipantResponse(
        userId = id,
        displayName = displayName,
        phoneNumber = phoneNumber,
        avatarUrl = avatarUrl,
        role = MemberRole.MEMBER,
        isOnline = false,
    )

    private fun conversation(
        type: ConversationType = ConversationType.DIRECT,
        name: String? = null,
        avatarUrl: String? = null,
        participants: List<ParticipantResponse>,
    ) = ConversationResponse(
        id = "c1",
        type = type,
        name = name,
        avatarUrl = avatarUrl,
        participants = participants,
        unreadCount = 0,
        createdAt = "2026-08-17T10:00:00Z",
    )

    // ─── the invariant ───────────────────────────────────────

    @Test
    fun toChatTarget_neverProducesABlankName() {
        // The whole of #543 in one assertion. A participant with no display name and no number is
        // the case every previous copy of this code turned into "".
        val target = conversation(
            participants = listOf(participant(ME, "Ben"), participant("u2")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals(FALLBACK, target.name)
        assertTrue(target.name.isNotBlank())
    }

    @Test
    fun toChatTarget_treatsABlankNameAsNoNameAtAll() {
        // A `?:` chain carries "" all the way to the title bar, which looks exactly like the bug
        // this change removes. The server validates displayName with isNotBlank(), but "the server
        // would never send that" is precisely the assumption being retired here.
        val target = conversation(
            name = "   ",
            participants = listOf(participant(ME, "Ben"), participant("u2", displayName = "")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals(FALLBACK, target.name)
    }

    @Test
    fun toChatTarget_carriesTheIdentityTheChatScreenNeedsToDrawItself() {
        // Name, avatar and the user id behind the title tap all travel together, because the chat
        // screen has no way to fetch any of them from a conversation id alone.
        val target = conversation(
            participants = listOf(
                participant(ME, "Ben"),
                participant("u2", displayName = "Ayşe", avatarUrl = "https://cdn/ayse.jpg"),
            ),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals("Ayşe", target.name)
        assertEquals("u2", target.otherUserId)
        assertEquals("https://cdn/ayse.jpg", target.avatarUrl)
        assertEquals(false, target.isGroup)
    }

    // ─── which of several candidate names wins ───────────────

    @Test
    fun toChatTarget_prefersTheNameTheUserWroteInTheirOwnAddressBook() {
        val target = conversation(
            participants = listOf(
                participant(ME, "Ben"),
                participant("u2", displayName = "ayse_1993", phoneNumber = "+905000000002"),
            ),
        ).toChatTarget(
            currentUserId = ME,
            fallbackName = FALLBACK,
            contactNames = mapOf("+905000000002" to "Ayşe Teyze"),
        )

        assertEquals("Ayşe Teyze", target.name)
    }

    @Test
    fun toChatTarget_fallsBackToThePhoneNumberBeforeGivingUp() {
        // The home shell's search results skipped this rung entirely — `conv.name ?: displayName
        // ?: ""` — so searching for someone who never set a display name opened a nameless chat.
        val target = conversation(
            participants = listOf(participant(ME, "Ben"), participant("u2", phoneNumber = "+905000000002")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals("+905000000002", target.name)
    }

    @Test
    fun toChatTarget_whenTheUserIdIsUnknown_stillNamesTheConversation() {
        // TokenStorage can answer null. Naming the conversation after whoever sorts first is wrong
        // half the time; a blank title bar is wrong every time.
        val target = conversation(
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe")),
        ).toChatTarget(currentUserId = null, fallbackName = FALLBACK)

        assertTrue(target.name.isNotBlank())
    }

    // ─── groups ──────────────────────────────────────────────

    @Test
    fun toChatTarget_forAGroup_usesTheGroupsOwnNameAndPicture() {
        val target = conversation(
            type = ConversationType.GROUP,
            name = "Aile",
            avatarUrl = "https://cdn/aile.jpg",
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe", avatarUrl = "https://cdn/ayse.jpg")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals("Aile", target.name)
        assertEquals("https://cdn/aile.jpg", target.avatarUrl)
        assertTrue(target.isGroup)
    }

    @Test
    fun toChatTarget_forAGroup_offersNoUserToOpenAProfileFrom() {
        // `otherUserId` drives the title tap. In a group there is no single "other", and handing
        // over whichever member sorted first would open a stranger's profile from the group header.
        val target = conversation(
            type = ConversationType.GROUP,
            name = "Aile",
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe"), participant("u3", "Bora")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertNull(target.otherUserId)
    }

    @Test
    fun toChatTarget_forAnUnnamedGroup_doesNotBorrowAMembersName() {
        val target = conversation(
            type = ConversationType.GROUP,
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe"), participant("u3", "Bora")),
        ).toChatTarget(currentUserId = ME, fallbackName = FALLBACK)

        assertEquals(FALLBACK, target.name)
    }

    // ─── who said this ───────────────────────────────────────

    @Test
    fun senderLabel_namesTheMemberOfAGroupWhoSentIt() {
        // Starred Messages printed the constant "Bilinmeyen kişi" for every message that was not
        // your own, including messages from people you talk to daily. It never looked.
        val label = conversation(
            type = ConversationType.GROUP,
            name = "Aile",
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe"), participant("u3", "Bora")),
        ).senderLabel(senderId = "u3")

        assertEquals("Bora", label)
    }

    @Test
    fun senderLabel_prefersTheAddressBookHereToo() {
        val label = conversation(
            participants = listOf(participant(ME, "Ben"), participant("u2", "ayse_1993", phoneNumber = "+905000000002")),
        ).senderLabel(senderId = "u2", contactNames = mapOf("+905000000002" to "Ayşe Teyze"))

        assertEquals("Ayşe Teyze", label)
    }

    @Test
    fun senderLabel_whenTheSenderHasLeft_returnsNullRatherThanAConstant() {
        // Null so the caller picks its own words. A resolver that returned "Unknown contact" itself
        // is how the starred list ended up labelling everyone that way.
        val label = conversation(
            type = ConversationType.GROUP,
            name = "Aile",
            participants = listOf(participant(ME, "Ben"), participant("u2", "Ayşe")),
        ).senderLabel(senderId = "u-departed")

        assertNull(label)
    }

    @Test
    fun senderLabel_neverFallsBackToTheUserId() {
        // #507: the first eight characters of a user id read as a hex hash, and shipped as a name.
        val label = conversation(
            participants = listOf(participant(ME, "Ben"), participant("u2")),
        ).senderLabel(senderId = "u2")

        assertNull(label)
    }
}
