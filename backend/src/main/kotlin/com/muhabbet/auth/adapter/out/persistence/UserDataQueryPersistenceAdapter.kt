package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.port.out.UserDataQueryPort
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserDataQueryPersistenceAdapter(
    private val entityManager: EntityManager
) : UserDataQueryPort {

    override fun countMessagesByUserId(userId: UUID): Long =
        entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM messages WHERE sender_id = :userId AND is_deleted = false"
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toLong() }

    override fun countConversationsByUserId(userId: UUID): Long =
        entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM conversation_members WHERE user_id = :userId"
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toLong() }

    override fun countMediaFilesByUserId(userId: UUID): Long =
        entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM media_files WHERE uploader_id = :userId"
        )
            .setParameter("userId", userId)
            .singleResult.let { (it as Number).toLong() }

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
