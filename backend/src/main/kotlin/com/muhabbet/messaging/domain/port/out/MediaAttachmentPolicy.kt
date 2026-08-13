package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Decides whether a sender is allowed to attach a given media reference to a message.
 *
 * Media authorization works backwards from messages: a user may download a blob if some conversation
 * they belong to holds a message referencing it. That makes the reference on the message the thing
 * granting access, so writing one has to be guarded — otherwise a user can author the very message
 * that authorizes them and read anyone's media (#267).
 */
interface MediaAttachmentPolicy {

    /**
     * True when [senderId] uploaded the media behind [mediaUrl], or can already reach it through a
     * conversation they belong to.
     *
     * The second arm is what keeps forwarding working: the recipient of an image does not own it, but
     * they can already see it, so passing it on grants nobody anything new.
     */
    fun canAttach(senderId: UUID, mediaUrl: String): Boolean
}
