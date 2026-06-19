package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ChatFolder
import com.muhabbet.messaging.domain.model.ChatFolderEntry
import com.muhabbet.messaging.domain.port.out.ChatFolderRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ChatFolderServiceTest {

    private val repository = mockk<ChatFolderRepository>()
    private lateinit var service: ChatFolderService

    private val ownerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        service = ChatFolderService(repository)
    }

    @Test
    fun `should create folder when under limit`() {
        every { repository.countByOwnerId(ownerId) } returns 3
        every { repository.save(any()) } answers { firstArg() }

        val folder = service.create(ownerId, "Work")

        assertEquals("Work", folder.name)
        assertEquals(3, folder.position)
        verify { repository.save(any()) }
    }

    @Test
    fun `should trim folder name on create`() {
        every { repository.countByOwnerId(ownerId) } returns 0
        every { repository.save(any()) } answers { firstArg() }

        val folder = service.create(ownerId, "  Family  ")

        assertEquals("Family", folder.name)
    }

    @Test
    fun `should throw when folder limit reached`() {
        every { repository.countByOwnerId(ownerId) } returns 20

        val ex = assertThrows<BusinessException> { service.create(ownerId, "Overflow") }
        assertEquals(ErrorCode.CHAT_FOLDER_LIMIT_REACHED, ex.errorCode)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `should throw when name is blank`() {
        val ex = assertThrows<BusinessException> { service.create(ownerId, "   ") }
        assertEquals(ErrorCode.CHAT_FOLDER_NAME_INVALID, ex.errorCode)
    }

    @Test
    fun `should throw when name too long`() {
        val ex = assertThrows<BusinessException> { service.create(ownerId, "x".repeat(51)) }
        assertEquals(ErrorCode.CHAT_FOLDER_NAME_INVALID, ex.errorCode)
    }

    @Test
    fun `should rename folder owned by user`() {
        val folderId = UUID.randomUUID()
        every { repository.findById(folderId) } returns ChatFolder(id = folderId, ownerId = ownerId, name = "Old")
        every { repository.save(any()) } answers { firstArg() }

        val result = service.rename(folderId, ownerId, "New")

        assertEquals("New", result.name)
    }

    @Test
    fun `should throw CHAT_FOLDER_NOT_FOUND when renaming a folder owned by someone else`() {
        val folderId = UUID.randomUUID()
        every { repository.findById(folderId) } returns
            ChatFolder(id = folderId, ownerId = UUID.randomUUID(), name = "NotYours")

        val ex = assertThrows<BusinessException> { service.rename(folderId, ownerId, "Hijack") }
        assertEquals(ErrorCode.CHAT_FOLDER_NOT_FOUND, ex.errorCode)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `should throw CHAT_FOLDER_NOT_FOUND when folder missing`() {
        val folderId = UUID.randomUUID()
        every { repository.findById(folderId) } returns null

        val ex = assertThrows<BusinessException> { service.delete(folderId, ownerId) }
        assertEquals(ErrorCode.CHAT_FOLDER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should add only new conversations and skip duplicates`() {
        val folderId = UUID.randomUUID()
        val existingConv = UUID.randomUUID()
        val newConv = UUID.randomUUID()
        every { repository.findById(folderId) } returns ChatFolder(id = folderId, ownerId = ownerId, name = "F")
        every { repository.findEntries(folderId) } returns listOf(ChatFolderEntry(folderId, existingConv))
        every { repository.addEntry(any()) } answers { firstArg() }

        val added = service.addConversations(folderId, ownerId, listOf(existingConv, newConv))

        assertEquals(1, added.size)
        assertEquals(newConv, added.first().conversationId)
        verify(exactly = 1) { repository.addEntry(ChatFolderEntry(folderId, newConv)) }
    }

    @Test
    fun `should return conversation ids for owned folder`() {
        val folderId = UUID.randomUUID()
        val convA = UUID.randomUUID()
        val convB = UUID.randomUUID()
        every { repository.findById(folderId) } returns ChatFolder(id = folderId, ownerId = ownerId, name = "F")
        every { repository.findEntries(folderId) } returns listOf(
            ChatFolderEntry(folderId, convA), ChatFolderEntry(folderId, convB)
        )

        val ids = service.getConversationIds(folderId, ownerId)

        assertTrue(ids.containsAll(listOf(convA, convB)))
        assertEquals(2, ids.size)
    }

    @Test
    fun `should remove conversation from owned folder`() {
        val folderId = UUID.randomUUID()
        val conv = UUID.randomUUID()
        every { repository.findById(folderId) } returns ChatFolder(id = folderId, ownerId = ownerId, name = "F")
        every { repository.removeEntry(folderId, conv) } returns Unit

        service.removeConversation(folderId, ownerId, conv)

        verify { repository.removeEntry(folderId, conv) }
    }
}
