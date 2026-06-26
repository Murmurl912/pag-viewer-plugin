package com.github.pagviewer.thumbnail

import com.github.pagviewer.file.PagFileType
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IconDeferrer
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.ceil

class PagThumbnailIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (!shouldProvideThumbnail(file, Registry.`is`("pag.viewer.thumbnails"))) {
            return null
        }
        return IconDeferrer.getInstance().defer(baseIcon, file) { buildThumbnailOrBase(it) }
    }

    private fun buildThumbnailOrBase(file: VirtualFile): Icon {
        val key = PagThumbnailCache.cacheKey(file)
        PagThumbnailCache.get(key)?.let { cached ->
            return if (cached === PagThumbnailCache.noThumbnail) baseIcon else cached
        }
        val icon = generateIcon(file)
        PagThumbnailCache.put(key, icon)
        return icon ?: baseIcon
    }

    private fun generateIcon(file: VirtualFile): Icon? {
        val nativeLibrary = SharedPagNativeLibrary.get() ?: return null
        val path = file.toNioPath() ?: return null
        val bytes = try {
            file.contentsToByteArray()
        } catch (throwable: Throwable) {
            LOG.debug("PAG thumbnail read failed: ${file.path}", throwable)
            return null
        }
        val logical = BASE_ICON_PX
        val devicePx = thumbnailDeviceSize(logical)
        val image: BufferedImage = PagThumbnailGenerator.generate(nativeLibrary, bytes, path, devicePx) ?: return null
        return PagThumbnailIcon(image, logical)
    }

    companion object {
        private val LOG = Logger.getInstance(PagThumbnailIconProvider::class.java)
        private const val BASE_ICON_PX = 16
        private const val MAX_FILE_BYTES = 32L * 1024L * 1024L
        private val baseIcon: Icon by lazy { PagFileType.getIcon() ?: AllIcons.FileTypes.Any_type }

        fun shouldProvideThumbnail(file: VirtualFile, enabled: Boolean): Boolean {
            if (!enabled) {
                return false
            }
            if (file.isDirectory) {
                return false
            }
            if (!"pag".equals(file.extension, ignoreCase = true)) {
                return false
            }
            return file.length in 1..MAX_FILE_BYTES
        }

        fun thumbnailDeviceSize(logicalPx: Int, deviceScale: Double = currentDeviceScale()): Int =
            maxOf(logicalPx, ceil(logicalPx * deviceScale).toInt())

        private fun currentDeviceScale(): Double {
            if (GraphicsEnvironment.isHeadless()) {
                return 1.0
            }
            return try {
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                    .defaultConfiguration
                    .defaultTransform
                    .scaleX
                    .coerceAtLeast(1.0)
            } catch (_: Throwable) {
                1.0
            }
        }
    }

    private class PagThumbnailIcon(
        private val image: BufferedImage,
        private val logicalSize: Int
    ) : Icon {
        override fun getIconWidth(): Int = logicalSize

        override fun getIconHeight(): Int = logicalSize

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            val graphics2D = graphics.create() as Graphics2D
            try {
                graphics2D.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                graphics2D.drawImage(image, x, y, logicalSize, logicalSize, null)
            } finally {
                graphics2D.dispose()
            }
        }
    }
}
