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
}
