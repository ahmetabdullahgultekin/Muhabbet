package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.ChatFolderEntryId
import com.muhabbet.messaging.adapter.out.persistence.entity.ChatFolderEntryJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.ChatFolderJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataChatFolderJpaRepository : JpaRepository<ChatFolderJpaEntity, UUID> {
    // created_at breaks position ties stably (positions can collide/gap after deletes).
    fun findByOwnerIdOrderByPositionAscCreatedAtAsc(ownerId: UUID): List<ChatFolderJpaEntity>
    fun countByOwnerId(ownerId: UUID): Long
}

interface SpringDataChatFolderEntryRepository : JpaRepository<ChatFolderEntryJpaEntity, ChatFolderEntryId> {
    fun findByFolderId(folderId: UUID): List<ChatFolderEntryJpaEntity>

    @Modifying
    @Query("DELETE FROM ChatFolderEntryJpaEntity e WHERE e.folderId = :folderId AND e.conversationId = :conversationId")
    fun deleteByFolderIdAndConversationId(folderId: UUID, conversationId: UUID)
}
