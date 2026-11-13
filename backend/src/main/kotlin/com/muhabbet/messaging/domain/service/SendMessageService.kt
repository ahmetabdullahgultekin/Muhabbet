package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Creating a message — the hottest path in the application, in a class of its own (#894).
 *
 * It was one quarter of a 761-line `MessageService` that also ran receipts, history, edits and the
 * scheduler. Its rules — membership, announcement mode, idempotency, the direct-message block, the
 * media-origin check and the transaction boundary that keeps fan-out outside the commit — now sit
 * in one file a reviewer can read end to end.
 */
class SendMessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    blockPolicy: BlockPolicyPort,
    private val mediaAttachmentPolicy: MediaAttachmentPolicyPort,
    private val transactions: TransactionRunner
) : SendMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val directSendBlock = DirectSendBlockPolicy(blockPolicy)

    /**
     * The one place a message is created, for both transports — the WebSocket handler and the REST
     * fallback call this same use case — which is why the block is enforced here and nowhere else.
     *
     * **The blocked sender is not told.** A dropped message is answered with the same
     * `ServerAck(OK)` / `200` a delivered one gets, so the sender's client shows a single tick that
     * never becomes two. That is what every messenger does, and it is a decision rather than an
     * accident of where the check sits: the alternative — an error the client can see — turns a
     * block into a probe, telling a harasser exactly who has blocked them and when they unblock.
     */
    override fun sendMessage(command: SendMessageCommand): Message {
        // Before the transaction, because resolving a media id reads the media table and asks
        // object storage to sign a URL, and #491's rule is that nothing which talks to something
        // outside this database holds a Hikari connection while it does. A text message — every
        // message with no media reference at all — never reaches the port and pays nothing.
        val vetted = vetMediaAttachment(command)
        val outcome = transactions.inTransaction { persistSend(vetted) }

        // Outside the transaction, and that is the entire point of #491.
        //
        // This call writes to every recipient's WebSocket, publishes to Redis once per offline
        // recipient and hands the push fan-out its work. A WebSocket write is blocking, and
        // Tomcat's blocking send timeout is twenty seconds; while any of it ran, the Hikari
        // connection this send had checked out was still checked out. The pool is twenty, so
        // twenty in-flight sends were the ceiling for the whole instance and the twenty-first
        // caller waited five seconds and failed — with the database idle and two hundred Tomcat
        // threads free.
        //
        // Moving it out also fixes an ordering bug that was quieter and worse: recipients were
        // handed the message before the transaction that created it committed, so a rollback left
        // them holding a message the database did not have.
        //
        // On the caller's own thread, deliberately. An executor would return the connection just
        // as well and would let two messages in one conversation change places on the way out,
        // which for a chat application is a worse bug than the one being fixed.
        outcome.recipients?.let { messageBroadcaster.broadcastMessage(outcome.message, it) }

        return outcome.message
    }

    /**
     * What the transaction produced. A null [recipients] means there is nothing to fan out — the
     * message was dropped for a block, or it is scheduled for later — as distinct from an empty
     * list, which would mean a conversation the sender is alone in.
     */
    private data class SendOutcome(val message: Message, val recipients: List<ConversationMember>?)

    /**
     * Settles what this message's media URLs will be before anything is written (#679).
     *
     * The URL on a message is not decoration: the recipient's client fetches it to draw the bubble,
     * with no tap and no confirmation. Whatever string wins here is a request the server has decided
     * every recipient's device will make, so it may not be a string the sender chose freely.
     *
     * Two outcomes, no third:
     *
     *  * The sender named a `mediaId` the server can confirm is their own upload → **the server's
     *    own URLs replace whatever the client sent**, which is discarded unread. This is the case
     *    the photo path takes, and after it `media_url` holds a value this process minted.
     *  * Otherwise — a forward, a GIF or sticker off GIPHY's CDN, a client older than `mediaId`, or
     *    an id that buys nothing — the client's string survives only if it sits on one of the
     *    origins this deployment publishes media from.
     *
     * A URL that is neither is refused, and refused here rather than at the controller so both
     * transports get it: the WebSocket frame and the REST fallback are the same use case.
     *
     * What this does not claim: for the second case the URL is still a string a client wrote, and
     * "on an allowed origin" is weaker than "this sender's blob". It is as strong as a forward can
     * be — forwarding deliberately carries no id, because claiming the original sender's blob would
     * hand the forwarder the power to destroy it (#541) — and it closes every harm in #679, all of
     * which need the *sender* to own the server the recipient's phone is made to talk to.
     */
    private fun vetMediaAttachment(command: SendMessageCommand): SendMessageCommand {
        if (command.mediaUrl == null && command.thumbnailUrl == null && command.mediaId == null) {
            return command
        }

        val resolved = command.mediaId?.let { mediaAttachmentPolicy.resolveOwnUpload(it, command.senderId) }

        // The thumbnail falls back to the client's value when the blob has none of its own — a voice
        // note or a document has no thumbnail row — and that fallback is checked like any other
        // client string rather than trusted because its sibling resolved.
        return command.copy(
            mediaUrl = resolved?.mediaUrl ?: requireAllowedOrigin(command.mediaUrl),
            thumbnailUrl = resolved?.thumbnailUrl ?: requireAllowedOrigin(command.thumbnailUrl)
        )
    }

    private fun requireAllowedOrigin(url: String?): String? {
        if (url == null) return null
        if (!mediaAttachmentPolicy.isAllowedOrigin(url)) {
            throw BusinessException(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE)
        }
        return url
    }

    private fun persistSend(command: SendMessageCommand): SendOutcome {
        if (command.contentType == ContentType.TEXT) {
            requireValidMessageContent(command.content)
        }

        val member = conversationRepository.findMember(command.conversationId, command.senderId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // One lookup, three uses: announcement mode, the disappearing-message window, and the
        // direct-conversation test the block check needs. It used to be fetched twice.
        val conversation = conversationRepository.findById(command.conversationId)

        // Announcement mode — only admins/owners can send
        if (conversation != null && conversation.announcementOnly && member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.MSG_ANNOUNCEMENT_ONLY)
        }

        // Idempotency check. Kept, against #492's suggestion to drop it and let the primary key
        // catch a duplicate: now that the insert is a real `persist` rather than a `merge`, a
        // duplicate id surfaces as a constraint violation at flush — after the delivery rows have
        // been written and as something far less legible than MSG_DUPLICATE. A client resending the
        // same messageId after a reconnect is an ordinary, expected event on this path, and one
        // SELECT to answer it cleanly is worth keeping. It is not a race-proof guard and never was;
        // two concurrent sends of the same id still both pass it and the second fails at commit,
        // which is the correct outcome.
        if (messageRepository.existsById(command.messageId)) {
            throw BusinessException(ErrorCode.MSG_DUPLICATE)
        }

        val now = Instant.now()
        val expiresAt = conversation?.disappearAfterSeconds?.let { now.plusSeconds(it.toLong()) }
        val isScheduled = command.scheduledAt != null && command.scheduledAt.isAfter(now)

        val draft = Message(
            id = command.messageId,
            conversationId = command.conversationId,
            senderId = command.senderId,
            contentType = command.contentType,
            content = command.content,
            replyToId = command.replyToId,
            mediaUrl = command.mediaUrl,
            thumbnailUrl = command.thumbnailUrl,
            // Recorded as the client asserted it, and trusted by nothing on this path.
            // `ViewOnceService` checks that the object really was this sender's upload immediately
            // before it destroys anything — the check belongs next to the consequence, and putting
            // it here would make every text message in the app carry a media dependency it never
            // uses (#541).
            mediaId = command.mediaId,
            serverTimestamp = now,
            clientTimestamp = command.clientTimestamp,
            expiresAt = expiresAt,
            forwardedFrom = command.forwardedFrom,
            viewOnce = command.viewOnce,
            scheduledAt = command.scheduledAt,
            isScheduled = isScheduled
        )

        // Loaded once and reused for both the block test and the delivery rows below — resolving it
        // twice would put an extra query on the hottest path in the app.
        val members = conversationRepository.findMembersByConversationId(command.conversationId)

        // Dropped *before* the insert, deliberately. Persisting it and filtering on the way out
        // would need the same filter on history, background sync, search, shared media and the
        // push fan-out — five chances to leak, and a leak means the block does not work. With no
        // row there is nothing for any of them to return.
        if (directSendBlock.isBlocked(conversation, members, command.senderId)) {
            log.info(
                "Message dropped, recipient has blocked the sender: conv={}, sender={}",
                command.conversationId,
                command.senderId
            )
            return SendOutcome(draft, recipients = null)
        }

        val message = messageRepository.save(draft)

        // Scheduled messages are not delivered now; ScheduledDeliveryService picks them up.
        if (isScheduled) {
            log.info(
                "Scheduled message saved: id={}, conv={}, scheduledAt={}",
                message.id,
                command.conversationId,
                command.scheduledAt
            )
            return SendOutcome(message, recipients = null)
        }

        val recipients = members.filter { it.userId != command.senderId }

        // One batched insert for the whole group rather than one statement per member (#492).
        messageRepository.saveDeliveryStatuses(sentStatusesFor(message.id, recipients))

        log.info("Message sent: id={}, conv={}, sender={}", message.id, command.conversationId, command.senderId)

        // Each recipient's own ConversationMember rides along so the broadcaster can withhold the
        // offline push from whoever muted this conversation (#571) without a second query per
        // recipient. The fan-out itself happens in sendMessage, after this transaction commits.
        return SendOutcome(message, recipients)
    }
}
