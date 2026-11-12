package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.MessageInfo
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.`in`.MessageRecipient
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.`in`.SendMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.SendResult
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.ConversationSequencePort
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class MessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val userDirectory: UserDirectoryPort,
    private val readReceiptPolicy: ReadReceiptPolicyPort,
    private val blockPolicy: BlockPolicyPort,
    private val mediaAttachmentPolicy: MediaAttachmentPolicyPort,
    private val transactions: TransactionRunner,
    private val sequence: ConversationSequencePort
) : SendMessageUseCase, GetMessageHistoryUseCase, UpdateDeliveryStatusUseCase, ManageMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // The `EDIT_WINDOW_MINUTES` alias that stood here is gone. #597 moved the rule into
        // `ValidationRules.isWithinEditWindow` so the app and the server compute the same answer,
        // and `editMessage` has called that directly ever since — the alias was left behind
        // referring to a call site that no longer used it, which is why detekt had it as an unused
        // property. One entry off `detekt-baseline.xml` (79 → 78).

        /**
         * How many due messages one scheduled run will take on.
         *
         * The query had no bound at all, so a backlog was loaded whole into one list and, before
         * #560, into one transaction. Per-message transactions remove the second problem; the cap
         * removes the first and keeps a run's duration predictable, which matters because the job
         * is `fixedDelay` — a long run delays the next one rather than overlapping it. At one run a
         * minute this drains 12,000 messages an hour, far above anything this product will
         * schedule, and the leftovers are simply picked up by the next run in the same order.
         */
        private const val SCHEDULED_BATCH_LIMIT = 200
    }

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
    override fun sendMessage(command: SendMessageCommand): SendResult {
        // Before the transaction, because resolving a media id reads the media table and asks
        // object storage to sign a URL, and #491's rule is that nothing which talks to something
        // outside this database holds a Hikari connection while it does. A text message — every
        // message with no media reference at all — never reaches the port and pays nothing.
        val vetted = vetMediaAttachment(command)
        val outcome = try {
            transactions.inTransaction { persistSend(vetted) }
        } catch (e: BusinessException) {
            // Two copies of the same send raced: both looked, neither found the other, and the
            // second insert hit the primary key. Its transaction — and with it the sequence number
            // it had allocated — is rolled back, so no seq is spent. Look again on a fresh read,
            // where the winner is now visible, and answer exactly as the winner was answered
            // (ICD §3.6 A-1 step 3). Anything that is not that race is rethrown unchanged.
            if (e.errorCode != ErrorCode.MSG_DUPLICATE) throw e
            transactions.inTransaction { replayOf(vetted) ?: throw e }
        }

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

        return SendResult(outcome.message, outcome.replay)
    }

    /**
     * What the transaction produced. A null [recipients] means there is nothing to fan out — the
     * message was dropped for a block, it is scheduled for later, or it is a [replay] of a send
     * that already went out — as distinct from an empty list, which would mean a conversation the
     * sender is alone in.
     */
    private data class SendOutcome(
        val message: Message,
        val recipients: List<ConversationMember>?,
        val replay: Boolean = false
    )

    /**
     * The committed row this send is a retry of, as a replay — or null when there is none (#750).
     *
     * The same send means the same sender, the same conversation and the same device; a legacy row
     * with no device recorded (written before V29) matches on sender alone. An id that already
     * belongs to anything else is refused with MSG_DUPLICATE and never answered with someone
     * else's ack: before off-server delivery nobody can learn a client message id before the server
     * commits it, so a collision is a client bug, not a retry.
     */
    private fun replayOf(command: SendMessageCommand): SendOutcome? {
        // `existsById` first: it is the one query every ordinary send pays, an index-only probe that
        // answers "no" — only an actual retry goes on to load the row.
        if (!messageRepository.existsById(command.messageId)) return null
        val existing = messageRepository.findById(command.messageId) ?: return null
        val sameSend = existing.senderId == command.senderId &&
            existing.conversationId == command.conversationId &&
            (existing.senderDeviceId == null || existing.senderDeviceId == command.senderDeviceId)
        if (!sameSend) throw BusinessException(ErrorCode.MSG_DUPLICATE)
        log.info("Send replayed: id={}, conv={}, seq={}", existing.id, existing.conversationId, existing.seq)
        return SendOutcome(existing, recipients = null, replay = true)
    }

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
        // Validate content
        if (command.contentType == ContentType.TEXT) {
            requireValidContent(command.content)
        }

        // Verify sender is member
        val member = conversationRepository.findMember(command.conversationId, command.senderId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // One lookup, three uses: announcement mode, the disappearing-message window, and the
        // direct-conversation test the block check needs. It used to be fetched twice.
        val conversation = conversationRepository.findById(command.conversationId)

        // Check announcement mode — only admins/owners can send
        if (conversation != null && conversation.announcementOnly &&
            member.role == com.muhabbet.messaging.domain.model.MemberRole.MEMBER
        ) {
            throw BusinessException(ErrorCode.MSG_ANNOUNCEMENT_ONLY)
        }

        // Idempotency (#750, ADR 0013 §3). A client resending the same messageId after a
        // reconnect is an ordinary, expected event, and it used to be answered MSG_DUPLICATE — so a
        // client that had lost its ack could never learn whether the message went through. It is
        // now answered with the original ack, before anything is allocated or written. The lookup
        // is not race-proof on its own: two concurrent copies both pass it, the second insert hits
        // the primary key, and [sendMessage] turns that into the same replay.
        replayOf(command)?.let { return it }

        // Calculate expiresAt for disappearing messages
        val now = Instant.now()
        val expiresAt = conversation?.disappearAfterSeconds?.let {
            now.plusSeconds(it.toLong())
        }

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
            isScheduled = isScheduled,
            senderDeviceId = command.senderDeviceId
        )

        // Loaded once and reused for both the block test and the delivery rows below — resolving it
        // twice would put an extra query on the hottest path in the app.
        val members = conversationRepository.findMembersByConversationId(command.conversationId)

        // Dropped *before* the insert, deliberately. Persisting it and filtering on the way out
        // would need the same filter on history, background sync, search, shared media and the
        // push fan-out — five chances to leak, and a leak means the block does not work. With no
        // row there is nothing for any of them to return.
        if (isBlockedDirectSend(conversation, members, command.senderId)) {
            log.info(
                "Message dropped, recipient has blocked the sender: conv={}, sender={}",
                command.conversationId,
                command.senderId
            )
            return SendOutcome(draft, recipients = null)
        }

        val message = messageRepository.save(numbered(draft))

        // Scheduled messages are not delivered immediately
        if (isScheduled) {
            log.info("Scheduled message saved: id={}, conv={}, scheduledAt={}", message.id, command.conversationId, command.scheduledAt)
            return SendOutcome(message, recipients = null)
        }

        // Create delivery status for all recipients
        val recipients = members.filter { it.userId != command.senderId }

        // One batched insert for the whole group rather than one statement per member (#492).
        messageRepository.saveDeliveryStatuses(
            recipients.map { member ->
                MessageDeliveryStatus(
                    messageId = message.id,
                    userId = member.userId,
                    status = DeliveryStatus.SENT
                )
            }
        )

        log.info("Message sent: id={}, conv={}, sender={}", message.id, command.conversationId, command.senderId)

        // Each recipient's own ConversationMember rides along so the broadcaster can withhold the
        // offline push from whoever muted this conversation (#571) without a second query per
        // recipient. The fan-out itself happens in sendMessage, after this transaction commits.
        return SendOutcome(message, recipients)
    }

    /**
     * [draft] with its place in the conversation (#750, ADR 0014), allocated as the last step
     * before the insert because the allocation holds the conversation's row lock until commit, and
     * every statement before it would otherwise queue other senders behind it.
     *
     * The server timestamp is re-read *after* the allocation, so within a conversation timestamp
     * order and seq order agree: a send that waited on the lock cannot commit with a timestamp older
     * than the one that held it. Before this, `now` was taken at the top of the transaction and a
     * slow send could commit behind a faster one that a client had already synced past.
     *
     * A scheduled message is not numbered here. It takes its seq when it is released, so its
     * position is the moment the recipients first see it rather than the moment it was written.
     */
    private fun numbered(draft: Message): Message {
        if (draft.isScheduled) return draft
        val seq = sequence.next(draft.conversationId)
        return draft.copy(seq = seq, changeSeq = seq, serverTimestamp = Instant.now())
    }

    /**
     * Whether this send is a direct message to someone who has blocked the sender.
     *
     * **Direct conversations only, and deliberately so.** In a group a block does not stop the
     * message — WhatsApp behaves the same way — because a group message has one sender and many
     * readers, and dropping it because one of thirty members has blocked the sender would silently
     * remove it for the other twenty-nine. What a block owes you in a group is the ability to
     * leave it, not the power to censor a room.
     *
     * **One direction only.** The question is whether the *recipient* blocked the *sender*. Someone
     * messaging a person they themselves blocked is not the case with a victim, and swallowing
     * their own outgoing message would read as a bug rather than a policy.
     */
    private fun isBlockedDirectSend(
        conversation: Conversation?,
        members: List<ConversationMember>,
        senderId: UUID
    ): Boolean {
        if (conversation?.type != ConversationType.DIRECT) return false
        val recipientId = members.map { it.userId }.firstOrNull { it != senderId } ?: return false
        return blockPolicy.hasBlocked(recipientId, senderId)
    }

    @Transactional(readOnly = true)
    override fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String
    ): MessagePage {
        // Verify membership
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        val before = cursor?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                null
            }
        }

        val effectiveLimit = limit.coerceIn(1, 100)
        val messages = messageRepository.findByConversationId(conversationId, userId, before, effectiveLimit + 1)

        val hasMore = messages.size > effectiveLimit
        val page = if (hasMore) messages.take(effectiveLimit) else messages
        val nextCursor = if (hasMore) page.lastOrNull()?.serverTimestamp?.toString() else null

        return MessagePage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }

    /**
     * The busiest of the sites #669 left behind: this runs on every delivery and every read
     * receipt, roughly once per message delivered. It carries no `@Transactional` for the same
     * reason [sendMessage] does not — the annotation would hold a pool connection across the
     * status fan-out — and because [TransactionRunner] propagates as REQUIRED, leaving one on
     * would have made the inner boundary a no-op that joined the outer transaction.
     *
     * The trade the move accepts: a broadcast that fails after the commit means a stored status
     * nobody was pushed, so the sender keeps one tick until their client next loads history, which
     * resolves the status from the same rows. Handing a client a receipt for a row a rollback then
     * removed is the worse of the two, and that is the one this removes.
     */
    override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        val outcome = transactions.inTransaction { persistStatus(messageId, userId, status) }

        val message = outcome.message ?: return
        messageBroadcaster.broadcastStatusUpdate(
            messageId,
            message.conversationId,
            userId,
            message.senderId,
            outcome.published
        )

        log.debug(
            "Delivery status updated: msg={}, user={}, stored={}, published={}",
            messageId,
            userId,
            status,
            outcome.published
        )
    }

    /**
     * What the status transaction produced. A null [message] means the id did not resolve, so
     * there is nobody to tell — the same early return the method used to take mid-transaction,
     * expressed as a value because [TransactionRunner.inTransaction] binds its result to a
     * non-null type.
     */
    private data class StatusOutcome(val message: Message?, val published: DeliveryStatus)

    /**
     * Only a member of the message's conversation may ack it, and only an ack that actually moved
     * a row is published (#299).
     *
     * Both halves were missing. Any authenticated socket could name any message id and the real
     * sender was sent a `message.status` for it — a forged read receipt from someone who never saw
     * the message. And because the broadcast ignored whether anything had changed, a late
     * DELIVERED arriving after READ was still announced, dragging the sender's ticks backwards on
     * screen even though the stored row was protected.
     *
     * An unknown id gets the same MSG_NOT_MEMBER a foreign one does, so the answer cannot be used
     * to learn whether an id exists. A member acking a message they have no row for — their own,
     * or one dropped for a block — changes nothing and is told nothing.
     */
    private fun persistStatus(messageId: UUID, userId: UUID, status: DeliveryStatus): StatusOutcome {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        if (!messageRepository.updateDeliveryStatus(messageId, userId, status)) {
            return StatusOutcome(null, status)
        }

        // A reader who has turned read receipts off still gets their own row stored as READ — that
        // row is what clears their unread badge — but the sender must not be told. We downgrade what
        // is *published*, never what is stored, because one column serves both concerns.
        // Only READ can be downgraded, so DELIVERED acks skip the lookup entirely — this runs on
        // every ack from every client.
        val published = if (status == DeliveryStatus.READ) {
            publishableStatus(userId, status, readReceiptPolicy.findReadReceiptsDisabled(listOf(userId)))
        } else {
            status
        }
        return StatusOutcome(message, published)
    }

    /**
     * READ from a reader who has receipts off is published as DELIVERED — the sender learns the
     * message arrived, never that it was opened. Every other status passes through untouched.
     */
    private fun publishableStatus(
        readerId: UUID,
        status: DeliveryStatus,
        receiptsDisabled: Set<UUID>
    ): DeliveryStatus =
        if (status == DeliveryStatus.READ && readerId in receiptsDisabled) DeliveryStatus.DELIVERED else status

    @Transactional
    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        // The same guard every other method here has had (#299). Without it any socket could write
        // READ rows and a `last_read_at` into a conversation it is not part of.
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        messageRepository.markConversationRead(conversationId, userId)
        conversationRepository.updateLastReadAt(conversationId, userId, Instant.now())
        log.debug("Conversation marked as read: conv={}, user={}", conversationId, userId)
    }

    @Transactional(readOnly = true)
    override fun resolveDeliveryStatuses(messages: List<Message>, requestingUserId: UUID): Map<UUID, DeliveryStatus> {
        if (messages.isEmpty()) return emptyMap()

        val messageIds = messages.map { it.id }
        val allStatuses = messageRepository.getDeliveryStatuses(messageIds)
        val statusesByMessageId = allStatuses.groupBy { it.messageId }
        // One batched lookup for the whole page, and only for rows that are actually READ — the
        // port short-circuits on an empty collection, so a page nobody has read costs no query.
        // Without this the live WS downgrade in [updateStatus] would be undone the moment the
        // sender scrolled or reopened the chat, because the stored row is still READ.
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(
            allStatuses.filter { it.status == DeliveryStatus.READ }.map { it.userId }
        )

        return messages.associate { message ->
            val statuses = statusesByMessageId[message.id] ?: emptyList()
            val resolved = if (message.senderId == requestingUserId) {
                // Sender perspective: aggregate across all recipients
                // all READ → READ, any DELIVERED/READ → DELIVERED, else SENT
                val visible = statuses.map { publishableStatus(it.userId, it.status, receiptsDisabled) }
                if (visible.isEmpty()) DeliveryStatus.SENT
                else if (visible.all { it == DeliveryStatus.READ }) DeliveryStatus.READ
                else if (visible.any { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.READ }) DeliveryStatus.DELIVERED
                else DeliveryStatus.SENT
            } else {
                // Recipient perspective: their own status row
                statuses.firstOrNull { it.userId == requestingUserId }?.status ?: DeliveryStatus.SENT
            }
            message.id to resolved
        }
    }

    @Transactional(readOnly = true)
    override fun getMediaMessages(conversationId: UUID, userId: UUID, limit: Int, offset: Int): List<Message> {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        return messageRepository.findMediaByConversationId(
            conversationId,
            userId,
            limit.coerceIn(1, 100),
            offset.coerceAtLeast(0)
        )
    }

    @Transactional(readOnly = true)
    override fun getMessagesSince(userId: UUID, since: Instant): List<Message> {
        return messageRepository.findUndeliveredForUser(userId, since)
    }

    @Transactional(readOnly = true)
    override fun getMessageInfo(messageId: UUID, requesterId: UUID): MessageInfo {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Authorize FIRST: only members of the conversation may read message info
        // (content, senderId, recipient list). Closes the getMessageInfo IDOR.
        conversationRepository.findMember(message.conversationId, requesterId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // An unreleased scheduled message does not exist yet for anyone but its sender (#746).
        if (message.isScheduled && message.senderId != requesterId) {
            throw BusinessException(ErrorCode.MSG_NOT_FOUND)
        }

        // The sender is not a recipient of their own message.
        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
            .filter { it.userId != message.senderId }

        // One batched lookup for the whole recipient list — resolving names one at a time was an N+1.
        val displayInfo = userDirectory.findDisplayInfo(statuses.map { it.userId })

        // The third publish path, and the one that was missed. updateStatus downgrades the live WS
        // broadcast and resolveDeliveryStatuses downgrades the history aggregate, but "Info" names
        // each recipient individually and printed the stored row verbatim — so a reader who had
        // turned read receipts off still appeared under "Read by", with the time they opened it.
        // Same batched, READ-only lookup as the aggregate above (#620).
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(
            statuses.filter { it.status == DeliveryStatus.READ && it.userId != requesterId }
                .map { it.userId }
        )

        return MessageInfo(
            message = message,
            recipients = statuses.map { status ->
                val user = displayInfo[status.userId]
                // Your own state is always truthful to you — the same rule resolveDeliveryStatuses
                // applies when the requester is a recipient. The setting governs what others are
                // told about you, not what you are told about yourself.
                val published =
                    if (status.userId == requesterId) status.status
                    else publishableStatus(status.userId, status.status, receiptsDisabled)
                MessageRecipient(
                    userId = status.userId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl,
                    status = published,
                    updatedAt = if (published == status.status) status.updatedAt else null
                )
            }
        )
    }

    // ─── Message Management ──────────────────────────────────

    /**
     * The message [requesterId] is allowed to change, or the reason they are not.
     *
     * Deleting and editing ask the same three questions in the same order, and used to ask them in
     * three copied guard clauses each. One helper is the DRY answer, and it also keeps the
     * precedence deliberate: a message that does not exist and a message that is not yours are
     * answered differently, and "already deleted" is only reachable once ownership is established.
     */
    private fun requireOwnLiveMessage(messageId: UUID, requesterId: UUID): Message {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        rejectionFor(message, requesterId)?.let { throw BusinessException(it) }
        return message
    }

    /** Why [requesterId] may not change [message], or null if they may. */
    private fun rejectionFor(message: Message, requesterId: UUID): ErrorCode? = when {
        message.senderId != requesterId -> ErrorCode.MSG_NOT_SENDER
        message.isDeleted -> ErrorCode.MSG_ALREADY_DELETED
        else -> null
    }

    /**
     * The one place text content is judged, for both the send path and the edit path — which is the
     * point, because an edit that accepted what a send rejects would be a way around the limit.
     */
    private fun requireValidContent(content: String) {
        if (ValidationRules.isValidMessageContent(content)) return
        throw BusinessException(
            if (content.isBlank()) ErrorCode.MSG_EMPTY_CONTENT else ErrorCode.MSG_CONTENT_TOO_LONG
        )
    }

    /**
     * No `@Transactional`, and the fan-out is after the commit — #669, the same move [sendMessage]
     * made in #491. A delete announces itself to every member of the conversation, so the write
     * that used to hold a pool connection across that fan-out held it for as many blocking socket
     * writes as the group has people.
     *
     * The trade: if the broadcast fails after the commit, the row is deleted and an open chat goes
     * on rendering it until the client reloads history, which no longer returns it. The reverse —
     * telling everyone a message is gone and then rolling the delete back — leaves them unable to
     * see a message that still exists, with nothing to correct it.
     */
    override fun deleteMessage(messageId: UUID, requesterId: UUID) {
        val outcome = transactions.inTransaction { persistDelete(messageId, requesterId) }

        messageBroadcaster.broadcastToUsers(
            outcome.recipients,
            WsMessage.MessageDeleted(
                messageId = messageId.toString(),
                conversationId = outcome.conversationId.toString(),
                deletedBy = requesterId.toString(),
                timestamp = System.currentTimeMillis()
            )
        )

        log.info("Message {} soft-deleted by {}", messageId, requesterId)
    }

    /** What the delete transaction produced: who to tell, and which conversation to name. */
    private data class DeleteOutcome(val conversationId: UUID, val recipients: List<UUID>)

    private fun persistDelete(messageId: UUID, requesterId: UUID): DeleteOutcome {
        val message = requireOwnLiveMessage(messageId, requesterId)

        // An unreleased scheduled message exists for its sender alone (#746): telling the other
        // members it was deleted would announce a message they were never shown, and it has no
        // place in the order to stamp. Only the sender's own devices hear about it.
        if (message.isScheduled && messageRepository.softDeleteScheduled(messageId)) {
            return DeleteOutcome(message.conversationId, listOf(requesterId))
        }

        // A delete is an event in the conversation's order, so it takes a change_seq (#750).
        messageRepository.softDelete(messageId, sequence.next(message.conversationId))

        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        return DeleteOutcome(message.conversationId, members.map { it.userId })
    }

    /**
     * No `@Transactional`, and the fan-out is after the commit — #669. `MessageEdited` goes to
     * every member, so this had the same connection-per-fan-out shape as [deleteMessage].
     *
     * The trade: a broadcast that fails after the commit leaves an open chat showing the old text
     * until it reloads, where it will read the new one. The old shape could push the new text and
     * then roll the write back, leaving readers with a version of the message that exists nowhere.
     */
    override fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message {
        val outcome = transactions.inTransaction { persistEdit(messageId, requesterId, newContent) }

        // A null recipient list is the block case: nothing was written, so nothing goes out.
        outcome.recipients?.let { recipients ->
            messageBroadcaster.broadcastToUsers(
                recipients,
                WsMessage.MessageEdited(
                    messageId = messageId.toString(),
                    conversationId = outcome.message.conversationId.toString(),
                    editedBy = requesterId.toString(),
                    newContent = newContent,
                    editedAt = outcome.editedAt.toEpochMilli()
                )
            )
            log.info("Message {} edited by {}", messageId, requesterId)
        }

        return outcome.message
    }

    /**
     * What the edit transaction produced. A null [recipients] means the edit was dropped because
     * the recipient has blocked the sender — nothing was written and nothing may go out — as
     * distinct from an empty list. Same convention as [SendOutcome].
     */
    private data class EditOutcome(
        val message: Message,
        val editedAt: Instant,
        val recipients: List<UUID>?
    )

    private fun persistEdit(messageId: UUID, requesterId: UUID, newContent: String): EditOutcome {
        val message = requireOwnLiveMessage(messageId, requesterId)

        // Through the shared rule rather than a local Duration, so the answer the app computed
        // before offering "Düzenle" and the answer the server computes here cannot disagree.
        val withinWindow = ValidationRules.isWithinEditWindow(
            sentAtEpochMillis = message.serverTimestamp.toEpochMilli(),
            nowEpochMillis = Instant.now().toEpochMilli()
        )
        if (!withinWindow) {
            throw BusinessException(ErrorCode.MSG_EDIT_WINDOW_EXPIRED)
        }

        requireValidContent(newContent)

        val editedAt = Instant.now()
        val edited = message.copy(content = newContent, editedAt = editedAt)

        // An unreleased scheduled message exists for its sender alone (#746): a MessageEdited to the
        // other members would push its text to them before it is due. No change_seq either — it is
        // numbered when released. Only the sender's own devices hear about the edit.
        if (message.isScheduled && messageRepository.updateScheduledContent(messageId, newContent, editedAt)) {
            return EditOutcome(edited, editedAt, listOf(requesterId))
        }

        // `updateContent` is the *second* way message content reaches a recipient, and guarding only
        // `sendMessage` left it open: for the edit window after their last pre-block message, a
        // blocked sender could rewrite it to anything and MessageEdited would push the new text into
        // the blocker's open chat — repeatedly, on the same message. Same silence as the send path:
        // the caller is told it worked, nothing is written and nothing is broadcast.
        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)

        if (isBlockedDirectSend(conversation, members, requesterId)) {
            log.info("Message edit dropped, recipient has blocked the sender: id={}", messageId)
            return EditOutcome(edited, editedAt, recipients = null)
        }

        // An edit is an event in the conversation's order, so it takes a change_seq (#750).
        val changeSeq = sequence.next(message.conversationId)
        messageRepository.updateContent(messageId, newContent, editedAt, changeSeq)

        return EditOutcome(edited.copy(changeSeq = changeSeq), editedAt, members.map { it.userId })
    }

    // ─── Scheduled Messages ──────────────────────────────────

    /**
     * [markAsDelivered] and [softDelete] are `@Modifying` queries, and Hibernate refuses those
     * outside a transaction. Without this annotation the whole loop throws on its first write,
     * every minute, forever, and the block check below would be unreachable code — which is what
     * had been happening, the same silent shape that stopped `last_seen_at` from ever being
     * written.
     *
     * Returns the number of messages actually delivered, so the caller can distinguish a run that
     * had nothing to do from a run that did nothing. Messages dropped for a block are not counted:
     * from the sender's side they were not delivered.
     */
    open fun deliverScheduledMessages(): Int {
        val now = Instant.now()
        val scheduledMessages = messageRepository.findScheduledMessagesReadyToSend(now, SCHEDULED_BATCH_LIMIT)
        var delivered = 0

        for (message in scheduledMessages) {
            // One transaction per message, and the try/catch that makes it worth having. Before
            // this, a single message that could not be delivered took the whole run down with it:
            // the batch shared one transaction, so its rollback also undid `markAsDelivered` for
            // every message already handled, leaving them due again. The next run a minute later
            // selected the same batch in the same `scheduled_at ASC` order and hit the same message.
            // Nothing recovered from that, and the only symptom was one log line a minute (#560).
            //
            // The try/catch alone would NOT have fixed it, which is the part worth remembering.
            // Spring Data's write methods carry their own `@Transactional`; joining a shared
            // transaction and then throwing marks it rollback-only, so the catch swallows an
            // exception that has already doomed the commit and the batch dies at commit time with
            // UnexpectedRollbackException — reporting success all the way. Isolation has to come
            // from a real transaction boundary per message, which is what `inTransaction` is.
            //
            // And the method-level `@Transactional` had to go for the same reason: TransactionRunner
            // uses the default REQUIRED propagation, so with an outer transaction still in place
            // every per-message call would simply join it and the isolation would be imaginary.
            try {
                val outcome = transactions.inTransaction { deliverOneScheduled(message) }

                // Outside the transaction, as on the immediate send path (#491): the fan-out is
                // blocking WebSocket writes and a push, and holding a pool connection across them
                // is what capped the instance at twenty concurrent sends.
                if (outcome.recipients != null) {
                    messageBroadcaster.broadcastMessage(outcome.released, outcome.recipients)
                    delivered++
                    log.info("Scheduled message delivered: id={}, conv={}", message.id, message.conversationId)
                }
            } catch (ignored: AlreadyReleasedException) {
                log.debug("Scheduled message already released by another run: id={}", message.id)
            } catch (e: Exception) {
                // Named, unlike before. The old handler was in the job and logged the run, not the
                // message, so a poison message produced an identical line every minute with nothing
                // to identify it. This one can be acted on.
                log.error(
                    "Scheduled message could not be delivered, skipping it: id={}, conv={}",
                    message.id,
                    message.conversationId,
                    e
                )
            }
        }

        return delivered
    }

    /**
     * What one scheduled message's transaction produced. A null [recipients] means there is nothing
     * to fan out — it was dropped for a block — as distinct from an empty list, which is a
     * conversation the sender is alone in. Same convention as [SendOutcome].
     *
     * A wrapper rather than a bare nullable list because [TransactionRunner.inTransaction] binds its
     * result to a non-null type: Spring's template signals "no result" with null, so a block that
     * may legitimately return null would be indistinguishable from one that failed to run.
     */
    private data class ScheduledOutcome(val recipients: List<ConversationMember>?, val released: Message)

    /** A due message some other run got to first. Thrown only to roll its transaction back. */
    private class AlreadyReleasedException : RuntimeException()

    /**
     * The persistence half of delivering one scheduled message, to be run in its own transaction.
     */
    private fun deliverOneScheduled(message: Message): ScheduledOutcome {
        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)

        // A message can be scheduled before a block and come due after it. Withholding the
        // delivery rows and the fan-out is not enough on its own: the sender can still read the
        // row they scheduled, so it would sit in their chat looking sent. Soft-deleting restores
        // the invariant the immediate path gets for free — no readable row, for either party. It
        // is never released, so it never takes a seq: it has no place in the conversation.
        if (isBlockedDirectSend(conversation, members, message.senderId)) {
            messageRepository.softDelete(message.id, null)
            log.info(
                "Scheduled message dropped, recipient has blocked the sender: id={}, conv={}",
                message.id,
                message.conversationId
            )
            return ScheduledOutcome(recipients = null, released = message)
        }

        // Released now, so numbered now (ADR 0014): its position is where the recipients first see
        // it, and its timestamp moves with it — see `markAsDelivered`.
        val seq = sequence.next(message.conversationId)
        val releasedAt = Instant.now()
        if (!messageRepository.markAsDelivered(message.id, seq, releasedAt)) {
            // Another run released it while this one waited for the conversation lock. Throwing
            // rolls back the allocation above, so the race costs no seq and sends nothing twice.
            throw AlreadyReleasedException()
        }
        val released = message.copy(isScheduled = false, seq = seq, changeSeq = seq, serverTimestamp = releasedAt)

        val recipients = members.filter { it.userId != message.senderId }

        messageRepository.saveDeliveryStatuses(
            recipients.map { member ->
                MessageDeliveryStatus(
                    messageId = message.id,
                    userId = member.userId,
                    status = DeliveryStatus.SENT
                )
            }
        )

        return ScheduledOutcome(recipients, released)
    }
}
