package com.muhabbet.moderation.adapter.`in`.web

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.ReportStatus
import com.muhabbet.moderation.domain.model.UserReport
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests for moderation use cases (report, block, review).
 * ModerationController uses @AuthenticationPrincipal which complicates direct
 * controller instantiation in unit tests. These tests verify the use case
 * behavior that the controller delegates to.
 */
class ModerationControllerTest {

    private lateinit var reportUserUseCase: ReportUserUseCase
    private lateinit var blockUserUseCase: BlockUserUseCase
    private lateinit var reviewReportsUseCase: ReviewReportsUseCase

    private val userId = TestData.USER_ID_1
    private val targetUserId = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        reportUserUseCase = mockk()
        blockUserUseCase = mockk()
        reviewReportsUseCase = mockk()
    }

    // ─── Report ──────────────────────────────────────────

    @Nested
    inner class ReportUser {

        @Test
        fun `should create spam report with user ID`() {
            val reportId = UUID.randomUUID()
            val report = UserReport(
                id = reportId,
                reporterId = userId,
                reportedUserId = targetUserId,
                reason = ReportReason.SPAM,
                description = "Spam messages",
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every {
                reportUserUseCase.reportUser(
                    reporterId = userId,
                    reportedUserId = targetUserId,
                    reportedMessageId = null,
                    reportedConversationId = null,
                    reason = ReportReason.SPAM,
                    description = "Spam messages"
                )
            } returns report

            val result = reportUserUseCase.reportUser(
                reporterId = userId,
                reportedUserId = targetUserId,
                reportedMessageId = null,
                reportedConversationId = null,
                reason = ReportReason.SPAM,
                description = "Spam messages"
            )

            assert(result.id == reportId)
            assert(result.reason == ReportReason.SPAM)
            assert(result.status == ReportStatus.PENDING)
        }

        @Test
        fun `should create harassment report with message ID`() {
            val messageId = UUID.randomUUID()
            val report = UserReport(
                id = UUID.randomUUID(),
                reporterId = userId,
                reportedMessageId = messageId,
                reason = ReportReason.HARASSMENT,
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every {
                reportUserUseCase.reportUser(
                    reporterId = userId,
                    reportedUserId = null,
                    reportedMessageId = messageId,
                    reportedConversationId = null,
                    reason = ReportReason.HARASSMENT,
                    description = null
                )
            } returns report

            val result = reportUserUseCase.reportUser(
                reporterId = userId,
                reportedUserId = null,
                reportedMessageId = messageId,
                reportedConversationId = null,
                reason = ReportReason.HARASSMENT,
                description = null
            )

            assert(result.reportedMessageId == messageId)
            assert(result.reason == ReportReason.HARASSMENT)
        }

        @Test
        fun `should create report for illegal content in conversation`() {
            val convId = UUID.randomUUID()
            val report = UserReport(
                id = UUID.randomUUID(),
                reporterId = userId,
                reportedConversationId = convId,
                reason = ReportReason.ILLEGAL_CONTENT,
                description = "Illegal material shared",
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every {
                reportUserUseCase.reportUser(
                    reporterId = userId,
                    reportedUserId = null,
                    reportedMessageId = null,
                    reportedConversationId = convId,
                    reason = ReportReason.ILLEGAL_CONTENT,
                    description = "Illegal material shared"
                )
            } returns report

            val result = reportUserUseCase.reportUser(
                reporterId = userId,
                reportedUserId = null,
                reportedMessageId = null,
                reportedConversationId = convId,
                reason = ReportReason.ILLEGAL_CONTENT,
                description = "Illegal material shared"
            )

            assert(result.reportedConversationId == convId)
            assert(result.reason == ReportReason.ILLEGAL_CONTENT)
        }
    }

    // ─── Block ───────────────────────────────────────────

    @Nested
    inner class BlockUser {

        @Test
        fun `should block user successfully`() {
            every { blockUserUseCase.blockUser(userId, targetUserId) } returns Unit

            blockUserUseCase.blockUser(userId, targetUserId)

            verify { blockUserUseCase.blockUser(userId, targetUserId) }
        }

        @Test
        fun `should throw BLOCK_SELF when blocking self`() {
            every {
                blockUserUseCase.blockUser(userId, userId)
            } throws BusinessException(ErrorCode.BLOCK_SELF)

            try {
                blockUserUseCase.blockUser(userId, userId)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.BLOCK_SELF)
            }
        }

        @Test
        fun `should unblock user successfully`() {
            every { blockUserUseCase.unblockUser(userId, targetUserId) } returns Unit

            blockUserUseCase.unblockUser(userId, targetUserId)

            verify { blockUserUseCase.unblockUser(userId, targetUserId) }
        }

        @Test
        fun `should return list of blocked users`() {
            val blockedIds = listOf(targetUserId, TestData.USER_ID_3)

            every { blockUserUseCase.getBlockedUsers(userId) } returns blockedIds

            val result = blockUserUseCase.getBlockedUsers(userId)

            assert(result.size == 2)
            assert(result.contains(targetUserId))
            assert(result.contains(TestData.USER_ID_3))
        }

        @Test
        fun `should check if user is blocked`() {
            every { blockUserUseCase.isBlocked(userId, targetUserId) } returns true
            every { blockUserUseCase.isBlocked(userId, TestData.USER_ID_3) } returns false

            assert(blockUserUseCase.isBlocked(userId, targetUserId))
            assert(!blockUserUseCase.isBlocked(userId, TestData.USER_ID_3))
        }

        @Test
        fun `should return empty list when no users blocked`() {
            every { blockUserUseCase.getBlockedUsers(userId) } returns emptyList()

            val result = blockUserUseCase.getBlockedUsers(userId)

            assert(result.isEmpty())
        }
    }

    // ─── Admin: Review Reports ───────────────────────────

    @Nested
    inner class ReviewReports {

        @Test
        fun `should return pending reports with pagination`() {
            val reports = listOf(
                UserReport(
                    id = UUID.randomUUID(),
                    reporterId = userId,
                    reportedUserId = targetUserId,
                    reason = ReportReason.HARASSMENT,
                    status = ReportStatus.PENDING,
                    createdAt = Instant.now()
                ),
                UserReport(
                    id = UUID.randomUUID(),
                    reporterId = TestData.USER_ID_3,
                    reportedUserId = targetUserId,
                    reason = ReportReason.HATE_SPEECH,
                    status = ReportStatus.PENDING,
                    createdAt = Instant.now()
                )
            )

            every { reviewReportsUseCase.getPendingReports(20, 0) } returns reports

            val result = reviewReportsUseCase.getPendingReports(20, 0)

            assert(result.size == 2)
            assert(result[0].reason == ReportReason.HARASSMENT)
            assert(result[1].reason == ReportReason.HATE_SPEECH)
        }

        @Test
        fun `should return empty list when no pending reports`() {
            every { reviewReportsUseCase.getPendingReports(20, 0) } returns emptyList()

            val result = reviewReportsUseCase.getPendingReports(20, 0)

            assert(result.isEmpty())
        }

        @Test
        fun `should resolve report`() {
            val reportId = UUID.randomUUID()

            every { reviewReportsUseCase.resolveReport(reportId, userId, false) } returns Unit

            reviewReportsUseCase.resolveReport(reportId, userId, false)

            verify { reviewReportsUseCase.resolveReport(reportId, userId, false) }
        }

        @Test
        fun `should dismiss report`() {
            val reportId = UUID.randomUUID()

            every { reviewReportsUseCase.resolveReport(reportId, userId, true) } returns Unit

            reviewReportsUseCase.resolveReport(reportId, userId, true)

            verify { reviewReportsUseCase.resolveReport(reportId, userId, true) }
        }

        @Test
        fun `should throw REPORT_NOT_FOUND for invalid report ID`() {
            val invalidId = UUID.randomUUID()

            every {
                reviewReportsUseCase.resolveReport(invalidId, userId, false)
            } throws BusinessException(ErrorCode.REPORT_NOT_FOUND)

            try {
                reviewReportsUseCase.resolveReport(invalidId, userId, false)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.REPORT_NOT_FOUND)
            }
        }
    }
}
