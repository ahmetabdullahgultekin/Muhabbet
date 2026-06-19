package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.ChatFolderEntryJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.ChatFolderJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataChatFolderEntryRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataChatFolderJpaRepository
import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import com.muhabbet.messaging.domain.port.out.ChatFolderRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatFolderPersistenceAdapter(
    private val folderRepo: SpringDataChatFolderJpaRepository,
    private val entryRepo: SpringDataChatFolderEntryRepository
) : ChatFolderRepository {

    override fun save(folder: ChatFolder): ChatFolder =
        folderRepo.save(ChatFolderJpaEntity.fromDomain(folder)).toDomain()

    override fun findById(id: UUID): ChatFolder? =
        folderRepo.findById(id).orElse(null)?.toDomain()

    override fun findByOwnerId(ownerId: UUID): List<ChatFolder> =
        folderRepo.findByOwnerIdOrderByPositionAscCreatedAtAsc(ownerId).map { it.toDomain() }

    override fun countByOwnerId(ownerId: UUID): Long =
        folderRepo.countByOwnerId(ownerId)

    override fun delete(id: UUID) =
        folderRepo.deleteById(id)

    override fun addEntry(entry: ChatFolderEntry): ChatFolderEntry =
        entryRepo.save(ChatFolderEntryJpaEntity.fromDomain(entry)).toDomain()

    override fun removeEntry(folderId: UUID, conversationId: UUID) =
        entryRepo.deleteByFolderIdAndConversationId(folderId, conversationId)

    override fun findEntries(folderId: UUID): List<ChatFolderEntry> =
        entryRepo.findByFolderId(folderId).map { it.toDomain() }
}
