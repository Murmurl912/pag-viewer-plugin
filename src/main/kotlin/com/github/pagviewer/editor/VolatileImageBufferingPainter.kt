package com.github.pagviewer.editor

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.ImageCapabilities
import java.awt.image.VolatileImage

internal class VolatileImageBufferingPainter(private val transparency: Int) {
    private var buffer: VolatileImage? = null

    fun paint(graphics: Graphics, size: Dimension, painter: (Graphics2D) -> Unit) {
        if (graphics !is Graphics2D || size.width <= 0 || size.height <= 0) {
            return
        }

        val configuration = graphics.deviceConfiguration
        if (configuration == null) {
            paintDirect(graphics, painter)
            return
        }

        ensureBuffer(configuration, size)
        if (buffer == null) {
            paintDirect(graphics, painter)
            return
        }

        for (attempt in 0 until MAX_REPAINT_ATTEMPTS) {
            var current = buffer!!
            val validationResult = current.validate(configuration)
            if (validationResult == VolatileImage.IMAGE_INCOMPATIBLE) {
                flush()
                ensureBuffer(configuration, size)
                val refreshed = buffer
                if (refreshed == null) {
                    paintDirect(graphics, painter)
                    return
                }
                current = refreshed
            }

            val bufferGraphics = current.createGraphics()
            try {
                painter(bufferGraphics)
            } finally {
                bufferGraphics.dispose()
            }

            graphics.drawImage(current, 0, 0, null)
            if (!current.contentsLost()) {
                return
            }
        }

        paintDirect(graphics, painter)
    }

    fun flush() {
        buffer?.flush()
        buffer = null
    }

    private fun ensureBuffer(configuration: GraphicsConfiguration, size: Dimension) {
        val existing = buffer
        if (existing != null && existing.width == size.width && existing.height == size.height) {
            return
        }
        flush()
        buffer = try {
            configuration.createCompatibleVolatileImage(
                size.width,
                size.height,
                ImageCapabilities(true),
                transparency
            )
        } catch (exception: Exception) {
            configuration.createCompatibleVolatileImage(size.width, size.height, transparency)
        }
    }

    private fun paintDirect(graphics: Graphics2D, painter: (Graphics2D) -> Unit) {
        val copy = graphics.create() as Graphics2D
        try {
            painter(copy)
        } finally {
            copy.dispose()
        }
    }

    companion object {
        private const val MAX_REPAINT_ATTEMPTS = 3
    }
}
