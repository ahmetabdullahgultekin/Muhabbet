package com.muhabbet.app.ui.chat

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class BubbleTailShape(
    private val isOwn: Boolean,
    private val cornerRadius: Float = 48f,
    private val tailWidth: Float = 10f,
    private val tailHeight: Float = 12f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val cr = cornerRadius.coerceAtMost(minOf(w, h) / 2)

            if (isOwn) {
                // Start top-left with rounded corner
                moveTo(cr, 0f)
                lineTo(w - cr, 0f)
                // Top-right rounded corner
                quadraticBezierTo(w, 0f, w, cr)
                // Right side down to near bottom
                lineTo(w, h - tailHeight)
                // Tail on bottom-right
                lineTo(w + tailWidth, h)
                lineTo(w - cr * 0.2f, h - tailHeight + cr * 0.1f)
                // Bottom-left rounded corner
                lineTo(cr, h)
                quadraticBezierTo(0f, h, 0f, h - cr)
                // Left side
                lineTo(0f, cr)
                // Top-left corner
                quadraticBezierTo(0f, 0f, cr, 0f)
            } else {
                // Start top-left
                moveTo(cr, 0f)
                lineTo(w - cr, 0f)
                // Top-right corner
                quadraticBezierTo(w, 0f, w, cr)
                lineTo(w, h - cr)
                // Bottom-right corner
                quadraticBezierTo(w, h, w - cr, h)
                // Bottom to tail
                lineTo(cr * 0.2f, h - tailHeight + cr * 0.1f)
                // Tail on bottom-left
                lineTo(-tailWidth, h)
                lineTo(0f, h - tailHeight)
                // Left side up
                lineTo(0f, cr)
                // Top-left corner
                quadraticBezierTo(0f, 0f, cr, 0f)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
