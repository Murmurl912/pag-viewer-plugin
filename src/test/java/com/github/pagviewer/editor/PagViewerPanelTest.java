package com.github.pagviewer.editor;

import com.github.pagviewer.nativebridge.PagNativeLibraryResolver;
import com.github.pagviewer.nativebridge.PagPreviewInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class PagViewerPanelTest {
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

    private static void waitForReadyPreview(PagViewerPanel panel) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) {
            AtomicReference<String> status = new AtomicReference<>();
            AtomicReference<String> metadata = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                status.set(findLabelText(panel, "Ready"));
                metadata.set(findLabelContaining(panel, "72 frames | 24.00 fps"));
            });
            if (status.get() != null && metadata.get() != null) {
                return;
            }
            Thread.sleep(50);
        }
        fail("PAG preview panel did not reach a ready decoded state.");
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
