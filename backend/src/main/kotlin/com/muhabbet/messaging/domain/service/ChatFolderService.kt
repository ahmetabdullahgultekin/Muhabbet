package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import com.muhabbet.messaging.domain.port.`in`.ManageChatFolderUseCase
import com.muhabbet.messaging.domain.port.out.ChatFolderRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class ChatFolderService(
    private val chatFolderRepository: ChatFolderRepository
) : ManageChatFolderUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_FOLDERS_PER_USER = 20
        private const val MAX_NAME_LENGTH = 50
    }

    @Transactional
    override fun create(ownerId: UUID, name: String): ChatFolder {
        val validName = validatedName(name)
        if (chatFolderRepository.countByOwnerId(ownerId) >= MAX_FOLDERS_PER_USER) {
            throw BusinessException(ErrorCode.CHAT_FOLDER_LIMIT_REACHED)
        }
        val position = chatFolderRepository.countByOwnerId(ownerId).toInt()
        val saved = chatFolderRepository.save(
            ChatFolder(ownerId = ownerId, name = validName, position = position)
        )
        log.info("Chat folder created: id={}, owner={}", saved.id, ownerId)
        return saved
    }

    @Transactional
    override fun rename(folderId: UUID, ownerId: UUID, name: String): ChatFolder {
        val folder = requireOwned(folderId, ownerId)
        val validName = validatedName(name)
        return chatFolderRepository.save(folder.copy(name = validName))
    }

    @Transactional(readOnly = true)
    override fun getByOwner(ownerId: UUID): List<ChatFolder> =
        chatFolderRepository.findByOwnerId(ownerId)

    @Transactional
    override fun delete(folderId: UUID, ownerId: UUID) {
        requireOwned(folderId, ownerId)
        chatFolderRepository.delete(folderId)
        log.info("Chat folder deleted: id={}", folderId)
    }

    @Transactional(readOnly = true)
    override fun getConversationIds(folderId: UUID, ownerId: UUID): List<UUID> {
        requireOwned(folderId, ownerId)
        return chatFolderRepository.findEntries(folderId).map { it.conversationId }
    }

    @Transactional
    override fun addConversations(
        folderId: UUID,
        ownerId: UUID,
        conversationIds: List<UUID>
    ): List<ChatFolderEntry> {
        requireOwned(folderId, ownerId)
        val existing = chatFolderRepository.findEntries(folderId).map { it.conversationId }.toSet()
        return conversationIds.filter { it !in existing }.map { conversationId ->
            chatFolderRepository.addEntry(ChatFolderEntry(folderId = folderId, conversationId = conversationId))
        }
    }

    @Transactional
    override fun removeConversation(folderId: UUID, ownerId: UUID, conversationId: UUID) {
        requireOwned(folderId, ownerId)
        chatFolderRepository.removeEntry(folderId, conversationId)
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
