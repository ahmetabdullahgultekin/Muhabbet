package com.muhabbet.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** #815: a link opens a conversation only when it names one, unambiguously. */
class DeepLinkParserTest {

    private val id = "3f2b8c1e-9a4d-4e2f-8b6a-1c2d3e4f5a6b"

    private fun parse(scheme: String?, host: String?, vararg path: String) =
        DeepLinkParser.parse(scheme, host, path.toList())

    @Test
    fun should_openTheChat_when_customSchemeCarriesAUuid() {
        assertEquals(DeepLink.Chat(id), parse("muhabbet", "chat", id))
    }

    @Test
    fun should_openTheChat_when_appLinkCarriesAUuid() {
        assertEquals(DeepLink.Chat(id), parse("https", "muhabbet.app", "chat", id))
    }

    @Test
    fun should_beUnknown_when_chatLinkHasNoId() {
        assertEquals(DeepLink.Unknown, parse("https", "muhabbet.app", "chat"))
        assertEquals(DeepLink.Unknown, parse("https", "muhabbet.app", "chat", ""))
        assertEquals(DeepLink.Unknown, parse("muhabbet", "chat"))
    }

    @Test
    fun should_beUnknown_when_idIsNotAUuid() {
        assertEquals(DeepLink.Unknown, parse("https", "muhabbet.app", "chat", "not-a-uuid"))
        assertEquals(DeepLink.Unknown, parse("muhabbet", "chat", "chat"))
    }

    @Test
    fun should_beUnknown_when_pathHasExtraSegments() {
        // `lastOrNull()` used to pick `b` here.
        assertEquals(DeepLink.Unknown, parse("muhabbet", "chat", id, "b"))
        assertEquals(DeepLink.Unknown, parse("https", "muhabbet.app", "chat", "a", id))
    }

    @Test
    fun should_beUnknown_when_httpsHostIsNotOurs() {
        assertEquals(DeepLink.Unknown, parse("https", "evil.example", "chat", id))
        assertEquals(DeepLink.Unknown, parse("http", "muhabbet.app", "chat", id))
    }

    @Test
    fun should_recogniseAnInvite_when_inviteLinkHasACode() {
        assertEquals(DeepLink.Invite("AbC-123_x"), parse("https", "muhabbet.app", "invite", "AbC-123_x"))
        assertEquals(DeepLink.Unknown, parse("https", "muhabbet.app", "invite"))
    }
}
