package com.github.pagviewer.nativebridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PagPreviewSessionTest {
    @Test
    fun readsBgraPremultipliedPixelsIntoArgbImage() {
        val nativeLibrary = FakePagNativeLibrary(
            2,
            1,
            1,
            12.0f,
            byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
        )

        PagPreviewSession.open(
            nativeLibrary,
            "PAG".toByteArray(),
            Path.of("/tmp/example.pag"),
            60.0f,
            1.0f
        ).use { session ->
            val image = session.readFrame(0)

            assertEquals(2, image.width)
            assertEquals(1, image.height)
            assertEquals(0x44112233, image.getRGB(0, 0))
            assertEquals(0x88556677.toInt(), image.getRGB(1, 0))
        }
    }

    @Test
    fun reusesCachedFrameWithoutRepeatedNativeDecode() {
        val nativeLibrary = FakePagNativeLibrary(
            2,
            1,
            2,
            12.0f,
            byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
        )

        PagPreviewSession.open(
            nativeLibrary,
            "PAG".toByteArray(),
            Path.of("/tmp/example.pag"),
            60.0f,
            1.0f
        ).use { session ->
            val firstRead = session.readFrame(0)
            val secondRead = session.readFrame(0)

            assertSame(firstRead, secondRead)
            assertEquals(1, nativeLibrary.readFrameCalls())
        }
    }

    @Test
    fun skipsNativeDecodeWhenFrameDidNotChange() {
        val nativeLibrary = FakePagNativeLibrary(
            2,
            1,
            2,
            12.0f,
            byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte()),
            booleanArrayOf(true, false)
        )

        PagPreviewSession.open(
            nativeLibrary,
            "PAG".toByteArray(),
            Path.of("/tmp/example.pag"),
            60.0f,
            1.0f
        ).use { session ->
            val firstFrame = session.readFrame(0)
            val unchangedFrame = session.readFrame(1)

            assertSame(firstFrame, unchangedFrame)
            assertEquals(1, nativeLibrary.readFrameCalls())
        }
    }

    @Test
    fun preloadsCacheableFrameForLaterPlaybackRead() {
        val nativeLibrary = FakePagNativeLibrary(
            2,
            1,
            3,
            12.0f,
            byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
        )

        PagPreviewSession.open(
            nativeLibrary,
            "PAG".toByteArray(),
            Path.of("/tmp/example.pag"),
            60.0f,
            1.0f
        ).use { session ->
            assertEquals(PagPreviewSession.PreloadResult.DECODED, session.preloadFrame(1))

            session.readFrame(1)

            assertEquals(1, nativeLibrary.readFrameCalls())
        }
    }

    @Test
    fun doesNotPreloadWhenFrameCacheIsDisabled() {
        val previousBudget = System.getProperty(FRAME_CACHE_BUDGET_PROPERTY)
        System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, "0")
        try {
            val nativeLibrary = FakePagNativeLibrary(
                2,
                1,
                3,
                12.0f,
                byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
            )

            PagPreviewSession.open(
                nativeLibrary,
                "PAG".toByteArray(),
                Path.of("/tmp/example.pag"),
                60.0f,
                1.0f
            ).use { session ->
                assertEquals(PagPreviewSession.PreloadResult.UNAVAILABLE, session.preloadFrame(1))

                session.readFrame(1)

                assertEquals(1, nativeLibrary.readFrameCalls())
            }
        } finally {
            restoreFrameCacheBudget(previousBudget)
        }
    }

    @Test
    fun streamsUncachedFramesThroughReusableImageRing() {
        val previousBudget = System.getProperty(FRAME_CACHE_BUDGET_PROPERTY)
        System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, "0")
        try {
            val nativeLibrary = FakePagNativeLibrary(
                2,
                1,
                4,
                12.0f,
                byteArrayOf(0x33, 0x22, 0x11, 0x44, 0x77, 0x66, 0x55, 0x88.toByte())
            )

            PagPreviewSession.open(
                nativeLibrary,
                "PAG".toByteArray(),
                Path.of("/tmp/example.pag"),
                60.0f,
                1.0f
            ).use { session ->
                val firstFrame = session.readFrame(0)
                session.readFrame(1)
                session.readFrame(2)
                val fourthFrame = session.readFrame(3)

                assertSame(firstFrame, fourthFrame)
                assertEquals(4, nativeLibrary.readFrameCalls())
            }
        } finally {
            restoreFrameCacheBudget(previousBudget)
        }
    }

    private fun restoreFrameCacheBudget(previousBudget: String?) {
        if (previousBudget == null) {
            System.clearProperty(FRAME_CACHE_BUDGET_PROPERTY)
        } else {
            System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, previousBudget)
        }
    }

    companion object {
        private const val FRAME_CACHE_BUDGET_PROPERTY = "pag.viewer.frame.cache.bytes"
    }
}
