package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBackup
import com.muhabbet.messaging.domain.port.out.MessageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BackupServiceTest {

    private lateinit var backupRepository: BackupRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var archivePort: BackupArchivePort
    private lateinit var service: BackupService

    private val userId = UUID.randomUUID()

    private fun message(conversationId: UUID, content: String) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = userId,
        content = content,
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        backupRepository = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        archivePort = mockk(relaxed = true)
        service = BackupService(backupRepository, conversationRepository, messageRepository, archivePort)

        // save() echoes back the PENDING row it was given (preserving the generated id).
        every { backupRepository.save(any()) } answers { firstArg() }
        every { archivePort.upload(any(), any(), any(), any(), any()) } answers {
            BackupArchivePort.StoredArchive(
                storageKey = "backups/$userId/x.json",
                presignedUrl = "https://minio.example/presigned"
            )
        }
    }

    @Test
    fun `should serialize messages, upload archive, and persist real url and counts`() {
        val conv = Conversation(type = ConversationType.DIRECT)
        every { conversationRepository.findConversationsByUserId(userId) } returns listOf(conv)
        // First page returns the messages; second page (paging) returns empty to stop.
        every { messageRepository.findByConversationId(conv.id, null, any()) } returns
            listOf(message(conv.id, "hello"), message(conv.id, "world"))
        every { messageRepository.findByConversationId(conv.id, match { it != null }, any()) } returns emptyList()

        val bytesSlot = slot<ByteArray>()
        every { archivePort.upload(eq(userId), any(), capture(bytesSlot), any(), any()) } answers {
            BackupArchivePort.StoredArchive("backups/k", "https://minio.example/presigned")
        }

        val info = service.createBackup(userId)

        // Real archive bytes were produced and contain the message content.
        assertTrue(bytesSlot.isCaptured)
        val archiveJson = bytesSlot.captured.decodeToString()
        assertTrue(archiveJson.contains("hello"), "archive should contain message content")
        assertTrue(archiveJson.contains("world"))

        // Status row updated with real url + counts (not the old null/zero placeholder).
        verify {
            backupRepository.updateStatus(
                id = any(),
                status = "COMPLETED",
                backupUrl = "https://minio.example/presigned",
                fileSizeBytes = match { it != null && it > 0 },
                messageCount = 2,
                conversationCount = 1,
                completedAt = any(),
                expiresAt = any()
            )
        }

        // Returned info reflects the completed backup.
        assertEquals("COMPLETED", info.status)
        assertEquals("https://minio.example/presigned", info.backupUrl)
        assertEquals(2, info.messageCount)
        assertEquals(1, info.conversationCount)
    }

    @Test
    fun `should produce an empty archive when user has no conversations`() {
        every { conversationRepository.findConversationsByUserId(userId) } returns emptyList()

        val info = service.createBackup(userId)

        assertEquals("COMPLETED", info.status)
        assertEquals(0, info.messageCount)
        assertEquals(0, info.conversationCount)
        assertNotNull(info.backupUrl)
        verify { archivePort.upload(eq(userId), any(), any(), any(), any()) }
    }

    @Test
    fun `should mark backup FAILED when archive upload throws`() {
        val conv = Conversation(type = ConversationType.DIRECT)
        every { conversationRepository.findConversationsByUserId(userId) } returns listOf(conv)
        every { messageRepository.findByConversationId(conv.id, any(), any()) } returns emptyList()
        every { archivePort.upload(any(), any(), any(), any(), any()) } throws RuntimeException("minio down")

        val info = service.createBackup(userId)

        assertEquals("FAILED", info.status)
        verify {
            backupRepository.updateStatus(
                id = any(),
                status = "FAILED",
                backupUrl = any(),
                fileSizeBytes = any(),
                messageCount = any(),
                conversationCount = any(),
                completedAt = any(),
                expiresAt = any()
            )
        }
    }

    @Test
    fun `should return latest backup from repository`() {
        val backup = MessageBackup(userId = userId, status = "COMPLETED", messageCount = 5)
        every { backupRepository.findLatestByUserId(userId) } returns backup

        val info = service.getLatestBackup(userId)

        assertNotNull(info)
        assertEquals("COMPLETED", info!!.status)
        assertEquals(5, info.messageCount)
    }
}
