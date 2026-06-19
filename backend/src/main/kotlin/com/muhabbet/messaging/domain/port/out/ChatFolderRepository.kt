package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import java.util.UUID

interface ChatFolderRepository {
    fun save(folder: ChatFolder): ChatFolder
    fun findById(id: UUID): ChatFolder?
    fun findByOwnerId(ownerId: UUID): List<ChatFolder>
    fun countByOwnerId(ownerId: UUID): Long
    fun delete(id: UUID)

    fun addEntry(entry: ChatFolderEntry): ChatFolderEntry
    fun removeEntry(folderId: UUID, conversationId: UUID)
    fun findEntries(folderId: UUID): List<ChatFolderEntry>
}
