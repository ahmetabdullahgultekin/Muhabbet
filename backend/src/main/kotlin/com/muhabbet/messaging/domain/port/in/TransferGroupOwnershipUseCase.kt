package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

/**
 * Hands a group's ownership from its current owner to another member (#890).
 *
 * Its own interface rather than an eighth method on [ManageGroupUseCase]: it is the one operation
 * that changes *who the owner is*, which every other role write is now forbidden to do, and callers
 * that only add or remove members have no business depending on it.
 */
interface TransferGroupOwnershipUseCase {
    /**
     * Makes [newOwnerId] the OWNER and the requester an ADMIN, in one transaction. Refused unless
     * [requesterId] is the current owner and [newOwnerId] is another member of the group.
     */
    fun transferOwnership(conversationId: UUID, requesterId: UUID, newOwnerId: UUID)
}
