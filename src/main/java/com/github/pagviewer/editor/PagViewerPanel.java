package com.github.pagviewer.editor;

import com.github.pagviewer.nativebridge.JnaPagNativeLibrary;
import com.github.pagviewer.nativebridge.PagFrameClock;
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver;
import com.github.pagviewer.nativebridge.PagPreviewInfo;
import com.github.pagviewer.nativebridge.PagPreviewSession;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PagViewerPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(PagViewerPanel.class);
    private static final String[] PLAYBACK_SPEEDS = {"0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
    private static final int DEFAULT_DECODE_AHEAD_FRAMES = 6;
    private static final long PERFORMANCE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long SLOW_FRAME_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final Dimension ICON_BUTTON_SIZE = JBUI.size(30, 30);

    private final VirtualFile file;
    private final LoadReporter loadReporter;
    private final PagCanvas canvas = new PagCanvas();
    private final JScrollPane canvasScrollPane = new JScrollPane(canvas);
    private final JToggleButton checkerboardButton = new JToggleButton(ToggleToolbarIcon.checkerboard(false));
    private final JToggleButton gridButton = new JToggleButton(ToggleToolbarIcon.grid(false));
    private final JButton zoomInButton = new JButton(AllIcons.General.ZoomIn);
    private final JButton zoomOutButton = new JButton(AllIcons.General.ZoomOut);
    private final JButton fitZoomButton = new JButton(AllIcons.General.FitContent);
    private final JButton actualSizeButton = new JButton(AllIcons.General.ActualZoom);
    private final JButton playPauseButton = new JButton(AllIcons.Actions.Execute);
    private final JSlider frameSlider = new JSlider(0, 0, 0);
    private final JComboBox<String> speedComboBox = new JComboBox<>(PLAYBACK_SPEEDS);
    private final JLabel statusLabel = new JBLabel("");
    private final JLabel metadataLabel = new JBLabel("");
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PAG Viewer Frame Decoder");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService decodeAheadExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PAG Viewer Decode Ahead");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean rendering = new AtomicBoolean();
    private final AtomicBoolean decodeAheadRunning = new AtomicBoolean();
    private final AtomicInteger decodeAheadGeneration = new AtomicInteger();

    private Timer playbackTimer;
    private volatile PagPreviewSession session;
    private int currentFrame;
    private boolean playing;
    private volatile boolean disposed;
    private volatile double playbackSpeed = 1.0d;
    private long performanceWindowStartedNanos;
    private int renderedPlaybackFrames;
    private int droppedPlaybackFrames;
    private long lastSlowFrameLogNanos;
    private volatile long lastDecodeAheadLogNanos;

    PagViewerPanel(VirtualFile file) {
        this(file, LoadReporter.LOGGING);
    }

    PagViewerPanel(VirtualFile file, LoadReporter loadReporter) {
        super(new BorderLayout());
        this.file = file;
        this.loadReporter = loadReporter;
        setBorder(JBUI.Borders.empty(8));
        add(toolbar(), BorderLayout.NORTH);
        add(viewer(), BorderLayout.CENTER);
        add(playbackBar(), BorderLayout.SOUTH);
        configureActions();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                handleShowingChanged(isShowing());
            }
        });
        loadAsync();
    }

    JComponent preferredFocusedComponent() {
        return playPauseButton;
    }

    void dispose() {
        disposed = true;
        LOG.info("PAG preview disposed: " + file.getPath() + ", frame=" + currentFrame);
        cancelDecodeAhead();
        stopPlayback();
        decodeAheadExecutor.shutdownNow();
        renderExecutor.shutdownNow();
        canvas.dispose();
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private JComponent toolbar() {
        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setName("pag-viewer-toolbar");
        toolbarPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0));
        toolbar.setName("pag-viewer-toolbar-controls");
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder());

        configureToggleToolbarButton(
                checkerboardButton,
                "Show chessboard",
                ToggleToolbarIcon.checkerboard(false),
                "Hide chessboard",
                ToggleToolbarIcon.checkerboard(true)
        );
        configureToggleToolbarButton(
                gridButton,
                "Show grid",
                ToggleToolbarIcon.grid(false),
                "Hide grid",
                ToggleToolbarIcon.grid(true)
        );
        configureToolbarButton(zoomInButton, "Zoom in");
        configureToolbarButton(zoomOutButton, "Zoom out");
        configureToolbarButton(actualSizeButton, "Actual size");
        configureToolbarButton(fitZoomButton, "Fit zoom");
        toolbar.add(checkerboardButton);
        toolbar.add(gridButton);
        toolbar.add(verticalSeparator());
        toolbar.add(zoomInButton);
        toolbar.add(zoomOutButton);
        toolbar.add(actualSizeButton);
        toolbar.add(fitZoomButton);

        metadataLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        toolbarPanel.add(toolbar, BorderLayout.WEST);
        toolbarPanel.add(metadataLabel, BorderLayout.EAST);
        return toolbarPanel;
    }

    private JComponent viewer() {
        canvasScrollPane.setBorder(BorderFactory.createEmptyBorder());
        canvasScrollPane.getViewport().setOpaque(false);
        return canvasScrollPane;
    }

    private JComponent playbackBar() {
        JPanel playbackBar = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        playbackBar.setName("pag-playback-bar");
        playbackBar.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel transportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        transportPanel.setOpaque(false);
        playPauseButton.setEnabled(false);
        configureToolbarButton(playPauseButton, "Play");
        transportPanel.add(playPauseButton);

        frameSlider.setEnabled(false);
        frameSlider.setPaintTicks(false);
        frameSlider.setOpaque(false);
        frameSlider.setFocusable(false);
        frameSlider.setUI(new RoundThumbSliderUI(frameSlider));
        frameSlider.setToolTipText("Frame");

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        statusPanel.setOpaque(false);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        speedComboBox.setSelectedItem("1x");
        speedComboBox.setToolTipText("Playback speed");
        statusPanel.add(statusLabel);
        statusPanel.add(speedComboBox);

        playbackBar.add(transportPanel, BorderLayout.WEST);
        playbackBar.add(frameSlider, BorderLayout.CENTER);
        playbackBar.add(statusPanel, BorderLayout.EAST);
        return playbackBar;
    }

    private void configureActions() {
        checkerboardButton.addActionListener(event -> {
            canvas.setCheckerboardVisible(checkerboardButton.isSelected());
            updateToggleToolbarTooltip(checkerboardButton, "Show chessboard", "Hide chessboard");
        });
        gridButton.addActionListener(event -> {
            canvas.setGridVisible(gridButton.isSelected());
            updateToggleToolbarTooltip(gridButton, "Show grid", "Hide grid");
        });
        zoomInButton.addActionListener(event -> canvas.zoomIn(canvasScrollPane.getViewport()));
        zoomOutButton.addActionListener(event -> canvas.zoomOut(canvasScrollPane.getViewport()));
        actualSizeButton.addActionListener(event -> canvas.setActualSize(canvasScrollPane.getViewport()));
        fitZoomButton.addActionListener(event -> canvas.setFitZoom());
        playPauseButton.addActionListener(event -> togglePlayback());
        speedComboBox.addActionListener(event -> {
            playbackSpeed = selectedPlaybackSpeed();
            if (playbackTimer != null) {
                playbackTimer.setDelay(playbackDelayMillisForTests());
                playbackTimer.setInitialDelay(playbackDelayMillisForTests());
            }
            LOG.info("PAG playback speed changed: " + file.getPath() + ", speed=" + speedComboBox.getSelectedItem());
        });
        frameSlider.addChangeListener(event -> {
            if (frameSlider.getValueIsAdjusting() || !playing) {
                renderFrame(frameSlider.getValue(), frameSlider.getValueIsAdjusting() ? "slider-adjusting" : "slider");
            }
        });
    }

    private void loadAsync() {
        LOG.info("PAG preview load queued: " + file.getPath() + ", virtualLength=" + safeLength(file));
        renderExecutor.submit(() -> {
            try {
                LoadedPreview preview = loadPreview();
                SwingUtilities.invokeLater(() -> applyLoadedPreview(preview));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> applyLoadFailure(exception));
            }
        });
    }

    private LoadedPreview loadPreview() throws Exception {
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

    private void applyLoadedPreview(LoadedPreview preview) {
        if (disposed) {
            preview.session().close();
            return;
        }
        session = preview.session();
        PagPreviewInfo info = session.info();
        frameSlider.setMaximum(Math.max(0, info.frameCount() - 1));
        frameSlider.setValue(0);
        frameSlider.setEnabled(true);
        playPauseButton.setEnabled(true);
        canvas.setImage(preview.firstFrame());
        metadataLabel.setText(metadataText(info));
        statusLabel.setText("");
        loadReporter.previewReady(file, info);
        startPlayback("auto-play");
    }

    private void applyLoadFailure(Exception failure) {
        if (disposed) {
            return;
        }
        statusLabel.setText(failure.getMessage());
        playPauseButton.setEnabled(false);
        frameSlider.setEnabled(false);
        loadReporter.previewFailed(file, failure);
    }

    private void togglePlayback() {
        if (playing) {
            stopPlayback();
        } else {
            startPlayback("manual");
        }
    }

    private void startPlayback(String reason) {
        if (session == null || playing) {
            return;
        }
        playing = true;
        resetPerformanceWindow();
        LOG.info("PAG playback started: " + file.getPath() + ", frame=" + currentFrame
                + ", fps=" + String.format("%.2f", session.info().frameRate())
                + ", speed=" + speedComboBox.getSelectedItem()
                + ", reason=" + reason);
        playPauseButton.setIcon(AllIcons.Actions.Pause);
        playPauseButton.setToolTipText("Pause");
        playbackTimer = new Timer(playbackDelayMillisForTests(), event -> advancePlaybackFrame());
        playbackTimer.setCoalesce(true);
        playbackTimer.start();
    }

    private void stopPlayback() {
        boolean wasPlaying = playing || playbackTimer != null;
        playing = false;
        cancelDecodeAhead();
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

    void handleShowingChanged(boolean showing) {
        if (!showing && playing) {
            LOG.info("PAG playback stopped because tab is hidden: " + file.getPath() + ", frame=" + currentFrame);
            stopPlayback();
        }
    }

    boolean isPlayingForTests() {
        return playing;
    }

    double playbackSpeedForTests() {
        return playbackSpeed;
    }

    int playbackDelayMillisForTests() {
        if (session == null) {
            return 0;
        }
        return Math.max(1, (int) Math.round(PagFrameClock.delayMillis(session.info().frameRate()) / playbackSpeed));
    }

    private void advancePlaybackFrame() {
        if (session == null) {
            return;
        }
        int nextFrame = PagFrameClock.nextFrame(currentFrame, session.info().frameCount());
        if (renderFrame(nextFrame, "playback")) {
            frameSlider.setValue(nextFrame);
        }
    }

    private boolean renderFrame(int frameIndex, String reason) {
        if (session == null) {
            LOG.info("PAG render ignored before session ready: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
            return false;
        }
        int renderGeneration = decodeAheadGeneration.incrementAndGet();
        boolean playbackRender = "playback".equals(reason);
        if (!rendering.compareAndSet(false, true)) {
            if (playbackRender) {
                droppedPlaybackFrames++;
            } else {
                LOG.info("PAG render skipped while busy: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
            }
            return false;
        }
        currentFrame = frameIndex;
        if (!playbackRender) {
            LOG.info("PAG render requested: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex
                    + "/" + Math.max(0, session.info().frameCount() - 1));
        }
        renderExecutor.submit(() -> {
            try {
                long startedNanos = System.nanoTime();
                BufferedImage image = session.readFrame(frameIndex);
                long decodeNanos = System.nanoTime() - startedNanos;
                maybeLogSlowFrame(frameIndex, decodeNanos, playbackRender);
                SwingUtilities.invokeLater(() -> {
                    if (disposed) {
                        return;
                    }
                    canvas.setImage(image, playbackRender);
                    updatePerformanceLabel(decodeNanos, playbackRender);
                    if (playbackRender) {
                        scheduleDecodeAhead(frameIndex, renderGeneration);
                    }
                    if (!playbackRender) {
                        LOG.info("PAG render completed: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex);
                    }
                });
            } catch (Exception exception) {
                LOG.warn("PAG render failed: " + file.getPath() + ", reason=" + reason + ", frame=" + frameIndex, exception);
                SwingUtilities.invokeLater(() -> {
                    if (!disposed) {
                        statusLabel.setText(exception.getMessage());
                    }
                });
            } finally {
                rendering.set(false);
            }
        });
        return true;
    }

    private void scheduleDecodeAhead(int frameIndex, int generation) {
        int frameCount = decodeAheadFrameCount();
        PagPreviewSession previewSession = session;
        if (disposed || previewSession == null || frameCount <= 0 || generation != decodeAheadGeneration.get()) {
            return;
        }
        if (!decodeAheadRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            decodeAheadExecutor.submit(() -> runDecodeAhead(previewSession, frameIndex, frameCount, generation));
        } catch (RejectedExecutionException exception) {
            decodeAheadRunning.set(false);
            if (!disposed) {
                LOG.warn("PAG decode-ahead rejected: " + file.getPath(), exception);
            }
        }
    }

    private void runDecodeAhead(PagPreviewSession previewSession, int frameIndex, int frameCount, int generation) {
        int decodedFrames = 0;
        int cachedFrames = 0;
        long startedNanos = System.nanoTime();
        try {
            for (int offset = 1; offset <= frameCount; offset++) {
                if (disposed || Thread.currentThread().isInterrupted() || generation != decodeAheadGeneration.get()) {
                    return;
                }
                PagPreviewSession.PreloadResult result = previewSession.preloadFrame(frameIndex + offset);
                if (result == PagPreviewSession.PreloadResult.UNAVAILABLE) {
                    return;
                }
                if (result == PagPreviewSession.PreloadResult.DECODED) {
                    decodedFrames++;
                } else if (result == PagPreviewSession.PreloadResult.ALREADY_CACHED) {
                    cachedFrames++;
                }
            }
        } catch (Exception exception) {
            if (!disposed && generation == decodeAheadGeneration.get()) {
                LOG.warn("PAG decode-ahead failed: " + file.getPath() + ", frame=" + frameIndex, exception);
            }
        } finally {
            decodeAheadRunning.set(false);
            maybeLogDecodeAhead(frameIndex, decodedFrames, cachedFrames, startedNanos);
        }
    }

    private void maybeLogDecodeAhead(int frameIndex, int decodedFrames, int cachedFrames, long startedNanos) {
        if (decodedFrames == 0 || disposed) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastDecodeAheadLogNanos < SLOW_FRAME_LOG_INTERVAL_NANOS) {
            return;
        }
        lastDecodeAheadLogNanos = now;
        LOG.info("PAG decode-ahead: " + file.getPath()
                + ", afterFrame=" + frameIndex
                + ", decoded=" + decodedFrames
                + ", alreadyCached=" + cachedFrames
                + ", millis=" + TimeUnit.NANOSECONDS.toMillis(now - startedNanos));
    }

    private void updatePerformanceLabel(long decodeNanos, boolean playbackRender) {
        long decodeMillis = TimeUnit.NANOSECONDS.toMillis(decodeNanos);
        if (!playbackRender) {
            LOG.info("PAG render decode timing: " + file.getPath()
                    + ", decodeMillis=" + decodeMillis);
            return;
        }
        renderedPlaybackFrames++;
        long now = System.nanoTime();
        if (performanceWindowStartedNanos == 0L) {
            performanceWindowStartedNanos = now;
        }
        long elapsedNanos = now - performanceWindowStartedNanos;
        if (elapsedNanos >= PERFORMANCE_WINDOW_NANOS) {
            double measuredFps = renderedPlaybackFrames / (elapsedNanos / 1_000_000_000.0d);
            LOG.info("PAG playback performance: " + file.getPath()
                    + ", measuredFps=" + String.format("%.1f", measuredFps)
                    + ", decodeMillis=" + decodeMillis
                    + ", droppedTicks=" + droppedPlaybackFrames);
            resetPerformanceWindow();
        }
    }

    private void maybeLogSlowFrame(int frameIndex, long decodeNanos, boolean playbackRender) {
        if (!playbackRender || session == null) {
            return;
        }
        long decodeMillis = TimeUnit.NANOSECONDS.toMillis(decodeNanos);
        int budgetMillis = playbackDelayMillisForTests();
        long now = System.nanoTime();
        if (decodeMillis > budgetMillis && now - lastSlowFrameLogNanos >= SLOW_FRAME_LOG_INTERVAL_NANOS) {
            lastSlowFrameLogNanos = now;
            LOG.info("PAG playback slow frame: " + file.getPath()
                    + ", frame=" + frameIndex
                    + ", decodeMillis=" + decodeMillis
                    + ", budgetMillis=" + budgetMillis
                    + ", speed=" + String.format("%.2fx", playbackSpeed));
        }
    }

    private void resetPerformanceWindow() {
        performanceWindowStartedNanos = System.nanoTime();
        renderedPlaybackFrames = 0;
        droppedPlaybackFrames = 0;
    }

    private void cancelDecodeAhead() {
        decodeAheadGeneration.incrementAndGet();
    }

    int decodeAheadFrameCountForTests() {
        return decodeAheadFrameCount();
    }

    private int decodeAheadFrameCount() {
        String configuredValue = System.getProperty("pag.viewer.decodeAhead.frames");
        if (configuredValue == null) {
            return DEFAULT_DECODE_AHEAD_FRAMES;
        }
        try {
            return Math.max(0, Integer.parseInt(configuredValue));
        } catch (NumberFormatException exception) {
            LOG.info("Ignoring invalid PAG decode-ahead frame count: " + configuredValue);
            return DEFAULT_DECODE_AHEAD_FRAMES;
        }
    }

    private double selectedPlaybackSpeed() {
        Object selectedItem = speedComboBox.getSelectedItem();
        if (selectedItem == null) {
            return 1.0d;
        }
        String selectedSpeed = selectedItem.toString().replace("x", "");
        return Double.parseDouble(selectedSpeed);
    }

    private static void configureToolbarButton(javax.swing.AbstractButton button, String tooltip) {
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBorder(JBUI.Borders.empty(4));
        button.setPreferredSize(ICON_BUTTON_SIZE);
        button.setMinimumSize(ICON_BUTTON_SIZE);
        button.setMaximumSize(ICON_BUTTON_SIZE);
        button.putClientProperty("JButton.buttonType", "toolbar");
    }

    private static JSeparator verticalSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(JBUI.size(1, 24));
        separator.setMinimumSize(JBUI.size(1, 24));
        separator.setMaximumSize(JBUI.size(1, 24));
        return separator;
    }

    private static void configureToggleToolbarButton(
            JToggleButton button,
            String tooltip,
            Icon icon,
            String selectedTooltip,
            Icon selectedIcon
    ) {
        configureToolbarButton(button, tooltip);
        button.setIcon(icon);
        button.setSelectedIcon(selectedIcon);
        updateToggleToolbarTooltip(button, tooltip, selectedTooltip);
    }

    private static void updateToggleToolbarTooltip(javax.swing.AbstractButton button, String tooltip, String selectedTooltip) {
        button.setToolTipText(button.isSelected() ? selectedTooltip : tooltip);
    }

    private static long safeLength(VirtualFile file) {
        try {
            return file.getLength();
        } catch (Throwable throwable) {
            return -1L;
        }
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

    static final class RoundThumbSliderUI extends BasicSliderUI {
        private static final int THUMB_SIZE = 14;
        private static final int TRACK_HEIGHT = 3;

        RoundThumbSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return JBUI.size(THUMB_SIZE, THUMB_SIZE);
        }

        @Override
        public void paintTrack(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int height = JBUI.scale(TRACK_HEIGHT);
                int y = trackRect.y + (trackRect.height - height) / 2;
                int arc = height;
                Color trackColor = JBColor.namedColor("Slider.trackColor", new JBColor(new Color(0xC9CED6), new Color(0x6D737C)));
                Color progressColor = JBColor.namedColor("Slider.thumbColor", new JBColor(new Color(0x4C8DFF), new Color(0x8AB4FF)));
                graphics2D.setColor(trackColor);
                graphics2D.fillRoundRect(trackRect.x, y, trackRect.width, height, arc, arc);
                graphics2D.setColor(progressColor);
                int progressWidth = Math.max(0, thumbRect.x + thumbRect.width / 2 - trackRect.x);
                graphics2D.fillRoundRect(trackRect.x, y, progressWidth, height, arc, arc);
            } finally {
                graphics2D.dispose();
            }
        }

        @Override
        public void paintThumb(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color thumbColor = JBColor.namedColor("Slider.thumbColor", new JBColor(new Color(0x4C8DFF), new Color(0xAEB4BE)));
                Color borderColor = JBColor.namedColor("Slider.thumbBorderColor", new JBColor(new Color(0xFFFFFF), new Color(0x8C939D)));
                graphics2D.setColor(thumbColor);
                graphics2D.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
                graphics2D.setColor(borderColor);
                graphics2D.drawOval(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1);
            } finally {
                graphics2D.dispose();
            }
        }
    }

    private static final class ToggleToolbarIcon implements Icon {
        private static final int SIZE = 22;
        private static final Color ACTIVE_FILL = new Color(0x4C, 0x8D, 0xFF, 72);
        private static final Color ACTIVE_STROKE = new Color(0x75, 0xA8, 0xFF, 210);
        private static final Color CHESS_LIGHT = new Color(0xD9, 0xDE, 0xE7, 230);
        private static final Color CHESS_DARK = new Color(0x72, 0x7B, 0x8A, 230);

        private final boolean grid;
        private final boolean active;

        static ToggleToolbarIcon checkerboard(boolean active) {
            return new ToggleToolbarIcon(false, active);
        }

        static ToggleToolbarIcon grid(boolean active) {
            return new ToggleToolbarIcon(true, active);
        }

        private ToggleToolbarIcon(boolean grid, boolean active) {
            this.grid = grid;
            this.active = active;
        }

        @Override
        public int getIconWidth() {
            return JBUI.scale(SIZE);
        }

        @Override
        public int getIconHeight() {
            return JBUI.scale(SIZE);
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = getIconWidth();
                int inset = JBUI.scale(2);
                if (active) {
                    graphics2D.setColor(ACTIVE_FILL);
                    graphics2D.fillRoundRect(x + inset, y + inset, size - inset * 2, size - inset * 2, JBUI.scale(6), JBUI.scale(6));
                    graphics2D.setColor(ACTIVE_STROKE);
                    graphics2D.drawRoundRect(x + inset, y + inset, size - inset * 2 - 1, size - inset * 2 - 1, JBUI.scale(6), JBUI.scale(6));
                }
                if (grid) {
                    paintGrid(component, graphics2D, x, y, size);
                } else {
                    paintCheckerboard(component, graphics2D, x, y, size);
                }
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintCheckerboard(Component component, Graphics2D graphics2D, int x, int y, int size) {
            int square = JBUI.scale(3);
            int boardSize = square * 4;
            int left = x + (size - boardSize) / 2;
            int top = y + (size - boardSize) / 2;
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 4; column++) {
                    graphics2D.setColor(((row + column) & 1) == 0 ? CHESS_LIGHT : CHESS_DARK);
                    graphics2D.fillRect(left + column * square, top + row * square, square, square);
                }
            }
            graphics2D.setColor(withAlpha(foreground(component), active ? 210 : 150));
            graphics2D.drawRect(left, top, boardSize - 1, boardSize - 1);
        }

        private void paintGrid(Component component, Graphics2D graphics2D, int x, int y, int size) {
            int left = x + JBUI.scale(5);
            int top = y + JBUI.scale(5);
            int gridSize = size - JBUI.scale(10);
            int step = gridSize / 3;
            graphics2D.setStroke(new BasicStroke(JBUI.scale(1.35f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics2D.setColor(withAlpha(foreground(component), active ? 230 : 175));
            for (int index = 0; index <= 3; index++) {
                int offset = index * step;
                graphics2D.drawLine(left + offset, top, left + offset, top + gridSize);
                graphics2D.drawLine(left, top + offset, left + gridSize, top + offset);
            }
        }

        private static Color foreground(Component component) {
            Color color = component.getForeground();
            if (color != null) {
                return color;
            }
            color = UIManager.getColor("Label.foreground");
            return color == null ? Color.GRAY : color;
        }

        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
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
