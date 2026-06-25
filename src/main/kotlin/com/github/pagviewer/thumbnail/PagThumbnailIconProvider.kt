package com.github.pagviewer.thumbnail

import com.github.pagviewer.file.PagFileType
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IconDeferrer
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.JBImageIcon
import java.awt.image.BufferedImage
import javax.swing.Icon

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
        val logical = JBUIScale.scale(BASE_ICON_PX)
        val devicePx = maxOf(1, Math.ceil(logical * JBUIScale.sysScale().toDouble()).toInt())
        val image: BufferedImage = PagThumbnailGenerator.generate(nativeLibrary, bytes, path, devicePx) ?: return null
        val hiDpi = JBHiDPIScaledImage(image, logical, logical, BufferedImage.TYPE_INT_ARGB)
        return JBImageIcon(hiDpi)
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
    }
}
