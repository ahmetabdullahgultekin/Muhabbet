package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.port.`in`.GetLinkPreviewUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class LinkPreviewControllerTest {

    private val useCase = mockk<GetLinkPreviewUseCase>()
    private val controller = LinkPreviewController(useCase)

    @BeforeEach
    fun setUp() {
        val claims = JwtClaims(userId = TestData.USER_ID_1, deviceId = TestData.DEVICE_ID_1)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `should pass the caller and url to the use case and map every field`() {
        val url = "https://example.com"
        every { useCase.getLinkPreview(TestData.USER_ID_1, url) } returns
            LinkPreview(url, "T", "D", "https://example.com/i.png", "S")

        val body = controller.getLinkPreview(url).body?.data

        assertEquals(url, body?.url)
        assertEquals("T", body?.title)
        assertEquals("D", body?.description)
        assertEquals("https://example.com/i.png", body?.imageUrl)
        assertEquals("S", body?.siteName)
        verify(exactly = 1) { useCase.getLinkPreview(TestData.USER_ID_1, url) }
    }

    @Test
    fun `should require authentication before calling the use case`() {
        SecurityContextHolder.clearContext()

        assertThrows(Exception::class.java) { controller.getLinkPreview("https://example.com") }
        verify(exactly = 0) { useCase.getLinkPreview(any(), any()) }
    }
}
