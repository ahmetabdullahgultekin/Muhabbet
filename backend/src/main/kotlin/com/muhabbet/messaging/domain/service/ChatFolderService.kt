package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import com.muhabbet.messaging.domain.port.`in`.ManageChatFolderUseCase
import com.muhabbet.messaging.domain.port.out.ChatFolderRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class ChatFolderService(
    private val chatFolderRepository: ChatFolderRepository,
    private val transactions: TransactionRunner
) : ManageChatFolderUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_FOLDERS_PER_USER = 20
        private const val MAX_NAME_LENGTH = 50
    }

    override fun create(ownerId: UUID, name: String): ChatFolder = transactions.inTransaction {
        val validName = validatedName(name)
        val currentCount = chatFolderRepository.countByOwnerId(ownerId)
        if (currentCount >= MAX_FOLDERS_PER_USER) {
            throw BusinessException(ErrorCode.CHAT_FOLDER_LIMIT_REACHED)
        }
        // Position is an initial ordering hint; ties are broken by created_at in the repository query.
        val saved = chatFolderRepository.save(
            ChatFolder(ownerId = ownerId, name = validName, position = currentCount.toInt())
        )
        log.info("Chat folder created: id={}, owner={}", saved.id, ownerId)
        return@inTransaction saved
    }

    override fun rename(folderId: UUID, ownerId: UUID, name: String): ChatFolder = transactions.inTransaction {
        val folder = requireOwned(folderId, ownerId)
        val validName = validatedName(name)
        return@inTransaction chatFolderRepository.save(folder.copy(name = validName))
    }

    override fun getByOwner(ownerId: UUID): List<ChatFolder> = transactions.inReadOnlyTransaction {
        chatFolderRepository.findByOwnerId(ownerId)
    }

    override fun delete(folderId: UUID, ownerId: UUID) {
        transactions.inTransaction<Unit> {
            requireOwned(folderId, ownerId)
            chatFolderRepository.delete(folderId)
            log.info("Chat folder deleted: id={}", folderId)
        }
    }

    override fun getConversationIds(folderId: UUID, ownerId: UUID): List<UUID> = transactions.inReadOnlyTransaction {
        requireOwned(folderId, ownerId)
        return@inReadOnlyTransaction chatFolderRepository.findEntries(folderId).map { it.conversationId }
    }

    override fun addConversations(
        folderId: UUID,
        ownerId: UUID,
        conversationIds: List<UUID>
    ): List<ChatFolderEntry> = transactions.inTransaction {
        requireOwned(folderId, ownerId)
        val existing = chatFolderRepository.findEntries(folderId).map { it.conversationId }.toSet()
        return@inTransaction conversationIds.filter { it !in existing }.map { conversationId ->
            chatFolderRepository.addEntry(ChatFolderEntry(folderId = folderId, conversationId = conversationId))
        }
    }

    override fun removeConversation(folderId: UUID, ownerId: UUID, conversationId: UUID) {
        transactions.inTransaction<Unit> {
            requireOwned(folderId, ownerId)
            chatFolderRepository.removeEntry(folderId, conversationId)
        }
    }

    private fun requireOwned(folderId: UUID, ownerId: UUID): ChatFolder {
        val folder = chatFolderRepository.findById(folderId)
            ?: throw BusinessException(ErrorCode.CHAT_FOLDER_NOT_FOUND)
        if (folder.ownerId != ownerId) {
            // Don't leak existence to non-owners — same error as "not found".
            throw BusinessException(ErrorCode.CHAT_FOLDER_NOT_FOUND)
        }
        return folder
    }

    private fun validatedName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            throw BusinessException(ErrorCode.CHAT_FOLDER_NAME_INVALID)
        }
        return name
    }
}
