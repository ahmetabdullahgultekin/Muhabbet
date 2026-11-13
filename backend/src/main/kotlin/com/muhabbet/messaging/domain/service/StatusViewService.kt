package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.RecordStatusViewUseCase
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.StatusViewRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Records status views (#787).
 *
 * Entitlement is not re-derived here. "May this viewer see this status" already has exactly one
 * answer in the app — [ManageStatusUseCase.getContactStatusesForUser], which applies the contact
 * scope (#507), blocks in both directions (#294, #687) and the author's audience list — and a second
 * copy of those rules would be a second place for them to drift. A view is accepted only for a
 * status that call would have served to this viewer.
 */
open class StatusViewService(
    private val statusRepository: StatusRepository,
    private val statusViewRepository: StatusViewRepository,
    private val manageStatusUseCase: ManageStatusUseCase,
    private val clock: Clock = Clock.systemUTC()
) : RecordStatusViewUseCase {

    @Transactional
    override fun recordView(statusId: UUID, viewerId: UUID) {
        val status = statusRepository.findById(statusId)
            ?: throw BusinessException(ErrorCode.STATUS_NOT_FOUND)
        if (status.userId == viewerId) return

        val visible = manageStatusUseCase.getContactStatusesForUser(viewerId)
            .any { group -> group.statuses.any { it.id == statusId } }
        if (!visible) throw BusinessException(ErrorCode.STATUS_NOT_FOUND)

        statusViewRepository.recordView(statusId, viewerId, Instant.now(clock))
    }
}
