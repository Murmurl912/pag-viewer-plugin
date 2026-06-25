package com.github.pagviewer.nativebridge

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

internal class PagNativeSmokeTest {
    @Test
    fun packagedNativeLibraryDecodesSamplePagFrame() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag")
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val nativeLibraryPath = PagNativeLibraryResolver().resolve().orElseThrow()
        val nativeLibrary = JnaPagNativeLibrary.load(nativeLibraryPath)

        PagPreviewSession.open(
            nativeLibrary,
            Files.readAllBytes(sample),
            sample,
            60.0f,
            1.0f
        ).use { session ->
            val image = session.readFrame(0)

            assertTrue(session.info.width > 0)
            assertTrue(session.info.height > 0)
            assertTrue(session.info.frameCount > 0)
            assertTrue(image.width > 0)
            assertTrue(image.height > 0)
        }
    }
}
