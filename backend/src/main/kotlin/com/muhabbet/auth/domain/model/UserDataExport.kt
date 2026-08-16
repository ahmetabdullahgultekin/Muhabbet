package com.muhabbet.auth.domain.model

import java.time.Instant
import java.util.UUID

/**
 * The KVKK m.11 / GDPR Art. 15 & 20 "right to access, in a portable form" export.
 *
 * This used to be the user's profile plus three `COUNT(*)` numbers — it told a data subject that
 * data existed without disclosing any of it, which discharges none of the rights those articles
 * grant (#341). This type carries the data itself.
 *
 * **Privacy rule applied throughout:** a data subject's own contributions to a shared context — a
 * message they sent, their membership in a conversation, a folder they built — are their data and
 * are exported in full, including content. A conversation counterparty's identifying data is
 * exported only to the minimum needed to make the user's *own* data legible: their display name
 * (already visible in the app to anyone sharing a conversation with them), and nothing else — not
 * their phone number, avatar, "about" text, devices, or any other profile field. No type below has
 * a field capable of holding another user's phone number; the exclusion is structural, not a
 * runtime filter that a future edit could accidentally drop.
 *
 * **Symmetry with erasure:** every table [com.muhabbet.auth.domain.port.out.UserDataQueryPort.erasePersonalData]
 * deletes has a corresponding category here (devices, sessions, login approvals, linked-device
 * sessions, contacts, the phone-hash flag, chat wallpapers, chat folders, message backups,
 * broadcast lists, encryption key material). [messages] and [mediaFiles] are deliberately *not*
 * touched by erasure (the sender is anonymised instead of deleting correspondence that belongs to
 * other people too) but are exported here since they are unambiguously the requesting user's data
 * from their own side of the conversation.
 *
 * **Size:** [messages] and [mediaFiles] are the two categories that can grow without bound for an
 * active account, so both are cursor-paginated the same way [com.muhabbet.messaging.domain.port.in.GetMessageHistoryUseCase.getMessagesSince]
 * already is — fetch `limit + 1`, report `hasMore`, hand back a `nextCursor`. A synchronous export
 * that tried to inline years of history would either time out or silently cap itself; a cap that
 * isn't reported back is exactly the "quietly incomplete" failure mode this issue exists to fix.
 * Every other category here is bounded by realistic per-user counts (a handful of devices, dozens
 * of folders) and is always returned in full.
 */
data class UserDataExport(
    val exportedAt: Instant,
    val profile: ExportedProfile,
    val privacySettings: ExportedPrivacySettings,
    val devices: List<ExportedDevice>,
    val sessions: List<ExportedSession>,
    val loginApprovals: List<ExportedLoginApproval>,
    val linkedDeviceSessions: List<ExportedDeviceLinkSession>,
    val contacts: List<ExportedContact>,
    val discoverableByPhoneHash: Boolean,
    val conversations: List<ExportedConversationMembership>,
    val messages: ExportedPage<ExportedMessage>,
    val mediaFiles: ExportedPage<ExportedMediaFile>,
    val chatWallpapers: List<ExportedChatWallpaper>,
    val chatFolders: List<ExportedChatFolder>,
    val messageBackups: List<ExportedMessageBackup>,
    val ownedBroadcastLists: List<ExportedBroadcastList>,
    val broadcastListMemberships: List<UUID>,
    val encryptionKeys: ExportedEncryptionKeySummary
)

data class ExportedProfile(
    val userId: UUID,
    val phoneNumber: String,
    val displayName: String?,
    val avatarUrl: String?,
    val about: String?,
    val joinedAt: Instant,
    val twoStepVerificationEnabled: Boolean
)

data class ExportedPrivacySettings(
    val readReceiptsEnabled: Boolean,
    val onlineStatusVisibility: String,
    val aboutVisibility: String
)

data class ExportedDevice(
    val id: UUID,
    val platform: String,
    val deviceName: String?,
    val displayName: String?,
    val isPrimary: Boolean,
    val createdAt: Instant,
    val lastActiveAt: Instant?,
    val revokedAt: Instant?
)

/**
 * A login session tied to a device. The bearer token hash itself is deliberately excluded — a
 * SHA-256 hash of a session credential is a security artifact, not information about the person,
 * and the user already holds the real token client-side. Disclosing the hash back would add attack
 * surface (one more place a stolen export could be replayed against) for zero portability benefit.
 */
data class ExportedSession(
    val deviceId: UUID,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?
)

