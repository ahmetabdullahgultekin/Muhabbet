package com.muhabbet.moderation.domain.service

import com.muhabbet.moderation.domain.model.*
import com.muhabbet.moderation.domain.port.out.BlockRepository
import com.muhabbet.moderation.domain.port.out.ReportEvidencePort
import com.muhabbet.moderation.domain.port.out.ReportRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ModerationServiceTest {

    private val reportRepository = mockk<ReportRepository>()
    private val blockRepository = mockk<BlockRepository>()
    private val evidencePort = mockk<ReportEvidencePort>()
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private lateinit var service: ModerationService

    private val reporterId = UUID.randomUUID()
    private val reportedUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = ModerationService(reportRepository, blockRepository, evidencePort, Clock.fixed(now, ZoneOffset.UTC))
        every { reportRepository.findPendingByReporterAndTarget(any(), any()) } returns null
        every { reportRepository.save(any()) } answers { firstArg() }
    }

    private fun report(reporterId: UUID = this.reporterId, userId: UUID? = reportedUserId, messageId: UUID? = null) =
        service.reportUser(reporterId, userId, messageId, null, ReportReason.SPAM, "spam")

    @Nested
    inner class Filing {

        @Test
        fun `should create a report on a user`() {
            val report = report()

            assertEquals(ReportTarget.User(reportedUserId), report.target)
            assertEquals(ReportStatus.PENDING, report.status)
            assertEquals(now, report.createdAt)
            verify { reportRepository.save(any()) }
        }

        @Test
        fun `should refuse a report with no target`() {
            val ex = assertThrows(BusinessException::class.java) { report(userId = null) }

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
            verify(exactly = 0) { reportRepository.save(any()) }
        }

        @Test
        fun `should refuse a report naming two targets`() {
            val ex = assertThrows(BusinessException::class.java) { report(messageId = UUID.randomUUID()) }

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should refuse reporting yourself`() {
            val ex = assertThrows(BusinessException::class.java) { report(userId = reporterId) }

            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should refuse a message the reporter cannot see`() {
            val messageId = UUID.randomUUID()
            every { evidencePort.snapshotVisibleMessage(reporterId, messageId) } returns null

            val ex = assertThrows(BusinessException::class.java) { report(userId = null, messageId = messageId) }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
            verify(exactly = 0) { reportRepository.save(any()) }
        }

        @Test
        fun `should keep a snapshot of the reported message and name its sender`() {
            val messageId = UUID.randomUUID()
            val snapshot = MessageEvidence(
                messageId = messageId, conversationId = UUID.randomUUID(), senderId = reportedUserId,
                contentType = "TEXT", content = "abuse", mediaUrl = null, sentAt = now.minusSeconds(60)
            )
            every { evidencePort.snapshotVisibleMessage(reporterId, messageId) } returns snapshot

            val report = report(userId = null, messageId = messageId)

            assertEquals(snapshot, report.evidence)
            assertEquals(ReportTarget.Message(messageId), report.target)
            assertEquals(reportedUserId, report.reportedUserId)
            assertEquals(snapshot.conversationId, report.reportedConversationId)
        }

        @Test
        fun `should refuse a conversation the reporter is not in`() {
            val conversationId = UUID.randomUUID()
            every { evidencePort.isConversationMember(reporterId, conversationId) } returns false

            val ex = assertThrows(BusinessException::class.java) {
                service.reportUser(reporterId, null, null, conversationId, ReportReason.SPAM, null)
            }

            assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        }

        @Test
        fun `should return the pending report instead of filing a duplicate`() {
            val existing = UserReport(reporterId = reporterId, reportedUserId = reportedUserId, reason = ReportReason.SPAM)
            every { reportRepository.findPendingByReporterAndTarget(reporterId, ReportTarget.User(reportedUserId)) } returns existing

            assertEquals(existing, report())
            verify(exactly = 0) { reportRepository.save(any()) }
        }
    }

    @Nested
    inner class Lifecycle {

        private val reviewerId = UUID.randomUUID()

        private fun stored(status: ReportStatus) =
            UserReport(reporterId = reporterId, reportedUserId = reportedUserId, reason = ReportReason.HARASSMENT, status = status)

        @Test
        fun `should move PENDING to RESOLVED and DISMISSED`() {
            val resolved = stored(ReportStatus.PENDING).resolve(reviewerId, now) as ReportReview.Closed
            val dismissed = stored(ReportStatus.PENDING).dismiss(reviewerId, now) as ReportReview.Closed

            assertEquals(ReportStatus.RESOLVED, resolved.report.status)
            assertEquals(ReportStatus.DISMISSED, dismissed.report.status)
            assertEquals(reviewerId, resolved.report.reviewedBy)
            assertEquals(now, resolved.report.resolvedAt)
        }

        @ParameterizedTest
        @EnumSource(value = ReportStatus::class, names = ["RESOLVED", "DISMISSED"])
        fun `should refuse to decide a report twice`(status: ReportStatus) {
            assertEquals(ReportReview.AlreadyClosed(status), stored(status).resolve(reviewerId, now))
            assertEquals(ReportReview.AlreadyClosed(status), stored(status).dismiss(reviewerId, now))
        }

        @Test
        fun `should resolve a pending report conditionally`() {
            val report = stored(ReportStatus.PENDING)
            every { reportRepository.findById(report.id) } returns report
            every { reportRepository.close(any()) } returns true

            service.resolveReport(report.id, reviewerId, dismiss = false)

            verify { reportRepository.close(match { it.status == ReportStatus.RESOLVED && it.reviewedBy == reviewerId }) }
        }

        @Test
        fun `should refuse flipping a resolved report to dismissed`() {
            val report = stored(ReportStatus.RESOLVED)
            every { reportRepository.findById(report.id) } returns report

            val ex = assertThrows(BusinessException::class.java) { service.resolveReport(report.id, reviewerId, dismiss = true) }

            assertEquals(ErrorCode.REPORT_ALREADY_RESOLVED, ex.errorCode)
            verify(exactly = 0) { reportRepository.close(any()) }
        }

        @Test
        fun `should refuse the loser of two concurrent decisions`() {
            val report = stored(ReportStatus.PENDING)
            every { reportRepository.findById(report.id) } returns report
            every { reportRepository.close(any()) } returns false

            val ex = assertThrows(BusinessException::class.java) { service.resolveReport(report.id, reviewerId, dismiss = true) }

            assertEquals(ErrorCode.REPORT_ALREADY_RESOLVED, ex.errorCode)
        }
    }

    @Nested
    inner class Blocking {

        @Test
        fun `should block user`() {
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()
            every { blockRepository.insertIfAbsent(any()) } returns true

            assertDoesNotThrow { service.blockUser(blockerId, blockedId) }
            verify { blockRepository.insertIfAbsent(match { it.blockerId == blockerId && it.blockedId == blockedId }) }
        }

        @Test
        fun `should succeed quietly when the block already exists`() {
            every { blockRepository.insertIfAbsent(any()) } returns false

            assertDoesNotThrow { service.blockUser(UUID.randomUUID(), UUID.randomUUID()) }
        }

        @Test
        fun `should throw when blocking self`() {
            val userId = UUID.randomUUID()
            assertThrows(BusinessException::class.java) { service.blockUser(userId, userId) }
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
            every { blockRepository.findByBlockerId(userId) } returns listOf(
                UserBlock(blockerId = userId, blockedId = UUID.randomUUID()),
                UserBlock(blockerId = userId, blockedId = UUID.randomUUID())
            )

            assertEquals(2, service.getBlockedUsers(userId).size)
        }
    }
}
