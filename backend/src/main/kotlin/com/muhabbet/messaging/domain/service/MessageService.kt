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
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.ViewOnceReveal
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
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
    private val transactions: TransactionRunner
) : SendMessageUseCase, GetMessageHistoryUseCase, UpdateDeliveryStatusUseCase, ManageMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Moved to [ValidationRules.MESSAGE_EDIT_WINDOW_MINUTES] (#597) so the app can enforce the
         * same rule in its context menu instead of letting the user discover it by failing. Kept as
         * an alias rather than inlined at the call site so the name still reads at line 371.
         */
        private const val EDIT_WINDOW_MINUTES = ValidationRules.MESSAGE_EDIT_WINDOW_MINUTES

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
    override fun sendMessage(command: SendMessageCommand): Message {
        val outcome = transactions.inTransaction { persistSend(command) }

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

    private fun persistSend(command: SendMessageCommand): SendOutcome {
        // Validate content
        if (command.contentType == ContentType.TEXT) {
            if (!ValidationRules.isValidMessageContent(command.content)) {
                if (command.content.isBlank()) throw BusinessException(ErrorCode.MSG_EMPTY_CONTENT)
                throw BusinessException(ErrorCode.MSG_CONTENT_TOO_LONG)
            }
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
        if (isBlockedDirectSend(conversation, members, command.senderId)) {
            log.info(
                "Message dropped, recipient has blocked the sender: conv={}, sender={}",
                command.conversationId,
                command.senderId
            )
            return SendOutcome(draft, recipients = null)
        }

        val message = messageRepository.save(draft)

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
        val messages = messageRepository.findByConversationId(conversationId, before, effectiveLimit + 1)

        val hasMore = messages.size > effectiveLimit
        val page = if (hasMore) messages.take(effectiveLimit) else messages
        val nextCursor = if (hasMore) page.lastOrNull()?.serverTimestamp?.toString() else null

        return MessagePage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }

    @Transactional
    override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        messageRepository.updateDeliveryStatus(messageId, userId, status)

        val message = messageRepository.findById(messageId) ?: return
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
        messageBroadcaster.broadcastStatusUpdate(messageId, message.conversationId, userId, message.senderId, published)

        log.debug("Delivery status updated: msg={}, user={}, stored={}, published={}", messageId, userId, status, published)
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
        return messageRepository.findMediaByConversationId(conversationId, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
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

        // The sender is not a recipient of their own message.
        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
            .filter { it.userId != message.senderId }

        // One batched lookup for the whole recipient list — resolving names one at a time was an N+1.
        val displayInfo = userDirectory.findDisplayInfo(statuses.map { it.userId })

        return MessageInfo(
            message = message,
            recipients = statuses.map { status ->
                val user = displayInfo[status.userId]
                MessageRecipient(
                    userId = status.userId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl,
                    status = status.status,
                    updatedAt = status.updatedAt
                )
            }
        )
    }

    // ─── Message Management ──────────────────────────────────

    @Transactional
    override fun deleteMessage(messageId: UUID, requesterId: UUID) {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        if (message.senderId != requesterId) {
            throw BusinessException(ErrorCode.MSG_NOT_SENDER)
        }
        if (message.isDeleted) {
            throw BusinessException(ErrorCode.MSG_ALREADY_DELETED)
        }

        messageRepository.softDelete(messageId)

        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        messageBroadcaster.broadcastToUsers(
            members.map { it.userId },
            WsMessage.MessageDeleted(
                messageId = messageId.toString(),
                conversationId = message.conversationId.toString(),
                deletedBy = requesterId.toString(),
                timestamp = System.currentTimeMillis()
            )
        )

        log.info("Message {} soft-deleted by {}", messageId, requesterId)
    }

    @Transactional
    override fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        if (message.senderId != requesterId) {
            throw BusinessException(ErrorCode.MSG_NOT_SENDER)
        }
        if (message.isDeleted) {
            throw BusinessException(ErrorCode.MSG_ALREADY_DELETED)
        }

        // Through the shared rule rather than a local Duration, so the answer the app computed
        // before offering "Düzenle" and the answer the server computes here cannot disagree.
        val withinWindow = ValidationRules.isWithinEditWindow(
            sentAtEpochMillis = message.serverTimestamp.toEpochMilli(),
            nowEpochMillis = Instant.now().toEpochMilli()
        )
        if (!withinWindow) {
            throw BusinessException(ErrorCode.MSG_EDIT_WINDOW_EXPIRED)
        }

        if (!ValidationRules.isValidMessageContent(newContent)) {
            if (newContent.isBlank()) throw BusinessException(ErrorCode.MSG_EMPTY_CONTENT)
            throw BusinessException(ErrorCode.MSG_CONTENT_TOO_LONG)
        }

        val editedAt = Instant.now()

        // `updateContent` is the *second* way message content reaches a recipient, and guarding only
        // `sendMessage` left it open: for EDIT_WINDOW_MINUTES after their last pre-block message, a
        // blocked sender could rewrite it to anything and MessageEdited would push the new text into
        // the blocker's open chat — repeatedly, on the same message. Same silence as the send path:
        // the caller is told it worked, nothing is written and nothing is broadcast.
        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        if (isBlockedDirectSend(conversation, members, requesterId)) {
            log.info("Message edit dropped, recipient has blocked the sender: id={}", messageId)
            return message.copy(content = newContent, editedAt = editedAt)
        }

        messageRepository.updateContent(messageId, newContent, editedAt)

        messageBroadcaster.broadcastToUsers(
            members.map { it.userId },
            WsMessage.MessageEdited(
                messageId = messageId.toString(),
                conversationId = message.conversationId.toString(),
                editedBy = requesterId.toString(),
                newContent = newContent,
                editedAt = editedAt.toEpochMilli()
            )
        )

        log.info("Message {} edited by {}", messageId, requesterId)
        return message.copy(content = newContent, editedAt = editedAt)
    }

    // ─── View-Once ────────────────────────────────────────────

    /**
     * Burns a view-once message and hands back its media, once.
     *
     * The order matters and every step earns its place:
     *
     * - **Authorize before anything else** — a non-member who knows the messageId must not be able
     *   to burn someone else's message (the IDOR closed by #55).
     * - **The sender is refused**, so opening your own view-once photo cannot consume the
     *   recipient's one look. The sender's own copy is stripped by `toSharedMessage` too; a
     *   view-once photo is not re-viewable by anyone once it leaves the composer.
     * - **The write is the arbiter, not the read.** `viewedAt != null` above is a cheap rejection
     *   for the common case, but two taps can both pass it. The conditional UPDATE can only match
     *   once, so a zero row count means someone else won and this caller gets nothing.
     */
    @Transactional
    override fun markViewOnceViewed(messageId: UUID, userId: UUID): ViewOnceReveal {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        if (!message.viewOnce) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        if (message.viewedAt != null) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        if (message.senderId == userId) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        val viewedAt = Instant.now()
        if (messageRepository.markViewOnceViewed(messageId, userId, viewedAt) == 0) {
            throw BusinessException(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED)
        }

        log.info("View-once message viewed: msg={}, user={}", messageId, userId)
        return ViewOnceReveal(
            messageId = messageId,
            mediaUrl = message.mediaUrl,
            thumbnailUrl = message.thumbnailUrl,
            viewedAt = viewedAt
        )
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
                    messageBroadcaster.broadcastMessage(message, outcome.recipients)
                    delivered++
                    log.info("Scheduled message delivered: id={}, conv={}", message.id, message.conversationId)
                }
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
    private data class ScheduledOutcome(val recipients: List<ConversationMember>?)

    /**
     * The persistence half of delivering one scheduled message, to be run in its own transaction.
     */
    private fun deliverOneScheduled(message: Message): ScheduledOutcome {
        messageRepository.markAsDelivered(message.id)

        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)

        // A message can be scheduled before a block and come due after it. Withholding the
        // delivery rows and the fan-out is not enough on its own: the row was written at
        // schedule time and history filters on `isDeleted`, not on `isScheduled`, so it would
        // simply appear in the recipient's chat. Soft-deleting restores the invariant the
        // immediate path gets for free — no readable row, for either party.
        if (isBlockedDirectSend(conversation, members, message.senderId)) {
            messageRepository.softDelete(message.id)
            log.info(
                "Scheduled message dropped, recipient has blocked the sender: id={}, conv={}",
                message.id,
                message.conversationId
            )
            return ScheduledOutcome(recipients = null)
        }

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

        return ScheduledOutcome(recipients)
    }
}
