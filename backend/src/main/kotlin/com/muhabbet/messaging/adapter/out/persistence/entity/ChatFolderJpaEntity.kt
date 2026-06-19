package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.ChatFolder
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_folders")
class ChatFolderJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "position", nullable = false)
    var position: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): ChatFolder = ChatFolder(
        id = id, ownerId = ownerId, name = name, position = position, createdAt = createdAt
    )

    companion object {
        fun fromDomain(f: ChatFolder): ChatFolderJpaEntity = ChatFolderJpaEntity(
            id = f.id, ownerId = f.ownerId, name = f.name, position = f.position, createdAt = f.createdAt
        )
    }
}
