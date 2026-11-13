package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.ImageTooLargeException
import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.port.out.ThumbnailResult
import com.muhabbet.shared.config.MediaProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Makes JPEG thumbnails, refusing any image whose header declares more than [maxPixels].
 *
 * Decoding allocates by pixel count, not by file size: a few kilobytes of PNG can declare a
 * 50,000 x 50,000 canvas, which is several gigabytes of heap. So the dimensions are read from the
 * header first, and the image is decoded only if they are within the limit. The decode then
 * subsamples on the way in, so even an image at the limit is never held at full resolution just
 * to be shrunk to a thumbnail.
 */
@Component
class JavaImageThumbnailAdapter(private val maxPixels: Long) : ThumbnailPort {

    @Autowired
    constructor(mediaProperties: MediaProperties) : this(mediaProperties.maxImagePixels)

    override fun generateThumbnail(
        inputStream: InputStream,
        contentType: String,
        maxWidth: Int,
        maxHeight: Int
    ): ThumbnailResult {
        val original = decodeWithinLimit(inputStream, maxWidth, maxHeight)

        val (newWidth, newHeight) = calculateDimensions(
            original.width, original.height, maxWidth, maxHeight
        )

        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        graphics.drawImage(original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(scaled, "jpg", output)

        return ThumbnailResult(
            data = output.toByteArray(),
            contentType = "image/jpeg",
            width = newWidth,
            height = newHeight
        )
    }

    private fun decodeWithinLimit(inputStream: InputStream, maxWidth: Int, maxHeight: Int): BufferedImage {
        val imageInput = requireNotNull(ImageIO.createImageInputStream(inputStream)) { "Cannot read image" }
        imageInput.use { stream ->
            val reader: ImageReader = requireNotNull(ImageIO.getImageReaders(stream).asSequence().firstOrNull()) {
                "Cannot read image"
            }
            try {
                reader.setInput(stream, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > maxPixels) {
                    throw ImageTooLargeException()
                }
                val param = reader.defaultReadParam
                val step = maxOf(1, minOf(width / maxWidth, height / maxHeight) / SUBSAMPLE_HEADROOM)
                param.setSourceSubsampling(step, step, 0, 0)
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }

    private fun calculateDimensions(
        origWidth: Int, origHeight: Int,
        maxWidth: Int, maxHeight: Int
    ): Pair<Int, Int> {
        if (origWidth <= maxWidth && origHeight <= maxHeight) {
            return origWidth to origHeight
        }

        val widthRatio = maxWidth.toDouble() / origWidth
        val heightRatio = maxHeight.toDouble() / origHeight
        val ratio = minOf(widthRatio, heightRatio)

        return (origWidth * ratio).toInt() to (origHeight * ratio).toInt()
    }

    private companion object {
        /** Subsample to about twice the thumbnail size, so the smooth scale still has detail to work with. */
        const val SUBSAMPLE_HEADROOM = 2
    }
}
