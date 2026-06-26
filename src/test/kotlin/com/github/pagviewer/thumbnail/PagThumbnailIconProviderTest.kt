package com.github.pagviewer.thumbnail

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PagThumbnailIconProviderTest {
    // LightVirtualFile.getLength() lazily reads content (needs a running Application), so
    // override it to expose a plain file size for the pure guard under test.
    private fun fileOfLength(name: String, length: Long): VirtualFile =
        object : LightVirtualFile(name) {
            override fun getLength(): Long = length
        }

    @Test
    fun acceptsPagFilesWhenEnabled() {
        assertTrue(PagThumbnailIconProvider.shouldProvideThumbnail(fileOfLength("a.pag", 4), true))
        assertTrue(PagThumbnailIconProvider.shouldProvideThumbnail(fileOfLength("A.PAG", 4), true))
    }

    @Test
    fun rejectsWhenDisabled() {
        assertFalse(PagThumbnailIconProvider.shouldProvideThumbnail(fileOfLength("a.pag", 4), false))
    }

    @Test
    fun rejectsNonPagExtension() {
        assertFalse(PagThumbnailIconProvider.shouldProvideThumbnail(fileOfLength("a.json", 4), true))
    }

    @Test
    fun rejectsEmptyFile() {
        assertFalse(PagThumbnailIconProvider.shouldProvideThumbnail(fileOfLength("a.pag", 0), true))
    }

    @Test
    fun thumbnailDecodeSizeTracksDeviceScaleWithoutShrinkingBelowLogicalSize() {
        assertEquals(16, PagThumbnailIconProvider.thumbnailDeviceSize(16, 1.0))
        assertEquals(32, PagThumbnailIconProvider.thumbnailDeviceSize(16, 2.0))
        assertEquals(16, PagThumbnailIconProvider.thumbnailDeviceSize(16, 0.75))
    }
}