data class ExportedLoginApproval(
    val deviceName: String?,
    val platform: String?,
    val ipAddress: String?,
    val status: String,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val expiresAt: Instant
)

/**
 * The QR-handshake record for linking a companion device. [linkToken] and the companion's public
 * prekey bundle are excluded for the same reason a session's token hash is: single-use handshake
 * secrets, not data about the person.
 */
data class ExportedDeviceLinkSession(
    val status: String,
    val companionPlatform: String?,
    val companionDeviceName: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val completedAt: Instant?
)

data class ExportedContact(
    val contactUserId: UUID,
    val nickname: String?,
    val isBlocked: Boolean,
    val createdAt: Instant
)

data class ExportedConversationMembership(
    val conversationId: UUID,
    val type: String,
    /** Set for groups/channels; null for direct conversations (which have no name of their own). */
    val name: String?,
    /**
     * The other side's display name for a direct conversation only — never populated for a group,
     * where "the other participant" is not a single, well-defined person. Never a phone number.
     */
    val otherParticipantDisplayName: String?,
    val role: String,
    val joinedAt: Instant,
    val mutedUntil: Instant?,
    val pinned: Boolean,
    val archived: Boolean,
    val lastReadAt: Instant?
)

enum class MessageDirection { SENT, RECEIVED }

data class ExportedMessage(
    val id: UUID,
    val conversationId: UUID,
    val direction: MessageDirection,
    /** The sender's display name, populated for [MessageDirection.RECEIVED] only. Never a phone number. */
    val counterpartyDisplayName: String?,
    val contentType: String,
    /** Null when the message was deleted — mirrors what the app itself shows in place of deleted content. */
    val content: String?,
    val mediaUrl: String?,
    val replyToId: UUID?,
    val forwardedFromId: UUID?,
    val serverTimestamp: Instant,
    val clientTimestamp: Instant,
    val editedAt: Instant?,
    val isDeleted: Boolean
)

data class ExportedMediaFile(
    val id: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val originalFilename: String?,
    val durationSeconds: Int?,
    val createdAt: Instant
)

data class ExportedChatWallpaper(
    /** Null for the user's global default wallpaper (not scoped to one conversation). */
    val conversationId: UUID?,
    val wallpaperType: String,
    val createdAt: Instant
)

data class ExportedChatFolder(
    val id: UUID,
    val name: String,
    val position: Int,
    val conversationIds: List<UUID>,
    val createdAt: Instant
)

data class ExportedMessageBackup(
    val id: UUID,
    val status: String,
    val fileSizeBytes: Long?,
    val messageCount: Int?,
    val conversationCount: Int?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val expiresAt: Instant?
)

/** [memberCount] only — the member list itself is other users' data, not disclosed here. */
data class ExportedBroadcastList(
    val id: UUID,
    val name: String,
    val memberCount: Int,
    val createdAt: Instant
)

/**
 * Deliberately a summary, not the key material itself. Identity keys and signed prekeys are public
 * by design (they exist so strangers can start a session), but a raw base64 blob in a JSON export
 * serves no portability purpose the user can act on, and one-time prekeys are meant to be consumed
 * once and forgotten. What's exported is proof the erasure/export symmetry holds: whether the
 * account has cryptographic identity material at all.
 */
data class ExportedEncryptionKeySummary(
    val registered: Boolean,
    val registeredAt: Instant?,
    val keyVersion: Int?,
    val unusedOneTimePreKeyCount: Int
)

data class ExportedPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val totalCount: Long
) {
    companion object {
        /**
         * Builds a page out of up to `limit + 1` fetched [items] — mirrors
         * [com.muhabbet.messaging.domain.service.MessageService.getMessages]'s "fetch one extra row
         * to know whether there's more" pattern, so [hasMore][ExportedPage.hasMore] never needs its
         * own `COUNT` query. [totalCount] is a separate, one-time `COUNT` purely for display (so the
         * client can show "showing 200 of 4,812"), not used to decide pagination. [cursorOf] resolves
         * the next cursor from the last item actually returned, when there is one.
         */
        fun <T> of(items: List<T>, limit: Int, totalCount: Long, cursorOf: (T) -> String): ExportedPage<T> {
            val hasMore = items.size > limit
            val page = if (hasMore) items.take(limit) else items
            val nextCursor = if (hasMore) page.lastOrNull()?.let(cursorOf) else null
            return ExportedPage(items = page, nextCursor = nextCursor, hasMore = hasMore, totalCount = totalCount)
        }
    }
}
