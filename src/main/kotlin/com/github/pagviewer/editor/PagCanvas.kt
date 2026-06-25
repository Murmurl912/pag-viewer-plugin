package com.github.pagviewer.editor

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Transparency
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

internal class PagCanvas : JComponent(), Scrollable {
    private val bufferingPainter = VolatileImageBufferingPainter(Transparency.OPAQUE)
    private val highQualityRepaintTimer: Timer = Timer(HIGH_QUALITY_REPAINT_DELAY_MILLIS) { requestHighQualityRenderingNow() }
    private var frameImage: BufferedImage? = null
    private var checkerboardVisible = false
    private var gridVisible = false
    private var fitZoom = true
    private var zoomScale = 1.0
    private var highQualityRenderingRequested = true

    init {
        isOpaque = true
        background = JBColor.PanelBackground
        minimumSize = Dimension(240, 180)
        preferredSize = Dimension(640, 420)
        highQualityRepaintTimer.isRepeats = false
        addMouseWheelListener { handleZoomGesture(it) }
    }

    fun setImage(image: BufferedImage) {
        setImage(image, false)
    }

    fun setImage(image: BufferedImage, playbackFrame: Boolean) {
        this.frameImage = image
        updatePreferredSize()
        if (playbackFrame) {
            highQualityRenderingRequested = false
            highQualityRepaintTimer.restart()
        } else {
            requestHighQualityRenderingNow()
        }
        repaint()
    }

    fun currentImage(): BufferedImage? = frameImage

    fun isCheckerboardVisible(): Boolean = checkerboardVisible

    fun setCheckerboardVisible(checkerboardVisible: Boolean) {
        this.checkerboardVisible = checkerboardVisible
        requestHighQualityRenderingNow()
    }

    fun isGridVisible(): Boolean = gridVisible

    fun setGridVisible(gridVisible: Boolean) {
        this.gridVisible = gridVisible
        requestHighQualityRenderingNow()
    }

    fun isFitZoom(): Boolean = fitZoom

    fun zoomScale(): Double = zoomScale

    fun zoomIn() {
        setManualZoom(zoomScale * ZOOM_STEP)
    }

    fun zoomIn(viewport: JViewport) {
        zoomBy(viewport, viewportCenterPoint(viewport), ZOOM_STEP)
    }

    fun zoomOut() {
        setManualZoom(zoomScale / ZOOM_STEP)
    }

    fun zoomOut(viewport: JViewport) {
        zoomBy(viewport, viewportCenterPoint(viewport), 1.0 / ZOOM_STEP)
    }

    fun setActualSize() {
        setManualZoom(1.0)
    }

    fun setActualSize(viewport: JViewport?) {
        if (viewport == null) {
            setActualSize()
            return
        }
        zoomTo(viewport, viewportCenterPoint(viewport), 1.0)
    }

    fun setFitZoom() {
        fitZoom = true
        updatePreferredSize()
        requestHighQualityRenderingNow()
    }

    fun isHighQualityRenderingRequestedForTests(): Boolean = highQualityRenderingRequested

    fun isHighQualityRepaintScheduledForTests(): Boolean = highQualityRepaintTimer.isRunning

    fun dispose() {
        highQualityRepaintTimer.stop()
        bufferingPainter.flush()
    }

    override fun removeNotify() {
        dispose()
        super.removeNotify()
    }

    override fun invalidate() {
        bufferingPainter.flush()
        super.invalidate()
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        if (width != this.width || height != this.height) {
            bufferingPainter.flush()
        }
        super.setBounds(x, y, width, height)
    }

    private fun requestHighQualityRenderingNow() {
        highQualityRepaintTimer.stop()
        highQualityRenderingRequested = true
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (frameImage == null) {
            return
        }
        bufferingPainter.paint(graphics, size) { paintContent(it) }
    }

    private fun paintContent(g: Graphics2D) {
        val image = frameImage ?: return
        g.color = background
        g.fillRect(0, 0, width, height)
        try {
            configureRenderingHints(g)
            val scale = currentPaintScale(Dimension(width, height))
            val drawWidth = maxOf(1, Math.round(image.width * scale).toInt())
            val drawHeight = maxOf(1, Math.round(image.height * scale).toInt())
            val x = (width - drawWidth) / 2
            val y = (height - drawHeight) / 2
            if (checkerboardVisible) {
                paintCheckerboard(g, x, y, drawWidth, drawHeight)
            }
            g.drawImage(image, x, y, drawWidth, drawHeight, null)
            if (gridVisible) {
                paintGrid(g, x, y, drawWidth, drawHeight)
            }
        } finally {
            g.clip = null
        }
    }

