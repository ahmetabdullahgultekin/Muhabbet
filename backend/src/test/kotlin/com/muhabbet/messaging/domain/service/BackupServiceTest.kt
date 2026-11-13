package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.BackupStatus
import com.muhabbet.messaging.domain.model.BackupTransition
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageBackup
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.messaging.domain.port.out.BackupRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
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
        service = BackupService(backupRepository, conversationRepository, messageRepository, archivePort, InlineTransactionRunner())

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
            backupRepository.finish(match {
                it.status == BackupStatus.COMPLETED &&
                    it.backupUrl == "https://minio.example/presigned" &&
                    (it.fileSizeBytes ?: 0) > 0 &&
                    it.messageCount == 2 &&
                    it.conversationCount == 1 &&
                    it.expiresAt != null
            })
        }

        // Returned info reflects the completed backup.
        assertEquals("COMPLETED", info.status)
        assertEquals("https://minio.example/presigned", info.backupUrl)
        assertEquals(2, info.messageCount)
        assertEquals(1, info.conversationCount)
    }

    @Test
    fun `should archive a view-once message without its media url`() {
        val conv = Conversation(type = ConversationType.DIRECT)
        val sealed = message(conv.id, "Photo").copy(
            mediaUrl = "https://cdn.example/sealed.jpg?X-Amz-Signature=deadbeef",
            thumbnailUrl = "https://cdn.example/sealed-thumb.jpg?X-Amz-Signature=deadbeef",
            viewOnce = true
        )
        every { conversationRepository.findConversationsByUserId(userId) } returns listOf(conv)
        every { messageRepository.findByConversationId(conv.id, null, any()) } returns listOf(sealed)
        every { messageRepository.findByConversationId(conv.id, match { it != null }, any()) } returns emptyList()

        val bytesSlot = slot<ByteArray>()
        every { archivePort.upload(eq(userId), any(), capture(bytesSlot), any(), any()) } answers {
            BackupArchivePort.StoredArchive("backups/k", "https://minio.example/presigned")
        }

        service.createBackup(userId)

        // The row still appears — a backup that silently loses messages is its own bug — but the
        // presigned URL does not. It needs no credential, so archiving it would turn a backup into
        // a way of keeping a view-once photo forever (#515).
        val archiveJson = bytesSlot.captured.decodeToString()
        assertTrue(archiveJson.contains("Photo"), "the message itself should still be archived")
        assertTrue(
            !archiveJson.contains("deadbeef"),
            "a view-once blob url must not reach the archive"
        )
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
        verify { backupRepository.finish(match { it.status == BackupStatus.FAILED && it.errorMessage == "RuntimeException" }) }
    }

    /**
     * #897: the FAILED row must survive a failure thrown from a *transactional repository* during
     * the build. Such a failure marks the surrounding transaction rollback-only, so a FAILED write
     * made inside that same transaction was rolled back with it — the backup vanished. The build now
     * runs outside any transaction and the ending is written in a transaction of its own; this pins
     * that the three steps are three separate transactions.
     */
    @Test
    fun `should record FAILED in its own transaction, apart from the build that failed`() {
        every { conversationRepository.findConversationsByUserId(userId) } throws IllegalStateException("db gone")
        val blocks = mutableListOf<String>()
        val recording = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T = block().also { blocks += it.javaClass.simpleName }
        }
        service = BackupService(backupRepository, conversationRepository, messageRepository, archivePort, recording)
        every { backupRepository.finish(any()) } returns true

        val info = service.createBackup(userId)

        assertEquals("FAILED", info.status)
        // One transaction for the PENDING row, one for the ending — and the failing read in neither.
        assertEquals(listOf("MessageBackup", "Boolean"), blocks)
        verify { backupRepository.finish(match { it.status == BackupStatus.FAILED }) }
    }

    @Test
    fun `should read a completed backup past its expiry as EXPIRED`() {
        val backup = MessageBackup(
            userId = userId, status = BackupStatus.COMPLETED, backupUrl = "u",
            expiresAt = Instant.now().minusSeconds(1)
        )
        every { backupRepository.findLatestByUserId(userId) } returns backup

        assertEquals("EXPIRED", service.getLatestBackup(userId)?.status)
    }

    @Test
    fun `should only end a pending backup, and only once`() {
        val now = Instant.now()
        val pending = MessageBackup.request(userId, now)
        val completed = (pending.complete("u", 1, 0, 0, now, 60) as BackupTransition.Moved).backup

        assertEquals(BackupStatus.COMPLETED, completed.status)
        assertEquals(now.plusSeconds(60), completed.expiresAt)
        assertEquals(BackupStatus.FAILED, (pending.fail("X", now) as BackupTransition.Moved).backup.status)
        assertEquals(BackupTransition.Rejected(BackupStatus.COMPLETED), completed.fail("X", now))
        assertEquals(BackupTransition.Rejected(BackupStatus.COMPLETED), completed.complete("u", 1, 0, 0, now, 60))
    }

    @Test
    fun `should return latest backup from repository`() {
        val backup = MessageBackup(userId = userId, status = BackupStatus.COMPLETED, messageCount = 5)
        every { backupRepository.findLatestByUserId(userId) } returns backup

        val info = service.getLatestBackup(userId)

        assertNotNull(info)
        assertEquals("COMPLETED", info!!.status)
        assertEquals(5, info.messageCount)
    }
}
