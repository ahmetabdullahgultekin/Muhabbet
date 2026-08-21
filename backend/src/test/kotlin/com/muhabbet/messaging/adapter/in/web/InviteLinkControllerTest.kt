package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.port.`in`.ManageInviteLinkUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.config.InviteLinkProperties
import com.muhabbet.shared.config.JsonConfig
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The invite sheet could not read a single response this controller produced (#695). The file
 * declared its own `InviteLinkResponse`, and the copy had neither `inviteUrl` nor `isActive` —
 * both of which the client's DTO requires. `ApiClient` sets `ignoreUnknownKeys`, which forgives a
 * field the client does not know and does nothing at all for one it requires and the server never
 * sends, so kotlinx-serialization threw `MissingFieldException` on the phone while every backend
 * test that asserted only "200 OK" stayed green.
 *
 * So these tests do not compare field names by eye. They encode the controller's own response with
 * the server's `Json` bean — the one Spring hands to `KotlinSerializationJsonHttpMessageConverter`
 * for a `@Serializable` body — and decode it with the client's DTO under `ApiClient`'s settings. A
 * dropped or renamed required field fails the decode here exactly as it did on the device.
 *
 * That the response type can be written once in this file is the fix: `InviteLinkResponse` below
 * is the shared KMP DTO, so the next field added to `shared/.../dto/Dtos.kt` breaks this compile
 * rather than the app.
 */
class InviteLinkControllerTest {

    private lateinit var manageInviteLinkUseCase: ManageInviteLinkUseCase
    private lateinit var controller: InviteLinkController

    /** The server's own encoder, from the bean that configures Spring's converter. */
    private val serverJson = JsonConfig().json()

    /** Field-for-field `ApiClient.json`. Decoding under anything laxer would not prove much. */
    private val clientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    private val properties = InviteLinkProperties(baseUrl = "https://muhabbet.app/invite")

    private val userId = TestData.USER_ID_1
    private val conversationId = TestData.CONVERSATION_ID
    private val linkId = UUID.fromString("00000000-0000-0000-0000-0000000004a1")
    private val token = "7wDcQmVUmvvcJmBLtSPu3xX2Bd0Cq1Yb1kQ9dfNfR8k"
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        manageInviteLinkUseCase = mockk()
        controller = InviteLinkController(manageInviteLinkUseCase, properties)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class CreateLink {

        @Test
        fun `should serve a body the client can decode`() {
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            val decoded = decode(created())

            assertEquals(linkId.toString(), decoded.id)
            assertEquals(conversationId.toString(), decoded.conversationId)
            assertEquals(token, decoded.inviteToken)
            assertFalse(decoded.requiresApproval)
            assertNull(decoded.maxUses)
            assertEquals(0, decoded.useCount)
            assertNull(decoded.expiresAt)
            assertEquals(createdAt.toString(), decoded.createdAt)
        }

        @Test
        fun `should send the shareable url the sheet copies to the clipboard`() {
            // The token alone is not shareable and the client has no host to build a URL from, so
            // an absent inviteUrl is not a cosmetic gap — it is the whole feature.
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            assertEquals("https://muhabbet.app/invite/$token", decode(created()).inviteUrl)
        }

        @Test
        fun `should not double the separator when the configured base ends in a slash`() {
            val trailing = InviteLinkController(
                manageInviteLinkUseCase,
                InviteLinkProperties(baseUrl = "https://muhabbet.app/invite/")
            )
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            val body = trailing.createLink(conversationId, CreateInviteLinkRequest()).body?.data

            assertEquals("https://muhabbet.app/invite/$token", decode(body).inviteUrl)
        }

        @Test
        fun `should report the link's own active flag rather than a constant`() {
            // isActive has to come from the domain model. Hardcoding true would satisfy the decode
            // and still lie about a revoked link.
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns
                link(isActive = false)

            assertFalse(decode(created()).isActive)
        }

        @Test
        fun `should carry both keys the old private DTO dropped`() {
            // Belt and braces on top of the decode: if either is renamed the decode fails with
            // MissingFieldException, and this says which key and why it matters.
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            val raw = serverJson.encodeToString(requireNotNull(created()))

            assertTrue(raw.contains("\"inviteUrl\""), "inviteUrl is required by the client DTO: $raw")
            assertTrue(raw.contains("\"isActive\""), "isActive is required by the client DTO: $raw")
        }

        @Test
        fun `should turn the client's expiresInHours into an expiry instant`() {
            // The old private request DTO read `expiresAt`, and the app has only ever sent
            // `expiresInHours` — so the field was dropped and the link never expired.
            val expiresAt = slot<Instant?>()
            every {
                manageInviteLinkUseCase.createLink(any(), any(), any(), any(), captureNullable(expiresAt))
            } returns link()

            val before = Instant.now()
            controller.createLink(conversationId, CreateInviteLinkRequest(expiresInHours = 48))
            val after = Instant.now()

            val captured = requireNotNull(expiresAt.captured) { "expiresInHours was dropped entirely" }
            assertTrue(
                !captured.isBefore(before.plus(48, ChronoUnit.HOURS)) &&
                    !captured.isAfter(after.plus(48, ChronoUnit.HOURS)),
                "expected an expiry 48h out, got $captured"
            )
        }

        @Test
        fun `should leave the expiry unset when the client asks for none`() {
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            controller.createLink(conversationId, CreateInviteLinkRequest(requiresApproval = true, maxUses = 5))

            verify { manageInviteLinkUseCase.createLink(conversationId, userId, true, 5, null) }
        }

        @Test
        fun `should pass the authenticated caller to the use case, not a caller-supplied id`() {
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            controller.createLink(conversationId, CreateInviteLinkRequest())

            verify { manageInviteLinkUseCase.createLink(conversationId, userId, false, null, null) }
        }

        @Test
        fun `should answer 201`() {
            every { manageInviteLinkUseCase.createLink(any(), any(), any(), any(), any()) } returns link()

            assertEquals(201, controller.createLink(conversationId, CreateInviteLinkRequest()).statusCode.value())
        }

        private fun created(): InviteLinkResponse? =
            controller.createLink(conversationId, CreateInviteLinkRequest()).body?.data
    }

    @Nested
    inner class GetLinkInfo {

        @Test
        fun `should serve a body the client can decode`() {
            val expiresAt = createdAt.plus(7, ChronoUnit.DAYS)
            every { manageInviteLinkUseCase.getLinkInfo(token) } returns
                link(requiresApproval = true, maxUses = 25, useCount = 3, expiresAt = expiresAt)

            val decoded = decode(controller.getLinkInfo(token).body?.data)

            assertEquals(token, decoded.inviteToken)
            assertEquals("https://muhabbet.app/invite/$token", decoded.inviteUrl)
            assertTrue(decoded.isActive)
            assertTrue(decoded.requiresApproval)
            assertEquals(25, decoded.maxUses)
            assertEquals(3, decoded.useCount)
            assertEquals(expiresAt.toString(), decoded.expiresAt)
        }

        @Test
        fun `should look the link up by the token in the path`() {
            every { manageInviteLinkUseCase.getLinkInfo(token) } returns link()

            assertNotNull(controller.getLinkInfo(token).body?.data)

            verify { manageInviteLinkUseCase.getLinkInfo(token) }
        }
    }

    @Nested
    inner class RevokeLink {

        @Test
        fun `should pass the authenticated caller to the use case`() {
            every { manageInviteLinkUseCase.revokeLink(linkId, userId) } returns Unit

            val response = controller.revokeLink(conversationId, linkId)

            assertEquals(200, response.statusCode.value())
            verify { manageInviteLinkUseCase.revokeLink(linkId, userId) }
        }
    }

    @Nested
    inner class JoinViaLink {

        @Test
        fun `should pass the authenticated caller to the use case`() {
            every { manageInviteLinkUseCase.joinViaLink(token, userId) } returns Unit

            val response = controller.joinViaLink(token)

            assertEquals(200, response.statusCode.value())
            verify { manageInviteLinkUseCase.joinViaLink(token, userId) }
        }
    }

    @Test
    fun `the body the old private DTO produced still fails to decode`() {
        // The reproduction, kept as a test so the mechanism is not re-argued: this is byte-for-byte
        // what the controller used to send. ignoreUnknownKeys forgives a field the client does not
        // know; nothing forgives one it requires. Anyone reintroducing a hand-rolled response DTO
        // here can read what that costs.
        val oldBody = """
            {"id":"$linkId","conversationId":"$conversationId","inviteToken":"$token",
             "requiresApproval":false,"maxUses":null,"useCount":0,"expiresAt":null,
             "createdAt":"$createdAt"}
        """.trimIndent()

        val failure = runCatching { clientJson.decodeFromString<InviteLinkResponse>(oldBody) }.exceptionOrNull()

        assertNotNull(failure, "the old body decoded, so this test no longer proves anything")
        assertTrue(
            failure is MissingFieldException,
            "expected MissingFieldException, got ${failure!!::class.simpleName}: ${failure.message}"
        )
        assertTrue(
            failure.message.orEmpty().contains("inviteUrl") &&
                failure.message.orEmpty().contains("isActive"),
            "expected both missing fields to be named: ${failure.message}"
        )
    }

    private fun link(
        isActive: Boolean = true,
        requiresApproval: Boolean = false,
        maxUses: Int? = null,
        useCount: Int = 0,
        expiresAt: Instant? = null
    ) = GroupInviteLink(
        id = linkId,
        conversationId = conversationId,
        inviteToken = token,
        createdBy = userId,
        requiresApproval = requiresApproval,
        isActive = isActive,
        maxUses = maxUses,
        useCount = useCount,
        expiresAt = expiresAt,
        createdAt = createdAt
    )

    /**
     * Server encoder out, client decoder in — the whole point of the test. `requireNotNull` first
     * so a missing body reads as a missing body rather than as a decode failure.
     */
    private fun decode(body: InviteLinkResponse?): InviteLinkResponse =
        clientJson.decodeFromString(serverJson.encodeToString(requireNotNull(body) { "no response body" }))

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