    private fun configureRenderingHints(g: Graphics2D) {
        val interpolation = if (highQualityRenderingRequested) {
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        } else {
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        }
        val rendering = if (highQualityRenderingRequested) {
            RenderingHints.VALUE_RENDER_QUALITY
        } else {
            RenderingHints.VALUE_RENDER_SPEED
        }
        val antialiasing = if (highQualityRenderingRequested) {
            RenderingHints.VALUE_ANTIALIAS_ON
        } else {
            RenderingHints.VALUE_ANTIALIAS_OFF
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, rendering)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(24)

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        maxOf(
            JBUI.scale(96),
            if (orientation == SwingConstants.HORIZONTAL) {
                visibleRect.width - JBUI.scale(48)
            } else {
                visibleRect.height - JBUI.scale(48)
            }
        )

    override fun getScrollableTracksViewportWidth(): Boolean =
        fitZoom || frameImage == null || preferredSizeTracksViewportWidth()

    override fun getScrollableTracksViewportHeight(): Boolean =
        fitZoom || frameImage == null || preferredSizeTracksViewportHeight()

    fun imageBoundsForTests(viewSize: Dimension): Rectangle =
        imageBounds(viewSize, currentPaintScale(viewSize))

    private fun setManualZoom(scale: Double) {
        fitZoom = false
        zoomScale = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        updatePreferredSize()
        requestHighQualityRenderingNow()
    }

    private fun handleZoomGesture(event: MouseWheelEvent) {
        if (frameImage == null || (!event.isControlDown && !event.isMetaDown)) {
            return
        }
        val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport ?: return
        val factor = Math.pow(ZOOM_STEP, -event.preciseWheelRotation)
        zoomBy(viewport, event.point, factor)
        event.consume()
    }

    private fun zoomBy(viewport: JViewport?, focalViewPoint: Point?, factor: Double) {
        if (frameImage == null || viewport == null || focalViewPoint == null) {
            setManualZoom(zoomScale * factor)
            return
        }
        val oldScale = currentPaintScale(currentViewSize(viewport))
        zoomTo(viewport, focalViewPoint, oldScale * factor)
    }

    private fun zoomTo(viewport: JViewport?, focalViewPoint: Point?, targetScale: Double) {
        val image = frameImage
        if (image == null || viewport == null || focalViewPoint == null) {
            setManualZoom(targetScale)
            return
        }
        val oldViewSize = currentViewSize(viewport)
        val oldImageBounds = imageBounds(oldViewSize, currentPaintScale(oldViewSize))
        val oldViewPosition = viewport.viewPosition
        val focalOffsetX = focalViewPoint.x - oldViewPosition.x
        val focalOffsetY = focalViewPoint.y - oldViewPosition.y
        val oldScale = currentPaintScale(oldViewSize)
        val imageX = ((focalViewPoint.x - oldImageBounds.x) / oldScale).coerceIn(0.0, image.width.toDouble())
        val imageY = ((focalViewPoint.y - oldImageBounds.y) / oldScale).coerceIn(0.0, image.height.toDouble())

        fitZoom = false
        zoomScale = targetScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        updatePreferredSize()

        val newViewSize = manualViewSizeForViewport(viewport)
        size = newViewSize
        viewport.viewSize = newViewSize
        val newImageBounds = imageBounds(newViewSize, zoomScale)
        val nextX = Math.round(newImageBounds.x + imageX * zoomScale - focalOffsetX).toInt()
        val nextY = Math.round(newImageBounds.y + imageY * zoomScale - focalOffsetY).toInt()
        viewport.viewPosition = Point(
            nextX.coerceIn(0, maxOf(0, newViewSize.width - viewport.extentSize.width)),
            nextY.coerceIn(0, maxOf(0, newViewSize.height - viewport.extentSize.height))
        )
        requestHighQualityRenderingNow()
    }

    private fun currentViewSize(viewport: JViewport?): Dimension {
        if (viewport != null && fitZoom) {
            val extentSize = viewport.extentSize
            if (extentSize.width > 0 && extentSize.height > 0) {
                return extentSize
            }
        }
        if (viewport != null) {
            val viewSize = viewport.viewSize
            if (viewSize.width > 0 && viewSize.height > 0) {
                return viewSize
            }
        }
        val currentWidth = width
        val currentHeight = height
        if (currentWidth > 0 && currentHeight > 0) {
            return Dimension(currentWidth, currentHeight)
        }
        return preferredSize
    }

    private fun manualViewSizeForViewport(viewport: JViewport): Dimension {
        val preferred = manualPreferredSize(zoomScale)
        val extent = viewport.extentSize
        return Dimension(
            maxOf(preferred.width, extent.width),
            maxOf(preferred.height, extent.height)
        )
    }

    private fun viewportCenterPoint(viewport: JViewport): Point {
        val viewPosition = viewport.viewPosition
        val extent = viewport.extentSize
        return Point(viewPosition.x + extent.width / 2, viewPosition.y + extent.height / 2)
    }

    private fun currentPaintScale(viewSize: Dimension): Double {
        if (!fitZoom) {
            return zoomScale
        }
        val image = frameImage!!
        val scale = minOf(
            viewSize.width / image.width.toDouble(),
            viewSize.height / image.height.toDouble()
        )
        return scale.coerceIn(MIN_ZOOM, 1.0)
    }

    private fun updatePreferredSize() {
        preferredSize = if (frameImage == null || fitZoom) {
            Dimension(640, 420)
        } else {
            manualPreferredSize(zoomScale)
        }
        revalidate()
    }

    private fun manualPreferredSize(scale: Double): Dimension {
        val image = frameImage!!
        val width = maxOf(1, Math.round(image.width * scale).toInt())
        val height = maxOf(1, Math.round(image.height * scale).toInt())
        return Dimension(width, height)
    }

    private fun imageBounds(viewSize: Dimension, scale: Double): Rectangle {
        val image = frameImage!!
        val width = maxOf(1, Math.round(image.width * scale).toInt())
        val height = maxOf(1, Math.round(image.height * scale).toInt())
        return Rectangle((viewSize.width - width) / 2, (viewSize.height - height) / 2, width, height)
    }

    private fun preferredSizeTracksViewportWidth(): Boolean {
        val viewport = parent as? JViewport ?: return false
        return preferredSize.width <= viewport.extentSize.width
    }

    private fun preferredSizeTracksViewportHeight(): Boolean {
        val viewport = parent as? JViewport ?: return false
        return preferredSize.height <= viewport.extentSize.height
    }

    companion object {
        private const val MIN_ZOOM = 0.05
        private const val MAX_ZOOM = 16.0
        private const val ZOOM_STEP = 1.25
        private val CHECKER_SIZE = JBUI.scale(12)
        private val GRID_SIZE = JBUI.scale(32)
        private const val HIGH_QUALITY_REPAINT_DELAY_MILLIS = 500

        private fun paintCheckerboard(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            val light = JBColor.namedColor("ImageViewer.checkerboard.light", JBColor(Color(0xF4F5F7), Color(0x45484D)))
            val dark = JBColor.namedColor("ImageViewer.checkerboard.dark", JBColor(Color(0xD7DBE2), Color(0x2E3035)))
            var row = 0
            while (row < height) {
                var column = 0
                while (column < width) {
                    val darkSquare = ((row / CHECKER_SIZE) + (column / CHECKER_SIZE)) % 2 == 0
                    g.color = if (darkSquare) dark else light
                    g.fillRect(
                        x + column,
                        y + row,
                        minOf(CHECKER_SIZE, width - column),
                        minOf(CHECKER_SIZE, height - row)
                    )
                    column += CHECKER_SIZE
                }
                row += CHECKER_SIZE
            }
        }

        private fun paintGrid(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
            g.color = JBColor.namedColor("ImageViewer.gridColor", Color(0x66000000, true))
            var gridX = x
            while (gridX <= x + width) {
                g.drawLine(gridX, y, gridX, y + height)
                gridX += GRID_SIZE
            }
            var gridY = y
            while (gridY <= y + height) {
                g.drawLine(x, gridY, x + width, gridY)
                gridY += GRID_SIZE
            }
        }
    }
}
