package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.ExportedBroadcastList
import com.muhabbet.auth.domain.model.ExportedChatFolder
import com.muhabbet.auth.domain.model.ExportedChatWallpaper
import com.muhabbet.auth.domain.model.ExportedContact
import com.muhabbet.auth.domain.model.ExportedConversationMembership
import com.muhabbet.auth.domain.model.ExportedEncryptionKeySummary
import com.muhabbet.auth.domain.model.ExportedMediaFile
import com.muhabbet.auth.domain.model.ExportedMessage
import com.muhabbet.auth.domain.model.ExportedMessageBackup
import java.time.Instant
import java.util.UUID

/**
 * Reads and erases data that belongs to the `messaging`/`media` modules, from the `auth` module.
 *
 * This crosses a module boundary by table name in raw SQL rather than by importing another
 * module's JPA entities, precisely so it does NOT cross it by Kotlin import — KVKK erasure and
 * export are cross-cutting concerns that legitimately need to touch every module's data, and doing
 * that with an entity import would put a persistence-layer dependency of `messaging`/`media`
 * straight into `auth`. A SQL string against a stable table name is the narrower coupling.
 */
interface UserDataQueryPort {
    fun countMessagesByUserId(userId: UUID): Long
    fun countMediaFilesByUserId(userId: UUID): Long

    /** Cursor page over the user's messages, both sent and received. See [ExportedMessage.direction]. */
    fun findMessagesPage(userId: UUID, since: Instant, limit: Int): List<ExportedMessage>

    /** Cursor page over the media files the user uploaded. */
    fun findMediaFilesPage(userId: UUID, since: Instant, limit: Int): List<ExportedMediaFile>

    /** Every conversation the user currently belongs to, with their own membership metadata. */
    fun findConversationMemberships(userId: UUID): List<ExportedConversationMembership>

    /** Contacts the user owns (contacts that merely point *at* the user are not exported — see #341 PR notes). */
    fun findContacts(userId: UUID): List<ExportedContact>

    fun findChatWallpapers(userId: UUID): List<ExportedChatWallpaper>
    fun findChatFolders(userId: UUID): List<ExportedChatFolder>
    fun findMessageBackups(userId: UUID): List<ExportedMessageBackup>

    /** Broadcast lists the user owns, each with a member *count* only — not the member list. */
    fun findOwnedBroadcastLists(userId: UUID): List<ExportedBroadcastList>

    /** IDs of broadcast lists (owned by someone else) the user is a member of. */
    fun findBroadcastListMemberships(userId: UUID): List<UUID>

    fun findEncryptionKeySummary(userId: UUID): ExportedEncryptionKeySummary

    fun removeUserFromAllConversations(userId: UUID)

    /**
     * Erases everything that identifies the person or makes them findable: the discovery hash,
     * devices and push tokens, encryption keys, contacts, and the per-user settings rows.
     *
     * Deliberately does **not** touch messages or media. Those sit inside other people's
     * conversations, and erasing them would delete correspondence belonging to someone who has not
     * asked for anything — the "rights of others" carve-out both KVKK and GDPR Art. 17(3) allow.
     * The sender is anonymised instead, so the words remain and the person behind them does not.
     * This choice has to be what the privacy policy says; see #426.
     */
    fun erasePersonalData(userId: UUID)
}
