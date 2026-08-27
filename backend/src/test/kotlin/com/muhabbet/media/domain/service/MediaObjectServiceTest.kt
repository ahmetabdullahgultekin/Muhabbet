package com.muhabbet.media.domain.service

import com.muhabbet.media.domain.model.MediaFile
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The half of #541 that makes "view once" true of the bytes rather than of the screen.
 *
 * A view-once photo used to be sealed in the UI and left sitting in MinIO behind a presigned URL
 * with a seven-day expiry — no credential needed, because the URL *is* the credential. So anyone
 * who had the string could fetch the burned photo for the rest of the week.
 *
 * What is asserted here is the *order*, not just the effect. Read, then delete the object, then
 * forget the row: each step is only safe to fail after the one before it has happened.
 */
class MediaObjectServiceTest {

    private lateinit var mediaFileRepository: MediaFileRepository
    private lateinit var mediaStoragePort: MediaStoragePort
    private lateinit var service: MediaObjectService

    private val mediaId: UUID = UUID.randomUUID()
    private val uploaderId: UUID = UUID.randomUUID()

    private val mediaFile = MediaFile(
        id = mediaId,
        uploaderId = uploaderId,
        fileKey = "images/$uploaderId/$mediaId.jpg",
        contentType = "image/jpeg",
        sizeBytes = 3,
        thumbnailKey = "thumbnails/$uploaderId/$mediaId.jpg"
    )

    @BeforeEach
    fun setUp() {
        mediaFileRepository = mockk(relaxed = true)
        mediaStoragePort = mockk(relaxed = true)
        service = MediaObjectService(mediaFileRepository, mediaStoragePort)
    }

    @Test
    fun `should return the uploader of a known object`() {
        every { mediaFileRepository.findById(mediaId) } returns mediaFile

        assertEquals(uploaderId, service.findUploaderId(mediaId))
    }

    @Test
    fun `should return no uploader for an object that does not exist`() {
        every { mediaFileRepository.findById(mediaId) } returns null

        assertNull(service.findUploaderId(mediaId))
    }

    @Test
    fun `should hand back the bytes it read`() {
        every { mediaFileRepository.findById(mediaId) } returns mediaFile
        every { mediaStoragePort.getObject(mediaFile.fileKey) } returns byteArrayOf(1, 2, 3)

        val taken = service.takeAndDestroy(mediaId)

        assertArrayEquals(byteArrayOf(1, 2, 3), taken?.bytes)
        assertEquals("image/jpeg", taken?.contentType)
    }

    @Test
    fun `should delete the object so it can no longer be fetched`() {
        every { mediaFileRepository.findById(mediaId) } returns mediaFile
        every { mediaStoragePort.getObject(mediaFile.fileKey) } returns byteArrayOf(1)

        service.takeAndDestroy(mediaId)

        verify(exactly = 1) { mediaStoragePort.deleteObject(mediaFile.fileKey) }
    }

    @Test
    fun `should delete the thumbnail too`() {
        // A thumbnail is a smaller copy of the same photo. A seal that leaves a legible 320px
        // version in the bucket is not a seal.
        every { mediaFileRepository.findById(mediaId) } returns mediaFile
        every { mediaStoragePort.getObject(any()) } returns byteArrayOf(1)

        service.takeAndDestroy(mediaId)

        verify(exactly = 1) { mediaStoragePort.deleteObject("thumbnails/$uploaderId/$mediaId.jpg") }
    }

    @Test
    fun `should read before deleting and delete before forgetting the row`() {
        every { mediaFileRepository.findById(mediaId) } returns mediaFile
        every { mediaStoragePort.getObject(mediaFile.fileKey) } returns byteArrayOf(1)

        service.takeAndDestroy(mediaId)

        // Reading first is what lets the caller show the photo while the blob is already gone.
        // Deleting the row last means a crash in between leaves metadata pointing at nothing —
        // harmless, and cleaned up by a retry — rather than an object with its key lost, still
        // reachable by anyone holding a presigned URL for it.
        verifyOrder {
            mediaStoragePort.getObject(mediaFile.fileKey)
            mediaStoragePort.deleteObject(mediaFile.fileKey)
            mediaFileRepository.deleteById(mediaId)
        }
    }

    @Test
    fun `should still destroy the object when its bytes could not be read`() {
        // MinIO said no. The photo is lost to the viewer either way; what must not happen is that
        // it stays fetchable by the URL somebody kept.
        every { mediaFileRepository.findById(mediaId) } returns mediaFile
        every { mediaStoragePort.getObject(mediaFile.fileKey) } returns null

        val taken = service.takeAndDestroy(mediaId)

        assertNull(taken)
        verify(exactly = 1) { mediaStoragePort.deleteObject(mediaFile.fileKey) }
        verify(exactly = 1) { mediaFileRepository.deleteById(mediaId) }
    }

    @Test
    fun `should do nothing for an object that is already gone`() {
        // Idempotent, so a retry of a partly failed burn cannot fail and cannot delete twice.
        every { mediaFileRepository.findById(mediaId) } returns null

        assertNull(service.takeAndDestroy(mediaId))

        verify(exactly = 0) { mediaStoragePort.deleteObject(any()) }
        verify(exactly = 0) { mediaFileRepository.deleteById(any()) }
    }
}
