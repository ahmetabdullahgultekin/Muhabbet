package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.model.ExportedBroadcastList
import com.muhabbet.auth.domain.model.ExportedChatFolder
import com.muhabbet.auth.domain.model.ExportedChatWallpaper
import com.muhabbet.auth.domain.model.ExportedContact
import com.muhabbet.auth.domain.model.ExportedConversationMembership
import com.muhabbet.auth.domain.model.ExportedEncryptionKeySummary
import com.muhabbet.auth.domain.model.ExportedMediaFile
import com.muhabbet.auth.domain.model.ExportedMessage
import com.muhabbet.auth.domain.model.ExportedMessageBackup
import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Reads `messaging`/`media`-owned tables by raw SQL rather than by importing those modules' JPA
 * entities — see the port doc for why. Every UUID column is cast `::text` and every `timestamptz`
 * column goes through `to_char(... , 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')` before it leaves Postgres, so
 * every value that reaches Kotlin is a plain, unambiguous `String`. Hibernate's native-query result
 * typing for raw UUID/timestamp objects is driver-version-dependent and this codebase had no prior
 * example of it (checked before writing this file) — text/ISO-8601 columns sidestep that ambiguity
 * entirely instead of guessing at it. Booleans and plain numeric/varchar columns are read with their
 * native JDBC types, which is the same well-established mapping the existing `COUNT(*)` queries
 * below already relied on.
 */
