package com.github.pagviewer.thumbnail

import com.github.pagviewer.nativebridge.PagNativeLibrary
import com.github.pagviewer.nativebridge.PagPreviewSession
import com.intellij.openapi.diagnostic.Logger
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Path

object PagThumbnailGenerator {
    private val LOG = Logger.getInstance(PagThumbnailGenerator::class.java)
    private const val MAX_COMPOSITION_PX = 4096

    fun generate(nativeLibrary: PagNativeLibrary, bytes: ByteArray, path: Path, sizePx: Int): BufferedImage? {
        if (bytes.isEmpty() || sizePx <= 0) {
            return null
        }
        return try {
            PagPreviewSession.open(nativeLibrary, bytes, path, 1.0f, 1.0f).use { session ->
                val info = session.info
                if (info.compositionWidth > MAX_COMPOSITION_PX || info.compositionHeight > MAX_COMPOSITION_PX) {
                    null
                } else {
                    scaleToIcon(session.readFrame(0), sizePx)
                }
            }
        } catch (throwable: Throwable) {
            LOG.debug("PAG thumbnail generation failed: $path", throwable)
            null
        }
    }

    private fun scaleToIcon(frame: BufferedImage, sizePx: Int): BufferedImage {
        val target = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val scale = minOf(sizePx.toDouble() / frame.width, sizePx.toDouble() / frame.height)
            val w = maxOf(1, Math.round(frame.width * scale).toInt())
            val h = maxOf(1, Math.round(frame.height * scale).toInt())
            g.drawImage(frame, (sizePx - w) / 2, (sizePx - h) / 2, w, h, null)
        } finally {
            g.dispose()
        }
        return target
    }
}
