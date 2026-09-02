package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.StatusGroup
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class StatusService(
    private val statusRepository: StatusRepository,
    private val conversationRepository: ConversationRepository,
    private val userDirectory: UserDirectoryPort,
    private val blockPolicy: BlockPolicyPort,
    private val mediaAttachmentPolicy: MediaAttachmentPolicyPort
) : ManageStatusUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A status URL reaches every contact's screen the moment they open the status tray, which is the
     * same unasked-for fetch a message's `mediaUrl` causes and the same fix (#679) — on a wider
     * audience, since a status goes to everyone you share a conversation with rather than to one
     * chat.
     *
     * Only the origin test applies here: `POST /statuses` takes a URL and no media id, so there is
     * nothing for the server to resolve. Giving statuses the id would be the same work #719 records
     * for messages and is not this change.
     */
    private fun requireAllowedOrigin(url: String?): String? {
        if (url == null) return null
        if (!mediaAttachmentPolicy.isAllowedOrigin(url)) {
            throw BusinessException(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE)
        }
        return url
    }

    @Transactional
    override fun createStatus(userId: UUID, content: String?, mediaUrl: String?): Status {
        val status = Status(
            userId = userId,
            content = content,
            mediaUrl = requireAllowedOrigin(mediaUrl)
        )
        val saved = statusRepository.save(status)
        log.info("Status created: id={}, user={}", saved.id, userId)
        return saved
    }

    @Transactional
    override fun createStatusWithAudience(
        userId: UUID,
        content: String?,
        mediaUrl: String?,
        visibility: String,
        excludedUserIds: List<UUID>,
        includedUserIds: List<UUID>
    ): Status {
        val status = Status(
            userId = userId,
            content = content,
            mediaUrl = requireAllowedOrigin(mediaUrl),
            visibility = visibility,
            excludedUserIds = excludedUserIds,
            includedUserIds = includedUserIds
        )
        val saved = statusRepository.save(status)
        log.info("Status created with audience: id={}, user={}, visibility={}", saved.id, userId, visibility)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getMyStatuses(userId: UUID): List<Status> {
        return statusRepository.findActiveByUserId(userId)
    }

    /**
     * Status is contact-scoped, using the same definition of "contact" as the rest of the app:
     * a user you share at least one conversation with. `UserController.resolveVisibility` gates
     * presence, last-seen and about on exactly this set, and `ChatWebSocketHandler.broadcastPresence`
     * fans presence out to exactly this set. Status was the one contact-scoped surface that did not
     * — it read every active status on the instance and filtered only on the author's own audience
     * list, so a viewer with no relationship to anybody was served everybody's status (#507).
     *
     * The audience list narrows this set further; it can never widen it. In particular "everyone"
     * means "everyone among my contacts", not every account on the server — it is the default that
     * every status carries, so treating it as unbounded is what made the leak the normal case
     * rather than an edge case.
     *
     * Scoping cannot be done on the client's word that it holds contacts permission: permission is
     * a device-side fact the server cannot verify, and a caller that lies would be served the same
     * leak. The relationship is the thing the server can actually check, and for the account in
     * #507 — brand new, no conversations — it yields the empty list the owner expected.
     *
     * **A block narrows it again (#294), in both directions (#687).** Blocking someone does not
     * delete the conversation the two share, so each stays a "contact" of the other by the only
     * definition this app has, and the statuses kept flowing both ways. Of the six surfaces a block
     * has to close, this was the one #554 left open — the send path, presence, about and the
     * group-add all grew a guard there and status did not.
     *
     * Both directions, because a block is not a request to be less visible; it is a request to be
     * done with someone. #294 asked only "can the person I blocked still watch me", so the filter
     * that answered it asked only [BlockPolicyPort.findBlockedBy] — and the blocker went on seeing
     * the blocked person's stories in the Updates tab every day. That is the half the person who
     * pressed Block was actually asking for, and it is the half that was missing.
     *
     * The audience list is not a substitute for either direction. It is the author's own per-status
     * allow/deny list, chosen in the composer; nobody goes back and edits it when they block
     * someone, and a block that required them to would be a two-step control that silently
     * half-works.
     *
     * Applied to the contact set rather than to the statuses it returns, so a hidden author's rows
     * are never read at all: nothing loaded is nothing to leak through a later change to the
     * mapping below, and it makes the query smaller rather than larger. One batched question per
     * direction for the whole set — the Updates tab resolves every contact the moment it opens, so
     * asking per author would be an N+1 on a screen that is opened constantly.
     */
    @Transactional(readOnly = true)
    override fun getContactStatusesForUser(viewerUserId: UUID): List<StatusGroup> {
        val contactIds = conversationRepository.findAllContactUserIds(viewerUserId)
        if (contactIds.isEmpty()) return emptyList()

        val authorIds = contactIds -
            blockPolicy.findBlockedBy(viewerUserId, contactIds) -
            blockPolicy.findBlockedAmong(viewerUserId, contactIds)
        if (authorIds.isEmpty()) return emptyList()

        val visible = statusRepository.findActiveByUserIds(authorIds)
            .filter { status -> isVisibleTo(status, viewerUserId) }
        if (visible.isEmpty()) return emptyList()

        val byUser = visible.groupBy { it.userId }
        val displayInfo = userDirectory.findDisplayInfo(byUser.keys)

        return byUser.map { (userId, statuses) ->
            StatusGroup(
                userId = userId,
                statuses = statuses.sortedByDescending { it.createdAt },
                displayName = displayInfo[userId]?.displayName,
                avatarUrl = displayInfo[userId]?.avatarUrl
            )
        }
    }

    /**
     * Narrowing only — the caller has already established that the author is a contact.
     * An unrecognised visibility string still honours the exclusion list rather than opening up,
     * so a value this build does not know about cannot be a way to reach a wider audience.
     */
    private fun isVisibleTo(status: Status, viewerUserId: UUID): Boolean = when (status.visibility) {
        "only_share_with" -> viewerUserId in status.includedUserIds
        else -> viewerUserId !in status.excludedUserIds
    }

    @Transactional
    override fun deleteStatus(statusId: UUID, userId: UUID) {
        val status = statusRepository.findById(statusId)
        if (status != null && status.userId == userId) {
            statusRepository.delete(statusId)
            log.info("Status deleted: id={}, user={}", statusId, userId)
        }
    }
}
