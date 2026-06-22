package com.github.pagviewer.nativebridge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PagPreviewSessionTest {
    @Test
    void readsBgraPremultipliedPixelsIntoArgbImage() throws Exception {
        FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                2,
                1,
                1,
                12.0f,
                new byte[]{
                        0x33, 0x22, 0x11, 0x44,
                        0x77, 0x66, 0x55, (byte) 0x88
                }
        );

        try (PagPreviewSession session = PagPreviewSession.open(
                nativeLibrary,
                new byte[]{'P', 'A', 'G'},
                Path.of("/tmp/example.pag"),
                60.0f,
                1.0f
        )) {
            BufferedImage image = session.readFrame(0);

            assertEquals(2, image.getWidth());
            assertEquals(1, image.getHeight());
            assertEquals(0x44112233, image.getRGB(0, 0));
            assertEquals(0x88556677, image.getRGB(1, 0));
        }
    }
}

