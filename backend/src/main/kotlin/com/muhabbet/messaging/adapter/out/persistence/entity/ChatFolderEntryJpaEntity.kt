package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.ChatFolderEntry
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class ChatFolderEntryId(
    val folderId: UUID = UUID.randomUUID(),
    val conversationId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "chat_folder_entries")
@IdClass(ChatFolderEntryId::class)
class ChatFolderEntryJpaEntity(
    @Id
    @Column(name = "folder_id")
    val folderId: UUID,

    @Id
    @Column(name = "conversation_id")
    val conversationId: UUID
) {
    fun toDomain(): ChatFolderEntry = ChatFolderEntry(
        folderId = folderId, conversationId = conversationId
    )

    companion object {
        fun fromDomain(e: ChatFolderEntry): ChatFolderEntryJpaEntity = ChatFolderEntryJpaEntity(
            folderId = e.folderId, conversationId = e.conversationId
        )
    }
}
