package com.github.pagviewer.thumbnail

import com.github.pagviewer.nativebridge.FakePagNativeLibrary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PagThumbnailGeneratorTest {
    @Test
    fun generatesRequestedSizeImageFromFirstFrame() {
        val nativeLibrary = FakePagNativeLibrary(
            2,
            1,
            1,
            12.0f,
            byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
        )

        val image = PagThumbnailGenerator.generate(nativeLibrary, "PAG".toByteArray(), Path.of("/tmp/a.pag"), 16)

        assertNotNull(image)
        assertEquals(16, image!!.width)
        assertEquals(16, image.height)
    }

    @Test
    fun returnsNullForEmptyBytes() {
        val nativeLibrary = FakePagNativeLibrary(2, 1, 1, 12.0f, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))

        assertNull(PagThumbnailGenerator.generate(nativeLibrary, ByteArray(0), Path.of("/tmp/a.pag"), 16))
    }
}
