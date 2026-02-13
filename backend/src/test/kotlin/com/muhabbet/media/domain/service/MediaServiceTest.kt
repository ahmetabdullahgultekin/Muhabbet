package com.muhabbet.media.domain.service

import com.muhabbet.media.domain.model.MediaFile
import com.muhabbet.media.domain.port.`in`.UploadAudioCommand
import com.muhabbet.media.domain.port.`in`.UploadImageCommand
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.port.out.ThumbnailResult
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.util.UUID

class MediaServiceTest {

    private lateinit var mediaStoragePort: MediaStoragePort
    private lateinit var mediaFileRepository: MediaFileRepository
    private lateinit var thumbnailPort: ThumbnailPort
    private lateinit var mediaService: MediaService

    private val uploaderId = UUID.randomUUID()
    private val thumbnailWidth = 200
    private val thumbnailHeight = 200

    @BeforeEach
    fun setUp() {
        mediaStoragePort = mockk(relaxed = true)
        mediaFileRepository = mockk()
        thumbnailPort = mockk()

        mediaService = MediaService(
            mediaStoragePort = mediaStoragePort,
            mediaFileRepository = mediaFileRepository,
            thumbnailPort = thumbnailPort,
            thumbnailWidth = thumbnailWidth,
            thumbnailHeight = thumbnailHeight
        )
    }

    // ─── uploadImage ──────────────────────────────────────

    @Nested
    inner class UploadImage {

        @Test
        fun `should upload image successfully when valid jpeg`() {
            val imageBytes = ByteArray(1024) { 0xFF.toByte() }
            val thumbnailBytes = ByteArray(256) { 0xAA.toByte() }

            every { thumbnailPort.generateThumbnail(any(), eq("image/jpeg"), thumbnailWidth, thumbnailHeight) } returns
                    ThumbnailResult(data = thumbnailBytes, contentType = "image/jpeg", width = 200, height = 150)
            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/jpeg",
                sizeBytes = 1024L,
                originalFilename = "photo.jpg"
            )

            val result = mediaService.uploadImage(command)

            assertEquals(uploaderId, result.uploaderId)
            assertEquals("image/jpeg", result.contentType)
            assertEquals(1024L, result.sizeBytes)
            assertEquals("photo.jpg", result.originalFilename)
            assertNotNull(result.fileKey)
            assertNotNull(result.thumbnailKey)
            assert(result.fileKey.startsWith("images/$uploaderId/"))
            assert(result.fileKey.endsWith(".jpg"))
            assert(result.thumbnailKey!!.startsWith("thumbnails/$uploaderId/"))

            // Verify storage was called twice: original + thumbnail
            verify(exactly = 2) { mediaStoragePort.putObject(any(), any(), any(), any()) }
            verify { mediaFileRepository.save(any()) }
        }

        @Test
        fun `should upload image successfully when valid png`() {
            val imageBytes = ByteArray(2048)
            val thumbnailBytes = ByteArray(128)

            every { thumbnailPort.generateThumbnail(any(), eq("image/png"), thumbnailWidth, thumbnailHeight) } returns
                    ThumbnailResult(data = thumbnailBytes, contentType = "image/jpeg", width = 200, height = 200)
            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/png",
                sizeBytes = 2048L,
                originalFilename = "screenshot.png"
            )

            val result = mediaService.uploadImage(command)

