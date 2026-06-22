package com.github.pagviewer.editor;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

final class PagCanvas extends JComponent implements Scrollable {
    private static final double MIN_ZOOM = 0.05d;
    private static final double MAX_ZOOM = 16.0d;
    private static final double ZOOM_STEP = 1.25d;
    private static final int CHECKER_SIZE = JBUI.scale(12);
    private static final int GRID_SIZE = JBUI.scale(32);
    private static final int HIGH_QUALITY_REPAINT_DELAY_MILLIS = 500;

    private final VolatileImageBufferingPainter bufferingPainter = new VolatileImageBufferingPainter(Transparency.OPAQUE);
    private final Timer highQualityRepaintTimer;
    private BufferedImage image;
    private boolean checkerboardVisible;
    private boolean gridVisible;
    private boolean fitZoom = true;
    private double zoomScale = 1.0d;
    private boolean highQualityRenderingRequested = true;

    PagCanvas() {
        setOpaque(true);
        setBackground(JBColor.PanelBackground);
        setMinimumSize(new Dimension(240, 180));
        setPreferredSize(new Dimension(640, 420));
        highQualityRepaintTimer = new Timer(HIGH_QUALITY_REPAINT_DELAY_MILLIS, event -> requestHighQualityRenderingNow());
        highQualityRepaintTimer.setRepeats(false);
        addMouseWheelListener(this::handleZoomGesture);
    }

    void setImage(BufferedImage image) {
        setImage(image, false);
    }

    void setImage(BufferedImage image, boolean playbackFrame) {
        this.image = image;
        updatePreferredSize();
        if (playbackFrame) {
            highQualityRenderingRequested = false;
            highQualityRepaintTimer.restart();
        } else {
            requestHighQualityRenderingNow();
        }
        repaint();
    }

    BufferedImage currentImage() {
        return image;
    }

    boolean isCheckerboardVisible() {
        return checkerboardVisible;
    }

    void setCheckerboardVisible(boolean checkerboardVisible) {
        this.checkerboardVisible = checkerboardVisible;
        requestHighQualityRenderingNow();
    }

    boolean isGridVisible() {
        return gridVisible;
    }

    void setGridVisible(boolean gridVisible) {
        this.gridVisible = gridVisible;
        requestHighQualityRenderingNow();
    }

    boolean isFitZoom() {
        return fitZoom;
    }

    double zoomScale() {
        return zoomScale;
    }

    void zoomIn() {
        setManualZoom(zoomScale * ZOOM_STEP);
    }

    void zoomIn(JViewport viewport) {
        zoomBy(viewport, viewportCenterPoint(viewport), ZOOM_STEP);
    }

    void zoomOut() {
        setManualZoom(zoomScale / ZOOM_STEP);
    }

    void zoomOut(JViewport viewport) {
        zoomBy(viewport, viewportCenterPoint(viewport), 1.0d / ZOOM_STEP);
    }

    void setActualSize() {
        setManualZoom(1.0d);
    }

    void setActualSize(JViewport viewport) {
        if (viewport == null) {
            setActualSize();
            return;
        }
        zoomTo(viewport, viewportCenterPoint(viewport), 1.0d);
    }

    void setFitZoom() {
        fitZoom = true;
        updatePreferredSize();
        requestHighQualityRenderingNow();
    }

    boolean isHighQualityRenderingRequestedForTests() {
        return highQualityRenderingRequested;
    }

    boolean isHighQualityRepaintScheduledForTests() {
        return highQualityRepaintTimer.isRunning();
    }

    void dispose() {
        highQualityRepaintTimer.stop();
        bufferingPainter.flush();
    }

    @Override
    public void removeNotify() {
        dispose();
        super.removeNotify();
    }

    @Override
    public void invalidate() {
        bufferingPainter.flush();
        super.invalidate();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        if (width != getWidth() || height != getHeight()) {
            bufferingPainter.flush();
        }
        super.setBounds(x, y, width, height);
    }

