package com.github.pagviewer.thumbnail

import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

object PagThumbnailCache {
    private const val MAX_ENTRIES = 256

    /** Sentinel stored when a file has no usable thumbnail, so it is not retried on every repaint. */
    val noThumbnail: Icon = object : Icon {
        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}
        override fun getIconWidth(): Int = 0
        override fun getIconHeight(): Int = 0
    }

    private val cache = object : LinkedHashMap<String, Icon>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Icon>): Boolean = size > MAX_ENTRIES
    }

    fun cacheKey(url: String, timeStamp: Long, length: Long): String = "$url@$timeStamp:$length"

    fun cacheKey(file: VirtualFile): String = cacheKey(file.url, file.timeStamp, file.length)

    @Synchronized
    fun get(key: String): Icon? = cache[key]

    @Synchronized
    fun put(key: String, icon: Icon?) {
        cache[key] = icon ?: noThumbnail
    }

    @Synchronized
    fun size(): Int = cache.size

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
