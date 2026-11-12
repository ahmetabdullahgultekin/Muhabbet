package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.ImageTooLargeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO

/**
 * An image's pixel count, not its byte count, decides how much memory decoding it takes. A few
 * kilobytes of PNG can declare a canvas of billions of pixels, so the declared size is read from
 * the header and checked before anything is decoded.
 */
class JavaImageThumbnailAdapterTest {

    @Test
    fun `should refuse an image when its declared dimensions exceed the pixel limit`() {
        val adapter = JavaImageThumbnailAdapter(maxPixels = 1_000_000)

        assertThrows<ImageTooLargeException> {
            adapter.generateThumbnail(ByteArrayInputStream(pngHeaderOnly(50_000, 50_000)), "image/png", 320, 320)
        }
    }

    @Test
    fun `should make a thumbnail when the image is within the pixel limit`() {
        val adapter = JavaImageThumbnailAdapter(maxPixels = 1_000_000)
        val image = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

        val thumb = adapter.generateThumbnail(ByteArrayInputStream(png), "image/png", 320, 320)

        assertEquals(320, thumb.width)
        assertEquals(240, thumb.height)
    }

    /** A PNG signature and IHDR chunk declaring [width] x [height], and nothing to decode. */
    private fun pngHeaderOnly(width: Int, height: Int): ByteArray {
        val ihdrData = ByteBuffer.allocate(13).putInt(width).putInt(height)
            .put(8).put(2).put(0).put(0).put(0).array()
        val type = "IHDR".toByteArray()
        val crc = CRC32().apply { update(type); update(ihdrData) }.value.toInt()
        val chunk = ByteBuffer.allocate(4 + 4 + 13 + 4).putInt(13).put(type).put(ihdrData).putInt(crc).array()
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return signature + chunk
    }
}
