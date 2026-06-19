package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import java.util.UUID

interface ManageChatFolderUseCase {
    fun create(ownerId: UUID, name: String): ChatFolder
    fun rename(folderId: UUID, ownerId: UUID, name: String): ChatFolder
    fun getByOwner(ownerId: UUID): List<ChatFolder>
    fun delete(folderId: UUID, ownerId: UUID)

    fun getConversationIds(folderId: UUID, ownerId: UUID): List<UUID>
    fun addConversations(folderId: UUID, ownerId: UUID, conversationIds: List<UUID>): List<ChatFolderEntry>
    fun removeConversation(folderId: UUID, ownerId: UUID, conversationId: UUID)
}
