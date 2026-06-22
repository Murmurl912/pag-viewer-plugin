package com.github.pagviewer.editor;

import com.github.pagviewer.nativebridge.JnaPagNativeLibrary;
import com.github.pagviewer.nativebridge.PagFrameClock;
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver;
import com.github.pagviewer.nativebridge.PagPreviewInfo;
import com.github.pagviewer.nativebridge.PagPreviewSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class PagViewerPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(PagViewerPanel.class);

    private final VirtualFile file;
    private final LoadReporter loadReporter;
    private final PagCanvas canvas = new PagCanvas();
    private final JButton playPauseButton = new JButton(AllIcons.Actions.Execute);
    private final JSlider frameSlider = new JSlider(0, 0, 0);
    private final JLabel statusLabel = new JBLabel("Loading PAG preview...");
    private final JLabel metadataLabel = new JBLabel("");
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PAG Viewer Frame Decoder");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean rendering = new AtomicBoolean();

    private Timer playbackTimer;
    private PagPreviewSession session;
    private int currentFrame;
    private boolean playing;

    PagViewerPanel(VirtualFile file) {
        this(file, LoadReporter.LOGGING);
    }

    PagViewerPanel(VirtualFile file, LoadReporter loadReporter) {
        super(new BorderLayout());
        this.file = file;
        this.loadReporter = loadReporter;
        setBorder(JBUI.Borders.empty(8));
        add(toolbar(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        configureActions();
        loadAsync();
    }

    JComponent preferredFocusedComponent() {
        return playPauseButton;
    }

    void dispose() {
        LOG.info("PAG preview disposed: " + file.getPath() + ", frame=" + currentFrame);
        stopPlayback();
        renderExecutor.shutdownNow();
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private JComponent toolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        playPauseButton.setEnabled(false);
        playPauseButton.setToolTipText("Play");
        playPauseButton.setFocusable(true);
        toolbar.add(playPauseButton);

        frameSlider.setEnabled(false);
        frameSlider.setPaintTicks(false);
        frameSlider.setToolTipText("Frame");
        toolbar.add(frameSlider);

        JPanel metadataPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        metadataPanel.setOpaque(false);
        metadataPanel.add(metadataLabel);
        toolbar.add(metadataPanel);
        return toolbar;
    }

    private JComponent footer() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        footer.add(statusLabel, BorderLayout.WEST);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        return footer;
    }

    private void configureActions() {
        playPauseButton.addActionListener(event -> togglePlayback());
        frameSlider.addChangeListener(event -> {
            if (frameSlider.getValueIsAdjusting() || !playing) {
                renderFrame(frameSlider.getValue(), frameSlider.getValueIsAdjusting() ? "slider-adjusting" : "slider");
            }
        });
    }

    private void loadAsync() {
        LOG.info("PAG preview load queued: " + file.getPath() + ", virtualLength=" + file.getLength());
        new SwingWorker<LoadedPreview, Void>() {
            @Override
            protected LoadedPreview doInBackground() throws Exception {
                LOG.info("PAG preview load started: " + file.getPath());
                Optional<Path> nativePath = new PagNativeLibraryResolver().resolve();
                if (nativePath.isEmpty()) {
                    throw new IllegalStateException("Set -Dpag.viewer.libpag.path or PAG_VIEWER_LIBPAG_PATH to a libpag dynamic library.");
                }
                LOG.info("PAG native library resolved: " + nativePath.get() + ", file=" + file.getPath());

                JnaPagNativeLibrary nativeLibrary = JnaPagNativeLibrary.load(nativePath.get());
                Path path = file.toNioPath();
                if (path == null) {
                    throw new IllegalStateException("This PAG file is not backed by a local path.");
                }
                byte[] bytes = file.contentsToByteArray();
                LOG.info("PAG file bytes read: " + file.getPath() + ", bytes=" + bytes.length);
                PagPreviewSession previewSession = PagPreviewSession.open(
                        nativeLibrary,
                        bytes,
                        path,
                        60.0f,
                        1.0f
                );
                BufferedImage firstFrame = previewSession.readFrame(0);
                LOG.info("PAG first frame decoded: " + file.getPath() + ", frame=0");
                return new LoadedPreview(previewSession, firstFrame);
            }

            @Override
            protected void done() {
                try {
                    LoadedPreview preview = get();
                    session = preview.session();
                    PagPreviewInfo info = session.info();
                    frameSlider.setMaximum(Math.max(0, info.frameCount() - 1));
                    frameSlider.setValue(0);
                    frameSlider.setEnabled(true);
                    playPauseButton.setEnabled(true);
                    canvas.setImage(preview.firstFrame());
                    metadataLabel.setText(metadataText(info));
                    statusLabel.setText("Ready");
                    loadReporter.previewReady(file, info);
                } catch (Exception exception) {
                    Exception failure = exception.getCause() instanceof Exception cause ? cause : exception;
                    statusLabel.setText(failure.getMessage());
                    playPauseButton.setEnabled(false);
                    frameSlider.setEnabled(false);
                    loadReporter.previewFailed(file, failure);
                }
            }
        }.execute();
    }

    private void togglePlayback() {
        if (playing) {
            stopPlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (session == null || playing) {
            return;
        }
        playing = true;
        LOG.info("PAG playback started: " + file.getPath() + ", frame=" + currentFrame
                + ", fps=" + String.format("%.2f", session.info().frameRate()));
        playPauseButton.setIcon(AllIcons.Actions.Pause);
        playPauseButton.setToolTipText("Pause");
        playbackTimer = new Timer(PagFrameClock.delayMillis(session.info().frameRate()), event -> {
            currentFrame = PagFrameClock.nextFrame(currentFrame, session.info().frameCount());
            frameSlider.setValue(currentFrame);
            renderFrame(currentFrame, "playback");
        });
        playbackTimer.start();
    }

    private void stopPlayback() {
        boolean wasPlaying = playing || playbackTimer != null;
        playing = false;
        playPauseButton.setIcon(AllIcons.Actions.Execute);
        playPauseButton.setToolTipText("Play");
        if (playbackTimer != null) {
            playbackTimer.stop();
            playbackTimer = null;
        }
        if (wasPlaying) {
            LOG.info("PAG playback stopped: " + file.getPath() + ", frame=" + currentFrame);
        }
    }

    private void renderFrame(int frameIndex, String reason) {
        if (session == null) {
            LOG.info("PAG render ignored before session ready: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
            return;
        }
        boolean playbackRender = "playback".equals(reason);
        if (!rendering.compareAndSet(false, true)) {
            if (!playbackRender) {
                LOG.info("PAG render skipped while busy: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
            }
            return;
        }
        currentFrame = frameIndex;
        if (!playbackRender) {
            LOG.info("PAG render requested: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex
                    + "/" + Math.max(0, session.info().frameCount() - 1));
        }
        renderExecutor.submit(() -> {
            try {
                BufferedImage image = session.readFrame(frameIndex);
                SwingUtilities.invokeLater(() -> {
                    canvas.setImage(image);
                    if (!playbackRender) {
                        LOG.info("PAG render completed: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
                    }
                });
            } catch (Exception exception) {
                LOG.warn("PAG render failed: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex, exception);
                SwingUtilities.invokeLater(() -> statusLabel.setText(exception.getMessage()));
            } finally {
                rendering.set(false);
            }
        });
    }

    private static String metadataText(PagPreviewInfo info) {
        return sizeText(info) + " | " + info.frameCount() + " frames | "
                + String.format("%.2f fps", info.frameRate());
    }

    private static String sizeText(PagPreviewInfo info) {
        String decoderSize = info.width() + " x " + info.height();
        if (!info.hasScaledDecoderSize()) {
            return decoderSize;
        }
        return decoderSize + " decode | " + info.compositionWidth() + " x " + info.compositionHeight() + " comp";
    }

    private record LoadedPreview(PagPreviewSession session, BufferedImage firstFrame) {
    }

    interface LoadReporter {
        LoadReporter LOGGING = new LoadReporter() {
            @Override
            public void previewReady(VirtualFile file, PagPreviewInfo info) {
                LOG.info("PAG preview ready: " + file.getPath() + ", decoder=" + info.width() + "x" + info.height()
                        + ", composition=" + info.compositionWidth() + "x" + info.compositionHeight()
                        + ", frames=" + info.frameCount() + ", fps=" + String.format("%.2f", info.frameRate()));
                if (info.hasScaledDecoderSize()) {
                    LOG.info("PAG decoder size differs from composition size: " + file.getPath()
                            + ", decoder=" + info.width() + "x" + info.height()
                            + ", composition=" + info.compositionWidth() + "x" + info.compositionHeight());
                }
            }

            @Override
            public void previewFailed(VirtualFile file, Exception exception) {
                LOG.warn("PAG preview failed: " + file.getPath(), exception);
            }
        };

        void previewReady(VirtualFile file, PagPreviewInfo info);

        void previewFailed(VirtualFile file, Exception exception);
    }
}
