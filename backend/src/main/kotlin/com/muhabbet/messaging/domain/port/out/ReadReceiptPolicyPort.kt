package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Whether a reader's read receipt may be shown to the person who sent the message.
 *
 * The preference lives on the user record, which the auth module owns. Messaging declares only the
 * question it needs answered — never the user type — so the adapter behind this port stays the one
 * place the two modules meet.
 *
 * Deliberately separate from `UserDirectoryPort`: that port answers "who is this person", this one
 * answers "what may I publish about them". A caller that only needs a display name must not be made
 * to depend on a privacy policy it never asks about.
 */
interface ReadReceiptPolicyPort {

    /**
     * Batched by contract — callers resolve a whole page of delivery rows in one query, never one
     * by one. Returns the subset of [userIds] who have turned read receipts **off**; the common
     * case (nobody has) is an empty set.
     */
    fun findReadReceiptsDisabled(userIds: Collection<UUID>): Set<UUID>
}
