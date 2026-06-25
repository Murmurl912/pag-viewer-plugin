package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.FakePagNativeLibrary
import com.github.pagviewer.nativebridge.PagPreviewSession
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PagFrameSourceTest {
    private fun fakeLibrary() = FakePagNativeLibrary(
        2, 1, 3, 12.0f,
        byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
    )

    private fun openSession(library: FakePagNativeLibrary) =
        PagPreviewSession.open(library, "PAG".toByteArray(), Path.of("/tmp/a.pag"), 60.0f, 1.0f)

    @Test
    fun decodesRequestedFrame() {
        val source = PagFrameSource(openSession(fakeLibrary()))
        try {
            val image = runBlocking { source.frame(0) }
            assertEquals(2, image.width)
            assertEquals(1, image.height)
        } finally {
            source.close()
        }
    }

    @Test
    fun prefetchWarmsCacheSoLaterReadDoesNotDecodeAgain() {
        val library = fakeLibrary()
        val source = PagFrameSource(openSession(library))
        try {
            runBlocking { source.prefetch(0, 1) }
            runBlocking { source.frame(1) }
            assertEquals(1, library.readFrameCalls())
        } finally {
            source.close()
        }
    }
}
