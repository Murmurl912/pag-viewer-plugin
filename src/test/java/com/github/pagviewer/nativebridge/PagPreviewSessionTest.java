package com.github.pagviewer.nativebridge;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PagPreviewSessionTest {
    private static final String FRAME_CACHE_BUDGET_PROPERTY = "pag.viewer.frame.cache.bytes";

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

    @Test
    void reusesCachedFrameWithoutRepeatedNativeDecode() throws Exception {
        FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                2,
                1,
                2,
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
            BufferedImage firstRead = session.readFrame(0);
            BufferedImage secondRead = session.readFrame(0);

            assertSame(firstRead, secondRead);
            assertEquals(1, nativeLibrary.readFrameCalls());
        }
    }

    @Test
    void skipsNativeDecodeWhenFrameDidNotChange() throws Exception {
        FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                2,
                1,
                2,
                12.0f,
                new byte[]{
                        0x33, 0x22, 0x11, 0x44,
                        0x77, 0x66, 0x55, (byte) 0x88
                },
                new boolean[]{true, false}
        );

        try (PagPreviewSession session = PagPreviewSession.open(
                nativeLibrary,
                new byte[]{'P', 'A', 'G'},
                Path.of("/tmp/example.pag"),
                60.0f,
                1.0f
        )) {
            BufferedImage firstFrame = session.readFrame(0);
            BufferedImage unchangedFrame = session.readFrame(1);

            assertSame(firstFrame, unchangedFrame);
            assertEquals(1, nativeLibrary.readFrameCalls());
        }
    }

    @Test
    void preloadsCacheableFrameForLaterPlaybackRead() throws Exception {
        FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                2,
                1,
                3,
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
            assertEquals(PagPreviewSession.PreloadResult.DECODED, session.preloadFrame(1));

            session.readFrame(1);

            assertEquals(1, nativeLibrary.readFrameCalls());
        }
    }

    @Test
    void doesNotPreloadWhenFrameCacheIsDisabled() throws Exception {
        String previousBudget = System.getProperty(FRAME_CACHE_BUDGET_PROPERTY);
        System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, "0");
        try {
            FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                    2,
                    1,
                    3,
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
                assertEquals(PagPreviewSession.PreloadResult.UNAVAILABLE, session.preloadFrame(1));

                session.readFrame(1);

                assertEquals(1, nativeLibrary.readFrameCalls());
            }
        } finally {
            restoreFrameCacheBudget(previousBudget);
        }
    }

    @Test
    void streamsUncachedFramesThroughReusableImageRing() throws Exception {
        String previousBudget = System.getProperty(FRAME_CACHE_BUDGET_PROPERTY);
        System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, "0");
        try {
            FakePagNativeLibrary nativeLibrary = new FakePagNativeLibrary(
                    2,
                    1,
                    4,
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
                BufferedImage firstFrame = session.readFrame(0);
                session.readFrame(1);
                session.readFrame(2);
                BufferedImage fourthFrame = session.readFrame(3);

                assertSame(firstFrame, fourthFrame);
                assertEquals(4, nativeLibrary.readFrameCalls());
            }
        } finally {
            restoreFrameCacheBudget(previousBudget);
        }
    }

    private static void restoreFrameCacheBudget(String previousBudget) {
        if (previousBudget == null) {
            System.clearProperty(FRAME_CACHE_BUDGET_PROPERTY);
        } else {
            System.setProperty(FRAME_CACHE_BUDGET_PROPERTY, previousBudget);
        }
    }
}