    private void requestHighQualityRenderingNow() {
        highQualityRepaintTimer.stop();
        highQualityRenderingRequested = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (image == null) {
            return;
        }

        bufferingPainter.paint(graphics, getSize(), this::paintContent);
    }

    private void paintContent(Graphics2D g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        try {
            configureRenderingHints(g);
            double scale = currentPaintScale(new Dimension(getWidth(), getHeight()));
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            if (checkerboardVisible) {
                paintCheckerboard(g, x, y, width, height);
            }
            g.drawImage(image, x, y, width, height, null);
            if (gridVisible) {
                paintGrid(g, x, y, width, height);
            }
        } finally {
            g.setClip(null);
        }
    }

    private void configureRenderingHints(Graphics2D g) {
        Object interpolation = highQualityRenderingRequested
                ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
        Object rendering = highQualityRenderingRequested
                ? RenderingHints.VALUE_RENDER_QUALITY
                : RenderingHints.VALUE_RENDER_SPEED;
        Object antialiasing = highQualityRenderingRequested
                ? RenderingHints.VALUE_ANTIALIAS_ON
                : RenderingHints.VALUE_ANTIALIAS_OFF;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, rendering);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return JBUI.scale(24);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(JBUI.scale(96), orientation == javax.swing.SwingConstants.HORIZONTAL
                ? visibleRect.width - JBUI.scale(48)
                : visibleRect.height - JBUI.scale(48));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return fitZoom || image == null || preferredSizeTracksViewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return fitZoom || image == null || preferredSizeTracksViewportHeight();
    }

    Rectangle imageBoundsForTests(Dimension viewSize) {
        return imageBounds(viewSize, currentPaintScale(viewSize));
    }

    private void setManualZoom(double scale) {
        fitZoom = false;
        zoomScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale));
        updatePreferredSize();
        requestHighQualityRenderingNow();
    }

    private void handleZoomGesture(MouseWheelEvent event) {
        if (image == null || (!event.isControlDown() && !event.isMetaDown())) {
            return;
        }
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport == null) {
            return;
        }
        double factor = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
        zoomBy(viewport, event.getPoint(), factor);
        event.consume();
    }

    private void zoomBy(JViewport viewport, Point focalViewPoint, double factor) {
        if (image == null || viewport == null || focalViewPoint == null) {
            setManualZoom(zoomScale * factor);
            return;
        }
        double oldScale = currentPaintScale(currentViewSize(viewport));
        zoomTo(viewport, focalViewPoint, oldScale * factor);
    }

    private void zoomTo(JViewport viewport, Point focalViewPoint, double targetScale) {
        if (image == null || viewport == null || focalViewPoint == null) {
            setManualZoom(targetScale);
            return;
        }
        Dimension oldViewSize = currentViewSize(viewport);
        Rectangle oldImageBounds = imageBounds(oldViewSize, currentPaintScale(oldViewSize));
        Point oldViewPosition = viewport.getViewPosition();
        int focalOffsetX = focalViewPoint.x - oldViewPosition.x;
        int focalOffsetY = focalViewPoint.y - oldViewPosition.y;
        double oldScale = currentPaintScale(oldViewSize);
        double imageX = clamp((focalViewPoint.x - oldImageBounds.x) / oldScale, 0.0d, image.getWidth());
        double imageY = clamp((focalViewPoint.y - oldImageBounds.y) / oldScale, 0.0d, image.getHeight());

        fitZoom = false;
        zoomScale = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetScale));
        updatePreferredSize();

        Dimension newViewSize = manualViewSizeForViewport(viewport);
        setSize(newViewSize);
        viewport.setViewSize(newViewSize);
        Rectangle newImageBounds = imageBounds(newViewSize, zoomScale);
        int nextX = (int) Math.round(newImageBounds.x + imageX * zoomScale - focalOffsetX);
        int nextY = (int) Math.round(newImageBounds.y + imageY * zoomScale - focalOffsetY);
        viewport.setViewPosition(new Point(
                clamp(nextX, 0, Math.max(0, newViewSize.width - viewport.getExtentSize().width)),
                clamp(nextY, 0, Math.max(0, newViewSize.height - viewport.getExtentSize().height))
        ));
        requestHighQualityRenderingNow();
    }

    private Dimension currentViewSize(JViewport viewport) {
        if (viewport != null && fitZoom) {
            Dimension extentSize = viewport.getExtentSize();
            if (extentSize.width > 0 && extentSize.height > 0) {
                return extentSize;
            }
        }
        if (viewport != null) {
            Dimension viewSize = viewport.getViewSize();
            if (viewSize.width > 0 && viewSize.height > 0) {
                return viewSize;
            }
        }
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            return new Dimension(width, height);
        }
        return getPreferredSize();
    }

    private Dimension manualViewSizeForViewport(JViewport viewport) {
        Dimension preferred = manualPreferredSize(zoomScale);
        Dimension extent = viewport.getExtentSize();
        return new Dimension(
                Math.max(preferred.width, extent.width),
                Math.max(preferred.height, extent.height)
        );
    }

    private Point viewportCenterPoint(JViewport viewport) {
        Point viewPosition = viewport.getViewPosition();
        Dimension extent = viewport.getExtentSize();
        return new Point(viewPosition.x + extent.width / 2, viewPosition.y + extent.height / 2);
    }

    private double currentPaintScale(Dimension viewSize) {
        if (!fitZoom) {
            return zoomScale;
        }
        double scale = Math.min(
                viewSize.width / (double) image.getWidth(),
                viewSize.height / (double) image.getHeight()
        );
        return Math.max(MIN_ZOOM, Math.min(1.0d, scale));
    }

    private void updatePreferredSize() {
        if (image == null || fitZoom) {
            setPreferredSize(new Dimension(640, 420));
        } else {
            setPreferredSize(manualPreferredSize(zoomScale));
        }
        revalidate();
    }

    private Dimension manualPreferredSize(double scale) {
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return new Dimension(width, height);
    }

    private Rectangle imageBounds(Dimension viewSize, double scale) {
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        return new Rectangle((viewSize.width - width) / 2, (viewSize.height - height) / 2, width, height);
    }

    private boolean preferredSizeTracksViewportWidth() {
        return getParent() instanceof JViewport viewport
                && getPreferredSize().width <= viewport.getExtentSize().width;
    }

    private boolean preferredSizeTracksViewportHeight() {
        return getParent() instanceof JViewport viewport
                && getPreferredSize().height <= viewport.getExtentSize().height;
    }

    private static void paintCheckerboard(Graphics2D g, int x, int y, int width, int height) {
        Color light = JBColor.namedColor("ImageViewer.checkerboard.light", new JBColor(new Color(0xF4F5F7), new Color(0x45484D)));
        Color dark = JBColor.namedColor("ImageViewer.checkerboard.dark", new JBColor(new Color(0xD7DBE2), new Color(0x2E3035)));
        for (int row = 0; row < height; row += CHECKER_SIZE) {
            for (int column = 0; column < width; column += CHECKER_SIZE) {
                boolean darkSquare = ((row / CHECKER_SIZE) + (column / CHECKER_SIZE)) % 2 == 0;
                g.setColor(darkSquare ? dark : light);
                g.fillRect(x + column, y + row, Math.min(CHECKER_SIZE, width - column), Math.min(CHECKER_SIZE, height - row));
            }
        }
    }

    private static void paintGrid(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(JBColor.namedColor("ImageViewer.gridColor", new Color(0x66000000, true)));
        for (int gridX = x; gridX <= x + width; gridX += GRID_SIZE) {
            g.drawLine(gridX, y, gridX, y + height);
        }
        for (int gridY = y; gridY <= y + height; gridY += GRID_SIZE) {
            g.drawLine(x, gridY, x + width, gridY);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
