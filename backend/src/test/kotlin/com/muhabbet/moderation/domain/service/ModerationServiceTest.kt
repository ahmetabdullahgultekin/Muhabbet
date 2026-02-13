package com.muhabbet.moderation.domain.service

import com.muhabbet.moderation.domain.model.*
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportRepository
import com.muhabbet.shared.exception.BusinessException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class ModerationServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val blockRepository = mockk<BlockRepository>()
    private lateinit var service: ModerationService

    @BeforeEach
    fun setUp() {
        service = ModerationService(reportRepository, blockRepository)
    }

    @Test
    fun `should create report successfully`() {
        val reporterId = UUID.randomUUID()
        val reportedUserId = UUID.randomUUID()

        every { reportRepository.save(any()) } answers {
            firstArg<UserReport>().copy(id = UUID.randomUUID())
        }

        val report = service.reportUser(
            reporterId = reporterId,
            reportedUserId = reportedUserId,
            reportedMessageId = null,
            reportedConversationId = null,
            reason = ReportReason.SPAM,
            description = "Spam messages"
        )

        assertNotNull(report)
        assertEquals(ReportReason.SPAM, report.reason)
        verify { reportRepository.save(any()) }
    }

    @Test
    fun `should block user`() {
        val blockerId = UUID.randomUUID()
        val blockedId = UUID.randomUUID()

        every { blockRepository.exists(blockerId, blockedId) } returns false
        every { blockRepository.save(any()) } answers { firstArg() }

        assertDoesNotThrow { service.blockUser(blockerId, blockedId) }
        verify { blockRepository.save(any()) }
    }

    @Test
    fun `should throw when blocking self`() {
        val userId = UUID.randomUUID()
        assertThrows(BusinessException::class.java) {
            service.blockUser(userId, userId)
        }
    }

    @Test
    fun `should not duplicate block`() {
        val blockerId = UUID.randomUUID()
        val blockedId = UUID.randomUUID()

        every { blockRepository.exists(blockerId, blockedId) } returns true

        service.blockUser(blockerId, blockedId)
        verify(exactly = 0) { blockRepository.save(any()) }
    }

    @Test
    fun `should unblock user`() {
        val blockerId = UUID.randomUUID()
        val blockedId = UUID.randomUUID()

        every { blockRepository.delete(blockerId, blockedId) } just Runs

        assertDoesNotThrow { service.unblockUser(blockerId, blockedId) }
    }

    @Test
    fun `should get blocked users`() {
        val userId = UUID.randomUUID()
        val blockedId1 = UUID.randomUUID()
        val blockedId2 = UUID.randomUUID()

        every { blockRepository.findByBlockerId(userId) } returns listOf(
            UserBlock(blockerId = userId, blockedId = blockedId1),
            UserBlock(blockerId = userId, blockedId = blockedId2)
        )

        val blocked = service.getBlockedUsers(userId)
        assertEquals(2, blocked.size)
        assertTrue(blocked.contains(blockedId1))
        assertTrue(blocked.contains(blockedId2))
    }

    @Test
    fun `should resolve report`() {
        val reportId = UUID.randomUUID()
        val reviewerId = UUID.randomUUID()
        val report = UserReport(
            id = reportId,
            reporterId = UUID.randomUUID(),
            reason = ReportReason.HARASSMENT,
            status = ReportStatus.PENDING
        )

        every { reportRepository.findById(reportId) } returns report
        every { reportRepository.updateStatus(reportId, ReportStatus.RESOLVED, reviewerId) } just Runs

        assertDoesNotThrow { service.resolveReport(reportId, reviewerId, dismiss = false) }
        verify { reportRepository.updateStatus(reportId, ReportStatus.RESOLVED, reviewerId) }
    }

    @Test
    fun `should dismiss report`() {
        val reportId = UUID.randomUUID()
        val reviewerId = UUID.randomUUID()
        val report = UserReport(
            id = reportId,
            reporterId = UUID.randomUUID(),
            reason = ReportReason.OTHER,
            status = ReportStatus.PENDING
        )

        every { reportRepository.findById(reportId) } returns report
        every { reportRepository.updateStatus(reportId, ReportStatus.DISMISSED, reviewerId) } just Runs

        service.resolveReport(reportId, reviewerId, dismiss = true)
        verify { reportRepository.updateStatus(reportId, ReportStatus.DISMISSED, reviewerId) }
    }
}
