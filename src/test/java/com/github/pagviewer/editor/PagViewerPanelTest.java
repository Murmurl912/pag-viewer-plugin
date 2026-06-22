package com.github.pagviewer.editor;

import com.github.pagviewer.nativebridge.PagFrameClock;
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver;
import com.github.pagviewer.nativebridge.PagPreviewInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JViewport;
import javax.swing.JSlider;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class PagViewerPanelTest {
    private static final String DECODE_AHEAD_FRAMES_PROPERTY = "pag.viewer.decodeAhead.frames";

    @Test
    void exposesImageViewerChromeAndPlaybackControlsAtBottom() throws Exception {
        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(
                new LightVirtualFile("empty.pag"),
                new NoopLoadReporter()
        )));

        PagViewerPanel panel = panelRef.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                AbstractButton chessboardButton = findButtonWithTooltip(panel, "Show chessboard");
                AbstractButton gridButton = findButtonWithTooltip(panel, "Show grid");
                assertNotNull(chessboardButton);
                assertNotNull(gridButton);
                assertToggleHasVisibleSelectedState(chessboardButton);
                assertToggleHasVisibleSelectedState(gridButton);
                assertToolbarButtonUsesIconStyle(chessboardButton);
                assertToolbarButtonUsesIconStyle(gridButton);
                chessboardButton.doClick();
                gridButton.doClick();
                assertTrue(chessboardButton.isSelected());
                assertTrue(gridButton.isSelected());
                assertEquals("Hide chessboard", chessboardButton.getToolTipText());
                assertEquals("Hide grid", gridButton.getToolTipText());
                assertNotNull(findButtonWithTooltip(panel, "Zoom in"));
                assertNotNull(findButtonWithTooltip(panel, "Zoom out"));
                assertNotNull(findButtonWithTooltip(panel, "Fit zoom"));
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Zoom in"));
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Zoom out"));
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Fit zoom"));
                assertToolbarIsTight(panel);
                assertNotNull(findFirst(panel, JScrollPane.class));
                JComboBox<?> speedBox = findFirst(panel, JComboBox.class);
                assertNotNull(speedBox);
                assertTrue(hasNamedAncestor(speedBox, "pag-playback-bar"));
                assertComboContains(speedBox, "0.25x", "1x", "2x");

                AbstractButton playButton = findButtonWithTooltip(panel, "Play");
                assertNotNull(playButton);
                assertTrue(hasNamedAncestor(playButton, "pag-playback-bar"));
                assertFalse(hasNamedAncestor(playButton, "pag-viewer-toolbar"));
                assertToolbarButtonUsesIconStyle(playButton);
                assertEquals(1, countTransportButtons(panel));

                JSlider slider = findFirst(panel, JSlider.class);
                assertNotNull(slider);
                assertTrue(slider.getUI() instanceof PagViewerPanel.RoundThumbSliderUI);
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    void canvasSupportsCheckerboardGridAndZoomState() throws Exception {
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB));
            canvasRef.set(canvas);
        });

        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = canvasRef.get();
            assertTrue(canvas.isFitZoom());
            assertFalse(canvas.isCheckerboardVisible());
            assertFalse(canvas.isGridVisible());

            canvas.setCheckerboardVisible(true);
            canvas.setGridVisible(true);
            assertTrue(canvas.isCheckerboardVisible());
            assertTrue(canvas.isGridVisible());

            double originalZoom = canvas.zoomScale();
            canvas.zoomIn();
            assertFalse(canvas.isFitZoom());
            assertTrue(canvas.zoomScale() > originalZoom);

            canvas.zoomOut();
            canvas.setActualSize();
            assertFalse(canvas.isFitZoom());
            assertEquals(1.0d, canvas.zoomScale(), 0.001d);

            canvas.setFitZoom();
            assertTrue(canvas.isFitZoom());
        });
    }

    @Test
    void fitZoomDoesNotUpscaleSmallDecodedFrames() throws Exception {
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics2D imageGraphics = image.createGraphics();
            try {
                imageGraphics.setColor(Color.MAGENTA);
                imageGraphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            } finally {
                imageGraphics.dispose();
            }
            canvas.setImage(image);
            canvas.setFitZoom();
            canvas.setSize(240, 240);
            canvasRef.set(canvas);
        });

        AtomicReference<Rectangle> paintedBounds = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage target = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            try {
                canvasRef.get().paint(graphics);
            } finally {
                graphics.dispose();
            }
            paintedBounds.set(boundsOfColor(target, Color.MAGENTA.getRGB()));
        });

        assertEquals(new Rectangle(108, 108, 24, 24), paintedBounds.get());
    }

    @Test
    void toolbarZoomKeepsViewportCenterAsFocalPoint() throws Exception {
        AtomicReference<JViewport> viewportRef = new AtomicReference<>();
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB));
            canvas.setActualSize();
            JScrollPane scrollPane = new JScrollPane(canvas);
            scrollPane.setSize(200, 200);
            scrollPane.doLayout();
            canvas.setSize(canvas.getPreferredSize());
            JViewport viewport = scrollPane.getViewport();
            viewport.setExtentSize(new Dimension(200, 200));
            viewport.setViewSize(canvas.getPreferredSize());
            viewport.setViewPosition(new Point(300, 400));

            canvas.zoomIn(viewport);

            viewportRef.set(viewport);
            canvasRef.set(canvas);
        });

        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1.25d, canvasRef.get().zoomScale(), 0.001d);
            assertEquals(new Point(400, 525), viewportRef.get().getViewPosition());
        });
    }

    @Test
    void manualZoomStillTracksViewportWhenImageIsSmallerThanViewport() throws Exception {
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        AtomicReference<JViewport> viewportRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(202, 202, BufferedImage.TYPE_INT_ARGB));
            JScrollPane scrollPane = new JScrollPane(canvas);
            scrollPane.setSize(900, 700);
            scrollPane.doLayout();
            JViewport viewport = scrollPane.getViewport();
            viewport.setExtentSize(new Dimension(900, 700));
            viewport.setViewSize(new Dimension(900, 700));

            canvas.zoomIn(viewport);

            canvasRef.set(canvas);
            viewportRef.set(viewport);
        });

        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = canvasRef.get();
            JViewport viewport = viewportRef.get();
            assertFalse(canvas.isFitZoom());
            assertTrue(canvas.getScrollableTracksViewportWidth());
            assertTrue(canvas.getScrollableTracksViewportHeight());
            Rectangle imageBounds = canvas.imageBoundsForTests(viewport.getExtentSize());
            assertEquals(new Rectangle(323, 223, 253, 253), imageBounds);
        });
    }

    @Test
    void zoomWheelGestureKeepsMousePositionAsFocalPoint() throws Exception {
        AtomicReference<JViewport> viewportRef = new AtomicReference<>();
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB));
            canvas.setActualSize();
            JScrollPane scrollPane = new JScrollPane(canvas);
            scrollPane.setSize(200, 200);
            scrollPane.doLayout();
            canvas.setSize(canvas.getPreferredSize());
            JViewport viewport = scrollPane.getViewport();
            viewport.setExtentSize(new Dimension(200, 200));
            viewport.setViewSize(canvas.getPreferredSize());
            viewport.setViewPosition(new Point(300, 400));

            MouseWheelEvent zoomEvent = new MouseWheelEvent(
                    canvas,
                    MouseWheelEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    InputEvent.CTRL_DOWN_MASK,
                    350,
                    450,
                    350,
                    450,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    1,
                    -1,
                    -1.0d
            );
            canvas.dispatchEvent(zoomEvent);

            viewportRef.set(viewport);
            canvasRef.set(canvas);
        });

        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1.25d, canvasRef.get().zoomScale(), 0.001d);
            assertEquals(new Point(388, 513), viewportRef.get().getViewPosition());
        });
    }

    @Test
    void checkerboardHasVisibleContrastOnDarkBackground() throws Exception {
        AtomicReference<BufferedImage> targetRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setBackground(new Color(0x1E1F22));
            canvas.setImage(new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB));
            canvas.setCheckerboardVisible(true);
            canvas.setFitZoom();
            canvas.setSize(48, 48);

            BufferedImage target = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            try {
                canvas.paint(graphics);
            } finally {
                graphics.dispose();
            }
            targetRef.set(target);
        });

        Rectangle bounds = new Rectangle(0, 0, 48, 48);
        assertTrue(luminanceRange(targetRef.get(), bounds) >= 24);
    }

    @Test
    void playbackFramesScheduleDeferredHighQualityPaint() throws Exception {
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), true);
            canvasRef.set(canvas);
        });

        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = canvasRef.get();
            assertFalse(canvas.isHighQualityRenderingRequestedForTests());
            assertTrue(canvas.isHighQualityRepaintScheduledForTests());

            canvas.setImage(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), false);
            assertTrue(canvas.isHighQualityRenderingRequestedForTests());
            assertFalse(canvas.isHighQualityRepaintScheduledForTests());
        });
    }

    @Test
    void disposingCanvasStopsDeferredHighQualityPaintTimer() throws Exception {
        AtomicReference<PagCanvas> canvasRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = new PagCanvas();
            canvas.setImage(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), true);
            canvasRef.set(canvas);
        });

        SwingUtilities.invokeAndWait(() -> {
            PagCanvas canvas = canvasRef.get();
            assertTrue(canvas.isHighQualityRepaintScheduledForTests());

            canvas.dispose();

            assertFalse(canvas.isHighQualityRepaintScheduledForTests());
        });
    }

    @Test
    void decodeAheadFrameCountCanBeDisabledForProfiling() throws Exception {
        String previousValue = System.getProperty(DECODE_AHEAD_FRAMES_PROPERTY);
        System.setProperty(DECODE_AHEAD_FRAMES_PROPERTY, "0");
        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(
                    new LightVirtualFile("empty.pag"),
                    new NoopLoadReporter()
            )));

            SwingUtilities.invokeAndWait(() -> assertEquals(0, panelRef.get().decodeAheadFrameCountForTests()));
        } finally {
            if (panelRef.get() != null) {
                SwingUtilities.invokeAndWait(panelRef.get()::dispose);
            }
            restoreSystemProperty(DECODE_AHEAD_FRAMES_PROPERTY, previousValue);
        }
    }

    @Test
    void loadsRealPagFileIntoReadyPreviewState() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath();
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        VirtualFile virtualFile = new PathBackedLightVirtualFile(sample);
        AtomicReference<VirtualFile> readyFile = new AtomicReference<>();
        AtomicReference<PagPreviewInfo> readyInfo = new AtomicReference<>();

        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(
                virtualFile,
                new PagViewerPanel.LoadReporter() {
                    @Override
                    public void previewReady(VirtualFile file, PagPreviewInfo info) {
                        readyFile.set(file);
                        readyInfo.set(info);
                    }

                    @Override
                    public void previewFailed(VirtualFile file, Exception exception) {
                        fail("PAG preview should decode the sample file.");
                    }
                }
        )));

        PagViewerPanel panel = panelRef.get();
        try {
            waitForReadyPreview(panel);
            PagPreviewInfo info = readyInfo.get();
            if (info == null || readyFile.get() != virtualFile || info.width() != 202 || info.height() != 202 || info.frameCount() != 72) {
                fail("PAG preview did not report ready metadata for the decoded sample.");
            }
            SwingUtilities.invokeAndWait(() -> assertNull(findLabelText(panel, "Ready")));
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    void scrubbingRealPagFileRendersDifferentFramePixels() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath();
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        VirtualFile virtualFile = new PathBackedLightVirtualFile(sample);

        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(virtualFile, new FailingLoadReporter())));

        PagViewerPanel panel = panelRef.get();
        try {
            waitForReadyPreview(panel);
            long firstDigest = waitForCanvasDigest(panel);

            SwingUtilities.invokeAndWait(() -> findFirst(panel, JSlider.class).setValue(12));

            waitForChangedCanvasDigest(panel, firstDigest);
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    void hidingPanelStopsPlayback() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath();
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        VirtualFile virtualFile = new PathBackedLightVirtualFile(sample);

        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(virtualFile, new FailingLoadReporter())));

        PagViewerPanel panel = panelRef.get();
        try {
            waitForReadyPreview(panel);
            waitForPlaying(panel);
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(panel.isPlayingForTests());
                panel.handleShowingChanged(false);
                assertFalse(panel.isPlayingForTests());
                assertNotNull(findButtonWithTooltip(panel, "Play"));
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    void readyPreviewAutoPlaysAndSpeedCanBeAdjusted() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath();
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        VirtualFile virtualFile = new PathBackedLightVirtualFile(sample);

        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(virtualFile, new FailingLoadReporter())));

        PagViewerPanel panel = panelRef.get();
        try {
            waitForReadyPreview(panel);
            waitForPlaying(panel);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, countTransportButtons(panel));
                assertNotNull(findButtonWithTooltip(panel, "Pause"));
                assertNull(findLabelContaining(panel, "Speed 1x"));
                JComboBox<?> speedBox = findFirst(panel, JComboBox.class);
                speedBox.setSelectedItem("2x");
                assertEquals(2.0d, panel.playbackSpeedForTests(), 0.001d);
                assertEquals(PagFrameClock.delayMillis(24.0f) / 2, panel.playbackDelayMillisForTests());
                assertNull(findLabelContaining(panel, "Speed 2x"));

                speedBox.setSelectedItem("0.25x");
                assertEquals(0.25d, panel.playbackSpeedForTests(), 0.001d);
                assertEquals(PagFrameClock.delayMillis(24.0f) * 4, panel.playbackDelayMillisForTests());
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    @Test
    void playbackButtonPausesWithoutRewindingCurrentFrame() throws Exception {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath();
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available");

        VirtualFile virtualFile = new PathBackedLightVirtualFile(sample);

        AtomicReference<PagViewerPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new PagViewerPanel(virtualFile, new FailingLoadReporter())));

        PagViewerPanel panel = panelRef.get();
        try {
            waitForReadyPreview(panel);
            waitForPlaying(panel);
            waitForSliderAboveZero(panel);
            SwingUtilities.invokeAndWait(() -> {
                JSlider slider = findFirst(panel, JSlider.class);
                int frameBeforePause = slider.getValue();
                findButtonWithTooltip(panel, "Pause").doClick();

                assertFalse(panel.isPlayingForTests());
                assertEquals(frameBeforePause, slider.getValue());
                assertNotNull(findButtonWithTooltip(panel, "Play"));
            });
        } finally {
            SwingUtilities.invokeAndWait(panel::dispose);
        }
    }

    private static void waitForReadyPreview(PagViewerPanel panel) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<String> status = new AtomicReference<>();
            AtomicReference<String> metadata = new AtomicReference<>();
            AtomicReference<BufferedImage> image = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                metadata.set(findLabelContaining(panel, "72 frames | 24.00 fps"));
                image.set(findFirst(panel, PagCanvas.class).currentImage());
            });
            if (metadata.get() != null && image.get() != null) {
                return;
            }
            Thread.sleep(50);
        }
        fail("PAG preview panel did not reach a ready decoded state.");
    }

    private static void waitForPlaying(PagViewerPanel panel) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> playing = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> playing.set(panel.isPlayingForTests()));
            if (Boolean.TRUE.equals(playing.get())) {
                return;
            }
            Thread.sleep(50);
        }
        fail("PAG preview did not auto-play after loading.");
    }

    private static void waitForSliderAboveZero(PagViewerPanel panel) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<Integer> frame = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> frame.set(findFirst(panel, JSlider.class).getValue()));
            if (frame.get() != null && frame.get() > 0) {
                return;
            }
            Thread.sleep(50);
        }
        fail("PAG preview did not advance beyond the first frame.");
    }

    private static String findLabelText(Component component, String expectedText) {
        if (component instanceof JLabel label && expectedText.equals(label.getText())) {
            return label.getText();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                String text = findLabelText(child, expectedText);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String findLabelContaining(Component component, String expectedText) {
        if (component instanceof JLabel label && label.getText().contains(expectedText)) {
            return label.getText();
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                String text = findLabelContaining(child, expectedText);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static long waitForCanvasDigest(PagViewerPanel panel) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<BufferedImage> image = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> image.set(findFirst(panel, PagCanvas.class).currentImage()));
            if (image.get() != null) {
                return digest(image.get());
            }
            Thread.sleep(50);
        }
        fail("PAG preview canvas did not receive a decoded frame.");
        return 0L;
    }

    private static void waitForChangedCanvasDigest(PagViewerPanel panel, long firstDigest) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<BufferedImage> image = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> image.set(findFirst(panel, PagCanvas.class).currentImage()));
            if (image.get() != null && digest(image.get()) != firstDigest) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Scrubbing the PAG preview did not render different frame pixels.");
    }

    private static long digest(BufferedImage image) {
        long digest = 1125899906842597L;
        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 7) {
                digest = 31 * digest + image.getRGB(x, y);
            }
        }
        return digest;
    }

    private static Rectangle boundsOfColor(BufferedImage image, int rgb) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == rgb) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return new Rectangle();
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static int luminanceRange(BufferedImage image, Rectangle bounds) {
        int min = 255;
        int max = 0;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                Color color = new Color(image.getRGB(x, y), true);
                int luminance = (int) Math.round(color.getRed() * 0.2126 + color.getGreen() * 0.7152 + color.getBlue() * 0.0722);
                min = Math.min(min, luminance);
                max = Math.max(max, luminance);
            }
        }
        return max - min;
    }

    private static <T extends Component> T findFirst(Component component, Class<T> type) {
        if (type.isInstance(component)) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findFirst(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static AbstractButton findButtonWithTooltip(Component component, String tooltip) {
        if (component instanceof AbstractButton button && tooltip.equals(button.getToolTipText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                AbstractButton found = findButtonWithTooltip(child, tooltip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean hasNamedAncestor(Component component, String name) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (name.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static int countTransportButtons(Component component) {
        int count = 0;
        if (component instanceof AbstractButton button && hasNamedAncestor(button, "pag-playback-bar")) {
            Object tooltip = button.getToolTipText();
            if ("Play".equals(tooltip) || "Pause".equals(tooltip) || "Stop".equals(tooltip)) {
                count++;
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                count += countTransportButtons(child);
            }
        }
        return count;
    }

    private static void assertToggleHasVisibleSelectedState(AbstractButton button) {
        assertNotNull(button.getIcon());
        assertNotNull(button.getSelectedIcon());
        assertFalse(button.getIcon() == button.getSelectedIcon());
    }

    private static void assertToolbarButtonUsesIconStyle(AbstractButton button) {
        assertFalse(button.isContentAreaFilled());
        assertFalse(button.isBorderPainted());
        assertFalse(button.isFocusable());
    }

    private static void assertToolbarIsTight(PagViewerPanel panel) {
        Component toolbarControls = findNamed(panel, "pag-viewer-toolbar-controls");
        assertNotNull(toolbarControls);
        assertTrue(toolbarControls.getPreferredSize().width <= 260);
    }

    private static Component findNamed(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findNamed(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertComboContains(JComboBox<?> comboBox, String... expectedItems) {
        for (String expectedItem : expectedItems) {
            boolean found = false;
            for (int index = 0; index < comboBox.getItemCount(); index++) {
                if (expectedItem.equals(comboBox.getItemAt(index))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Missing playback speed option: " + expectedItem);
        }
    }

    private static void restoreSystemProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static final class NoopLoadReporter implements PagViewerPanel.LoadReporter {
        @Override
        public void previewReady(VirtualFile file, PagPreviewInfo info) {
        }

        @Override
        public void previewFailed(VirtualFile file, Exception exception) {
        }
    }

    private static final class FailingLoadReporter implements PagViewerPanel.LoadReporter {
        @Override
        public void previewReady(VirtualFile file, PagPreviewInfo info) {
        }

        @Override
        public void previewFailed(VirtualFile file, Exception exception) {
            fail("PAG preview should decode the sample file.");
        }
    }

    private static final class PathBackedLightVirtualFile extends LightVirtualFile {
        private final Path path;
        private final byte[] bytes;

        private PathBackedLightVirtualFile(Path path) throws IOException {
            super(path.getFileName().toString());
            this.path = path;
            this.bytes = Files.readAllBytes(path);
        }

        @Override
        public Path toNioPath() {
            return path;
        }

        @Override
        public byte[] contentsToByteArray() {
            return bytes.clone();
        }
    }
}