            assert(result.fileKey.endsWith(".png"))
            assertEquals("image/png", result.contentType)
        }

        @Test
        fun `should upload image successfully when valid webp`() {
            val imageBytes = ByteArray(512)
            val thumbnailBytes = ByteArray(64)

            every { thumbnailPort.generateThumbnail(any(), eq("image/webp"), thumbnailWidth, thumbnailHeight) } returns
                    ThumbnailResult(data = thumbnailBytes, contentType = "image/jpeg", width = 100, height = 100)
            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/webp",
                sizeBytes = 512L,
                originalFilename = null
            )

            val result = mediaService.uploadImage(command)

            assert(result.fileKey.endsWith(".webp"))
            assertNull(result.originalFilename)
        }

        @Test
        fun `should throw MEDIA_UNSUPPORTED_TYPE when content type is not allowed`() {
            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(ByteArray(10)),
                contentType = "image/gif",
                sizeBytes = 10L,
                originalFilename = "animated.gif"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadImage(command)
            }
            assertEquals(ErrorCode.MEDIA_UNSUPPORTED_TYPE, ex.errorCode)
        }

        @Test
        fun `should throw MEDIA_UNSUPPORTED_TYPE when content type is application pdf`() {
            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(ByteArray(10)),
                contentType = "application/pdf",
                sizeBytes = 10L,
                originalFilename = "doc.pdf"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadImage(command)
            }
            assertEquals(ErrorCode.MEDIA_UNSUPPORTED_TYPE, ex.errorCode)
        }

        @Test
        fun `should throw MEDIA_TOO_LARGE when image exceeds 10MB`() {
            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(ByteArray(10)),
                contentType = "image/jpeg",
                sizeBytes = 10L * 1024 * 1024 + 1, // 10MB + 1 byte
                originalFilename = "huge.jpg"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadImage(command)
            }
            assertEquals(ErrorCode.MEDIA_TOO_LARGE, ex.errorCode)
        }

        @Test
        fun `should accept image at exactly 10MB size limit`() {
            val imageBytes = ByteArray(100) // Small actual content for test
            val thumbnailBytes = ByteArray(50)

            every { thumbnailPort.generateThumbnail(any(), any(), any(), any()) } returns
                    ThumbnailResult(data = thumbnailBytes, contentType = "image/jpeg", width = 200, height = 200)
            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/jpeg",
                sizeBytes = 10L * 1024 * 1024, // Exactly 10MB
                originalFilename = "max_size.jpg"
            )

            // Should not throw
            val result = mediaService.uploadImage(command)
            assertNotNull(result)
        }

        @Test
        fun `should throw MEDIA_UPLOAD_FAILED when storage throws exception`() {
            val imageBytes = ByteArray(100)

            every { mediaStoragePort.putObject(any(), any(), any(), any()) } throws RuntimeException("Storage unavailable")

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/jpeg",
                sizeBytes = 100L,
                originalFilename = "test.jpg"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadImage(command)
            }
            assertEquals(ErrorCode.MEDIA_UPLOAD_FAILED, ex.errorCode)
        }

        @Test
        fun `should throw MEDIA_UPLOAD_FAILED when thumbnail generation fails`() {
            val imageBytes = ByteArray(100)

            every { thumbnailPort.generateThumbnail(any(), any(), any(), any()) } throws RuntimeException("Thumbnail failed")

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/jpeg",
                sizeBytes = 100L,
                originalFilename = "test.jpg"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadImage(command)
            }
            assertEquals(ErrorCode.MEDIA_UPLOAD_FAILED, ex.errorCode)
        }

        @Test
        fun `should generate correct file key structure for images`() {
            val imageBytes = ByteArray(100)
            val thumbnailBytes = ByteArray(50)

            every { thumbnailPort.generateThumbnail(any(), any(), any(), any()) } returns
                    ThumbnailResult(data = thumbnailBytes, contentType = "image/jpeg", width = 200, height = 200)

            val savedMediaSlot = slot<MediaFile>()
            every { mediaFileRepository.save(capture(savedMediaSlot)) } answers { savedMediaSlot.captured }

            val command = UploadImageCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(imageBytes),
                contentType = "image/jpeg",
                sizeBytes = 100L,
                originalFilename = "photo.jpg"
            )

            mediaService.uploadImage(command)

            val saved = savedMediaSlot.captured
            assert(saved.fileKey.matches(Regex("images/$uploaderId/[a-f0-9-]+\\.jpg")))
            assert(saved.thumbnailKey!!.matches(Regex("thumbnails/$uploaderId/[a-f0-9-]+\\.jpg")))
        }
    }

    // ─── uploadAudio ──────────────────────────────────────

    @Nested
    inner class UploadAudio {

        @Test
        fun `should upload audio successfully when valid ogg`() {
            val audioBytes = ByteArray(4096)

            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/ogg",
                sizeBytes = 4096L,
                durationSeconds = 30,
                originalFilename = "voice.ogg"
            )

            val result = mediaService.uploadAudio(command)

            assertEquals(uploaderId, result.uploaderId)
            assertEquals("audio/ogg", result.contentType)
            assertEquals(4096L, result.sizeBytes)
            assertEquals(30, result.durationSeconds)
            assertNull(result.thumbnailKey) // Audio should not have thumbnails
            assert(result.fileKey.startsWith("audio/$uploaderId/"))
            assert(result.fileKey.endsWith(".ogg"))

            verify(exactly = 1) { mediaStoragePort.putObject(any(), any(), any(), any()) }
        }

        @Test
        fun `should upload audio successfully when valid opus`() {
            val audioBytes = ByteArray(2048)

            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/opus",
                sizeBytes = 2048L,
                durationSeconds = 15,
                originalFilename = null
            )

            val result = mediaService.uploadAudio(command)

            assert(result.fileKey.endsWith(".opus"))
        }

        @Test
        fun `should upload audio successfully when valid mp4 audio`() {
            val audioBytes = ByteArray(3000)

            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/mp4",
                sizeBytes = 3000L,
                durationSeconds = 60,
                originalFilename = "voice.m4a"
            )

            val result = mediaService.uploadAudio(command)

            assert(result.fileKey.endsWith(".m4a"))
        }

        @Test
        fun `should throw MEDIA_UNSUPPORTED_TYPE when audio type is not allowed`() {
            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(ByteArray(10)),
                contentType = "audio/wav",
                sizeBytes = 10L,
                durationSeconds = 5,
                originalFilename = "audio.wav"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadAudio(command)
            }
            assertEquals(ErrorCode.MEDIA_UNSUPPORTED_TYPE, ex.errorCode)
        }

        @Test
        fun `should throw MEDIA_TOO_LARGE when audio exceeds 16MB`() {
            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(ByteArray(10)),
                contentType = "audio/ogg",
                sizeBytes = 16L * 1024 * 1024 + 1, // 16MB + 1 byte
                durationSeconds = 300,
                originalFilename = "long_voice.ogg"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadAudio(command)
            }
            assertEquals(ErrorCode.MEDIA_TOO_LARGE, ex.errorCode)
        }

        @Test
        fun `should accept audio at exactly 16MB size limit`() {
            val audioBytes = ByteArray(100)

            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/ogg",
                sizeBytes = 16L * 1024 * 1024, // Exactly 16MB
                durationSeconds = 600,
                originalFilename = "max_audio.ogg"
            )

            val result = mediaService.uploadAudio(command)
            assertNotNull(result)
        }

        @Test
        fun `should throw MEDIA_UPLOAD_FAILED when storage throws during audio upload`() {
            val audioBytes = ByteArray(100)

            every { mediaStoragePort.putObject(any(), any(), any(), any()) } throws RuntimeException("Disk full")

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/ogg",
                sizeBytes = 100L,
                durationSeconds = 5,
                originalFilename = "test.ogg"
            )

            val ex = assertThrows<BusinessException> {
                mediaService.uploadAudio(command)
            }
            assertEquals(ErrorCode.MEDIA_UPLOAD_FAILED, ex.errorCode)
        }

        @Test
        fun `should handle null duration for audio upload`() {
            val audioBytes = ByteArray(512)

            every { mediaFileRepository.save(any()) } answers { firstArg() }

            val command = UploadAudioCommand(
                uploaderId = uploaderId,
                inputStream = ByteArrayInputStream(audioBytes),
                contentType = "audio/ogg",
                sizeBytes = 512L,
                durationSeconds = null,
                originalFilename = "unknown_duration.ogg"
            )

            val result = mediaService.uploadAudio(command)

            assertNull(result.durationSeconds)
        }
    }

    // ─── getPresignedUrl ──────────────────────────────────

    @Nested
    inner class GetPresignedUrl {

        @Test
        fun `should return URL with thumbnail when media file has thumbnail`() {
            val mediaId = UUID.randomUUID()
            val mediaFile = MediaFile(
                id = mediaId,
                uploaderId = uploaderId,
                fileKey = "images/$uploaderId/$mediaId.jpg",
                contentType = "image/jpeg",
                sizeBytes = 1024L,
                thumbnailKey = "thumbnails/$uploaderId/$mediaId.jpg"
            )

            every { mediaFileRepository.findById(mediaId) } returns mediaFile
            every { mediaStoragePort.getPresignedUrl("images/$uploaderId/$mediaId.jpg") } returns "https://cdn.example.com/images/presigned-url"
            every { mediaStoragePort.getPresignedUrl("thumbnails/$uploaderId/$mediaId.jpg") } returns "https://cdn.example.com/thumbnails/presigned-url"

            val result = mediaService.getPresignedUrl(mediaId)

            assertEquals("https://cdn.example.com/images/presigned-url", result.url)
            assertEquals("https://cdn.example.com/thumbnails/presigned-url", result.thumbnailUrl)
        }

        @Test
        fun `should return URL without thumbnail when media file has no thumbnail`() {
            val mediaId = UUID.randomUUID()
            val mediaFile = MediaFile(
                id = mediaId,
                uploaderId = uploaderId,
                fileKey = "audio/$uploaderId/$mediaId.ogg",
                contentType = "audio/ogg",
                sizeBytes = 2048L,
                thumbnailKey = null
            )

            every { mediaFileRepository.findById(mediaId) } returns mediaFile
            every { mediaStoragePort.getPresignedUrl("audio/$uploaderId/$mediaId.ogg") } returns "https://cdn.example.com/audio/presigned-url"

            val result = mediaService.getPresignedUrl(mediaId)

            assertEquals("https://cdn.example.com/audio/presigned-url", result.url)
            assertNull(result.thumbnailUrl)
        }

        @Test
        fun `should throw MEDIA_NOT_FOUND when media file does not exist`() {
            val mediaId = UUID.randomUUID()

            every { mediaFileRepository.findById(mediaId) } returns null

            val ex = assertThrows<BusinessException> {
                mediaService.getPresignedUrl(mediaId)
            }
            assertEquals(ErrorCode.MEDIA_NOT_FOUND, ex.errorCode)
        }
    }
}
