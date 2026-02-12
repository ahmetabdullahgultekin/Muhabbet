package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.ThumbnailPort
import com.muhabbet.media.domain.port.out.ThumbnailResult
import org.springframework.stereotype.Component
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

@Component
class JavaImageThumbnailAdapter : ThumbnailPort {

    override fun generateThumbnail(
        inputStream: InputStream,
        contentType: String,
        maxWidth: Int,
        maxHeight: Int
    ): ThumbnailResult {
        val original = ImageIO.read(inputStream)
            ?: throw IllegalArgumentException("Cannot read image")

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
}
