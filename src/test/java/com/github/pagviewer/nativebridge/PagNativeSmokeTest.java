package com.github.pagviewer.nativebridge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class PagNativeSmokeTest {
    @Test
    void packagedNativeLibraryDecodesSamplePagFrame() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag");
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        Path nativeLibraryPath = new PagNativeLibraryResolver().resolve().orElseThrow();
        JnaPagNativeLibrary nativeLibrary = JnaPagNativeLibrary.load(nativeLibraryPath);

        try (PagPreviewSession session = PagPreviewSession.open(
                nativeLibrary,
                Files.readAllBytes(sample),
                sample,
                60.0f,
                1.0f
        )) {
            BufferedImage image = session.readFrame(0);

            assertTrue(session.info().width() > 0);
            assertTrue(session.info().height() > 0);
            assertTrue(session.info().frameCount() > 0);
            assertTrue(image.getWidth() > 0);
            assertTrue(image.getHeight() > 0);
        }
    }
}

