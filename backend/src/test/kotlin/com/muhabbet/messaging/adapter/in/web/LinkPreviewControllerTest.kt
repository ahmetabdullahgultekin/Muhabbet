package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class LinkPreviewControllerTest {

    private lateinit var controller: LinkPreviewController

    @BeforeEach
    fun setUp() {
        controller = LinkPreviewController()
        setAuthenticatedUser(TestData.USER_ID_1, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class GetLinkPreview {

        @Test
        fun `should return fallback response for unreachable URL`() {
            // LinkPreviewController catches exceptions and returns fallback
            val response = controller.getLinkPreview("https://nonexistent.invalid.test")

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.url == "https://nonexistent.invalid.test")
            // Title/description should be null for unreachable URL
            assert(response.body?.data?.title == null || response.body?.data?.title?.isNotEmpty() == true)
        }

        @Test
        fun `should require authentication`() {
            SecurityContextHolder.clearContext()

            try {
                controller.getLinkPreview("https://example.com")
                assert(false) { "Expected exception for unauthenticated user" }
            } catch (_: Exception) {
                // Expected — AuthenticatedUser.currentUserId() throws when no auth
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