@Component
class UserDataQueryPersistenceAdapter(
    private val entityManager: EntityManager
) : UserDataQueryPort {

    override fun countMessagesByUserId(userId: UUID): Long =
        entityManager.createNativeQuery(
            """
            SELECT COUNT(*) FROM messages
            WHERE (sender_id = :userId
                   OR conversation_id IN (SELECT conversation_id FROM conversation_members WHERE user_id = :userId))
            """
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toLong() }

    override fun countMediaFilesByUserId(userId: UUID): Long =
        entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM media_files WHERE uploader_id = :userId"
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toLong() }

    override fun findMessagesPage(userId: UUID, since: Instant, limit: Int): List<ExportedMessage> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                m.id::text,
                m.conversation_id::text,
                m.sender_id::text,
                m.content_type,
                m.content,
                m.media_url,
                m.reply_to_id::text,
                m.forwarded_from::text,
                $TS_EXPR_PREFIX m.server_timestamp $TS_EXPR_SUFFIX,
                $TS_EXPR_PREFIX m.client_timestamp $TS_EXPR_SUFFIX,
                $TS_EXPR_PREFIX m.edited_at $TS_EXPR_SUFFIX,
                m.is_deleted,
                su.display_name,
                m.view_once
            FROM messages m
            JOIN users su ON su.id = m.sender_id
            WHERE (m.sender_id = :userId
                   OR m.conversation_id IN (SELECT conversation_id FROM conversation_members WHERE user_id = :userId))
              AND m.server_timestamp > :since
            ORDER BY m.server_timestamp ASC
            LIMIT :limitPlusOne
            """
        )
            .setParameter("userId", userId)
            .setParameter("since", since)
            .setParameter("limitPlusOne", limit + 1)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            UserDataExportMapper.toExportedMessage(
                id = r.uuid(0),
                conversationId = r.uuid(1),
                senderId = r.uuid(2),
                requestingUserId = userId,
                contentType = r.str(3),
                content = r.str(4),
                mediaUrl = r.strOrNull(5),
                replyToId = r.uuidOrNull(6),
                forwardedFromId = r.uuidOrNull(7),
                serverTimestamp = r.instant(8),
                clientTimestamp = r.instant(9),
                editedAt = r.instantOrNull(10),
                isDeleted = r.bool(11),
                senderDisplayName = r.strOrNull(12),
                viewOnce = r.bool(13)
            )
        }
    }

    override fun findMediaFilesPage(userId: UUID, since: Instant, limit: Int): List<ExportedMediaFile> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                id::text,
                content_type,
                size_bytes,
                original_filename,
                duration_seconds,
                $TS_EXPR_PREFIX created_at $TS_EXPR_SUFFIX
            FROM media_files
            WHERE uploader_id = :userId AND created_at > :since
            ORDER BY created_at ASC
            LIMIT :limitPlusOne
            """
        )
            .setParameter("userId", userId)
            .setParameter("since", since)
            .setParameter("limitPlusOne", limit + 1)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            ExportedMediaFile(
                id = r.uuid(0),
                contentType = r.str(1),
                sizeBytes = r.long(2),
                originalFilename = r.strOrNull(3),
                durationSeconds = r.intOrNull(4),
                createdAt = r.instant(5)
            )
        }
    }

    override fun findConversationMemberships(userId: UUID): List<ExportedConversationMembership> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                cm.conversation_id::text,
                c.type,
                c.name,
                cm.role,
                $TS_EXPR_PREFIX cm.joined_at $TS_EXPR_SUFFIX,
                $TS_EXPR_PREFIX cm.muted_until $TS_EXPR_SUFFIX,
                cm.pinned,
                cm.archived,
                $TS_EXPR_PREFIX cm.last_read_at $TS_EXPR_SUFFIX,
                (SELECT u2.display_name FROM conversation_members cm2
                    JOIN users u2 ON u2.id = cm2.user_id
                   WHERE cm2.conversation_id = cm.conversation_id AND cm2.user_id <> :userId
                   LIMIT 1)
            FROM conversation_members cm
            JOIN conversations c ON c.id = cm.conversation_id
            WHERE cm.user_id = :userId
            ORDER BY cm.joined_at
            """
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            UserDataExportMapper.toExportedConversationMembership(
                conversationId = r.uuid(0),
                type = r.str(1),
                name = r.strOrNull(2),
                role = r.str(3),
                joinedAt = r.instant(4),
                mutedUntil = r.instantOrNull(5),
                pinned = r.bool(6),
                archived = r.bool(7),
                lastReadAt = r.instantOrNull(8),
                rawOtherParticipantDisplayName = r.strOrNull(9)
            )
        }
    }

    override fun findContacts(userId: UUID): List<ExportedContact> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT contact_id::text, nickname, is_blocked, $TS_EXPR_PREFIX created_at $TS_EXPR_SUFFIX
            FROM contacts
            WHERE owner_id = :userId
            ORDER BY created_at
            """
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            ExportedContact(
                contactUserId = r.uuid(0),
                nickname = r.strOrNull(1),
                isBlocked = r.bool(2),
                createdAt = r.instant(3)
            )
        }
    }

    override fun findChatWallpapers(userId: UUID): List<ExportedChatWallpaper> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT conversation_id::text, wallpaper_type, $TS_EXPR_PREFIX created_at $TS_EXPR_SUFFIX
            FROM chat_wallpapers
            WHERE user_id = :userId
            ORDER BY created_at
            """
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            ExportedChatWallpaper(
                conversationId = r.uuidOrNull(0),
                wallpaperType = r.str(1),
                createdAt = r.instant(2)
            )
        }
    }

    override fun findChatFolders(userId: UUID): List<ExportedChatFolder> {
        val folderRows = entityManager.createNativeQuery(
            """
            SELECT id::text, name, position, $TS_EXPR_PREFIX created_at $TS_EXPR_SUFFIX
            FROM chat_folders
            WHERE owner_id = :userId
            ORDER BY position
            """
        )
            .setParameter("userId", userId)
            .resultList

        if (folderRows.isEmpty()) return emptyList()

        val entryRows = entityManager.createNativeQuery(
            """
            SELECT folder_id::text, conversation_id::text
            FROM chat_folder_entries
            WHERE folder_id IN (SELECT id FROM chat_folders WHERE owner_id = :userId)
            """
        )
            .setParameter("userId", userId)
            .resultList

        val conversationIdsByFolder: Map<UUID, List<UUID>> = entryRows
            .map { it as Array<*> }
            .groupBy({ it.uuid(0) }, { it.uuid(1) })

        return folderRows.map { row ->
            val r = row as Array<*>
            val folderId = r.uuid(0)
            ExportedChatFolder(
                id = folderId,
                name = r.str(1),
                position = (r[2] as Number).toInt(),
                conversationIds = conversationIdsByFolder[folderId].orEmpty(),
                createdAt = r.instant(3)
            )
        }
    }

    override fun findMessageBackups(userId: UUID): List<ExportedMessageBackup> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                id::text, status, file_size_bytes, message_count, conversation_count,
                $TS_EXPR_PREFIX started_at $TS_EXPR_SUFFIX,
                $TS_EXPR_PREFIX completed_at $TS_EXPR_SUFFIX,
                $TS_EXPR_PREFIX expires_at $TS_EXPR_SUFFIX
            FROM message_backups
            WHERE user_id = :userId
            ORDER BY started_at DESC
            """
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            ExportedMessageBackup(
                id = r.uuid(0),
                status = r.str(1),
                fileSizeBytes = (r[2] as Number?)?.toLong(),
                messageCount = r.intOrNull(3),
                conversationCount = r.intOrNull(4),
                startedAt = r.instant(5),
                completedAt = r.instantOrNull(6),
                expiresAt = r.instantOrNull(7)
            )
        }
    }

    override fun findOwnedBroadcastLists(userId: UUID): List<ExportedBroadcastList> {
        val rows = entityManager.createNativeQuery(
            """
            SELECT
                bl.id::text, bl.name, $TS_EXPR_PREFIX bl.created_at $TS_EXPR_SUFFIX,
                (SELECT COUNT(*) FROM broadcast_list_members m WHERE m.broadcast_list_id = bl.id)
            FROM broadcast_lists bl
            WHERE bl.owner_id = :userId
            ORDER BY bl.created_at
            """
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { row ->
            val r = row as Array<*>
            ExportedBroadcastList(
                id = r.uuid(0),
                name = r.str(1),
                createdAt = r.instant(2),
                memberCount = (r[3] as Number).toInt()
            )
        }
    }

    override fun findBroadcastListMemberships(userId: UUID): List<UUID> {
        val rows = entityManager.createNativeQuery(
            "SELECT broadcast_list_id::text FROM broadcast_list_members WHERE user_id = :userId"
        )
            .setParameter("userId", userId)
            .resultList

        return rows.map { UUID.fromString(it as String) }
    }

    override fun findEncryptionKeySummary(userId: UUID): ExportedEncryptionKeySummary {
        val keyRows = entityManager.createNativeQuery(
            """
            SELECT $TS_EXPR_PREFIX created_at $TS_EXPR_SUFFIX, key_version
            FROM encryption_keys
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT 1
            """
        )
            .setParameter("userId", userId)
            .resultList

        val unusedOneTimePreKeyCount = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM one_time_pre_keys WHERE user_id = :userId AND used = false"
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toInt() }

        val keyRow = keyRows.firstOrNull() as Array<*>?
        return ExportedEncryptionKeySummary(
            registered = keyRow != null,
            registeredAt = keyRow?.instant(0),
            keyVersion = keyRow?.let { (it[1] as Number).toInt() },
            unusedOneTimePreKeyCount = unusedOneTimePreKeyCount
        )
    }

    override fun removeUserFromAllConversations(userId: UUID) {
        entityManager.createNativeQuery(
            "DELETE FROM conversation_members WHERE user_id = :userId"
        )
            .setParameter("userId", userId)
            .executeUpdate()
    }

    override fun erasePersonalData(userId: UUID) {
        ERASURE_STATEMENTS.forEach { sql ->
            entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    private companion object {
        /** Every column read by this adapter goes through this to leave Postgres as a plain ISO-8601 string. */
        const val TS_EXPR_PREFIX = "to_char("
        const val TS_EXPR_SUFFIX = "AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')"

        /**
         * Ordered children-first so foreign keys do not block a delete.
         *
         * `phone_hashes` is the one that matters most and is easiest to forget: leave it and a
         * deleted person keeps turning up in other people's contact sync, which is the opposite of
         * what they asked for.
         */
        val ERASURE_STATEMENTS = listOf(
            // Discovery footprint — without this the account is erased but still findable.
            "DELETE FROM phone_hashes WHERE user_id = :userId",
            "DELETE FROM contacts WHERE owner_id = :userId OR contact_id = :userId",

            // Sessions, devices and anything that could still receive a notification.
            "DELETE FROM refresh_tokens WHERE user_id = :userId",
            "DELETE FROM login_approvals WHERE user_id = :userId",
            "DELETE FROM device_link_sessions WHERE user_id = :userId",
            "DELETE FROM devices WHERE user_id = :userId",

            // Key material. Useless once the account is gone, and it is cryptographic identity.
            "DELETE FROM one_time_pre_keys WHERE user_id = :userId",
            "DELETE FROM encryption_keys WHERE user_id = :userId",

            // Per-user settings and stores that belong to nobody else.
            // chat_folder_entries and broadcast_list_members cascade from their parents.
            "DELETE FROM chat_wallpapers WHERE user_id = :userId",
            "DELETE FROM chat_folders WHERE owner_id = :userId",
            "DELETE FROM message_backups WHERE user_id = :userId",
            "DELETE FROM broadcast_list_members WHERE user_id = :userId",
            "DELETE FROM broadcast_lists WHERE owner_id = :userId",
        )
    }
}

// ─── Row-casting helpers ──────────────────────────────────────────────────────────────────────
// Every value here already left Postgres as a String (::text / to_char — see the class doc), a
// Boolean, or a Number, which are exactly the types the JDBC driver hands back natively — no
// ambiguous Hibernate native-query type inference involved.

private fun Array<*>.str(i: Int): String = this[i] as String
private fun Array<*>.strOrNull(i: Int): String? = this[i] as String?
private fun Array<*>.uuid(i: Int): UUID = UUID.fromString(this[i] as String)
private fun Array<*>.uuidOrNull(i: Int): UUID? = (this[i] as String?)?.let { UUID.fromString(it) }
private fun Array<*>.instant(i: Int): Instant = Instant.parse(this[i] as String)
private fun Array<*>.instantOrNull(i: Int): Instant? = (this[i] as String?)?.let { Instant.parse(it) }
private fun Array<*>.bool(i: Int): Boolean = this[i] as Boolean
private fun Array<*>.long(i: Int): Long = (this[i] as Number).toLong()
private fun Array<*>.intOrNull(i: Int): Int? = (this[i] as Number?)?.toInt()
