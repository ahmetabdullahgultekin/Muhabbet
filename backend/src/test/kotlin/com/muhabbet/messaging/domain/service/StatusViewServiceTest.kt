package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.StatusGroup
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.StatusViewRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * #787: a view is recorded only by someone the status was actually served to. Entitlement is the
 * same call the Updates tab is answered from, so a stranger, a blocked contact or someone outside an
 * "only share with" list is refused exactly as the tray would refuse them.
 */
class StatusViewServiceTest {

    private val statuses = mockk<StatusRepository>()
    private val views = mockk<StatusViewRepository>()
    private val manageStatus = mockk<ManageStatusUseCase>()
    private val now = Instant.parse("2026-11-13T10:00:00Z")
    private lateinit var service: StatusViewService

    private val author = TestData.USER_ID_1
    private val viewer = TestData.USER_ID_2
    private val status = Status(userId = author, content = "Merhaba")

    @BeforeEach
    fun setUp() {
        service = StatusViewService(statuses, views, manageStatus, Clock.fixed(now, ZoneOffset.UTC))
        every { statuses.findById(status.id) } returns status
        justRun { views.recordView(any(), any(), any()) }
    }

    @Test
    fun `should record the view when the status is in the viewer's tray`() {
        every { manageStatus.getContactStatusesForUser(viewer) } returns listOf(StatusGroup(author, listOf(status)))

        service.recordView(status.id, viewer)

        verify { views.recordView(status.id, viewer, now) }
    }

    @Test
    fun `should refuse as not found when the status is not served to the viewer`() {
        // Not a contact, blocked, or excluded by the audience list: all collapse to "not in my tray".
        every { manageStatus.getContactStatusesForUser(viewer) } returns emptyList()

        val e = assertThrows<BusinessException> { service.recordView(status.id, viewer) }

        assertEquals(ErrorCode.STATUS_NOT_FOUND, e.errorCode)
        verify(exactly = 0) { views.recordView(any(), any(), any()) }
    }

    @Test
    fun `should refuse as not found when the status does not exist`() {
        val missing = java.util.UUID.randomUUID()
        every { statuses.findById(missing) } returns null

        val e = assertThrows<BusinessException> { service.recordView(missing, viewer) }

        assertEquals(ErrorCode.STATUS_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `should accept but not record the author viewing their own status`() {
        service.recordView(status.id, author)

        verify(exactly = 0) { views.recordView(any(), any(), any()) }
        verify(exactly = 0) { manageStatus.getContactStatusesForUser(any()) }
    }
}
