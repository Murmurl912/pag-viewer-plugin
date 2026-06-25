package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.PagFrameClock
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver
import com.github.pagviewer.nativebridge.PagPreviewInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JViewport
import javax.swing.SwingUtilities

@TestApplication
internal class PagViewerPanelTest {
    @Test
    fun exposesImageViewerChromeAndPlaybackControlsAtBottom() {
        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait {
            panelRef.set(PagViewerPanel(LightVirtualFile("empty.pag"), NoopLoadReporter(), autoLoad = false))
        }

        val panel = panelRef.get()
        try {
            SwingUtilities.invokeAndWait {
                val chessboardButton = findButtonWithTooltip(panel, "Show chessboard")!!
                val gridButton = findButtonWithTooltip(panel, "Show grid")!!
                assertToggleHasVisibleSelectedState(chessboardButton)
                assertToggleHasVisibleSelectedState(gridButton)
                assertToolbarButtonUsesIconStyle(chessboardButton)
                assertToolbarButtonUsesIconStyle(gridButton)
                chessboardButton.doClick()
                gridButton.doClick()
                assertTrue(chessboardButton.isSelected)
                assertTrue(gridButton.isSelected)
                assertEquals("Hide chessboard", chessboardButton.toolTipText)
                assertEquals("Hide grid", gridButton.toolTipText)
                assertNotNull(findButtonWithTooltip(panel, "Zoom in"))
                assertNotNull(findButtonWithTooltip(panel, "Zoom out"))
                assertNotNull(findButtonWithTooltip(panel, "Fit zoom"))
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Zoom in")!!)
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Zoom out")!!)
                assertToolbarButtonUsesIconStyle(findButtonWithTooltip(panel, "Fit zoom")!!)
                assertToolbarIsTight(panel)
                assertNotNull(findFirst(panel, JScrollPane::class.java))
                val speedBox = findFirst(panel, JComboBox::class.java)!!
                assertTrue(hasNamedAncestor(speedBox, "pag-playback-bar"))
                assertComboContains(speedBox, "0.25x", "1x", "2x")

                val playButton = findButtonWithTooltip(panel, "Play")!!
                assertTrue(hasNamedAncestor(playButton, "pag-playback-bar"))
                assertFalse(hasNamedAncestor(playButton, "pag-viewer-toolbar"))
                assertToolbarButtonUsesIconStyle(playButton)
                assertEquals(1, countTransportButtons(panel))

                val slider = findFirst(panel, JSlider::class.java)!!
                assertTrue(slider.ui is RoundThumbSliderUI)
            }
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    @Test
    fun canvasSupportsCheckerboardGridAndZoomState() {
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(100, 80, BufferedImage.TYPE_INT_ARGB))
            canvasRef.set(canvas)
        }

        SwingUtilities.invokeAndWait {
            val canvas = canvasRef.get()
            assertTrue(canvas.isFitZoom())
            assertFalse(canvas.isCheckerboardVisible())
            assertFalse(canvas.isGridVisible())

            canvas.setCheckerboardVisible(true)
            canvas.setGridVisible(true)
            assertTrue(canvas.isCheckerboardVisible())
            assertTrue(canvas.isGridVisible())

            val originalZoom = canvas.zoomScale()
            canvas.zoomIn()
            assertFalse(canvas.isFitZoom())
            assertTrue(canvas.zoomScale() > originalZoom)

            canvas.zoomOut()
            canvas.setActualSize()
            assertFalse(canvas.isFitZoom())
            assertEquals(1.0, canvas.zoomScale(), 0.001)

            canvas.setFitZoom()
            assertTrue(canvas.isFitZoom())
        }
    }

    @Test
    fun fitZoomDoesNotUpscaleSmallDecodedFrames() {
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            val image = BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB)
            val imageGraphics = image.createGraphics()
            try {
                imageGraphics.color = Color.MAGENTA
                imageGraphics.fillRect(0, 0, image.width, image.height)
            } finally {
                imageGraphics.dispose()
            }
            canvas.setImage(image)
            canvas.setFitZoom()
            canvas.setSize(240, 240)
            canvasRef.set(canvas)
        }

        val paintedBounds = AtomicReference<Rectangle>()
        SwingUtilities.invokeAndWait {
            val target = BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB)
            val graphics = target.createGraphics()
            try {
                canvasRef.get().paint(graphics)
            } finally {
                graphics.dispose()
            }
            paintedBounds.set(boundsOfColor(target, Color.MAGENTA.rgb))
        }

        assertEquals(Rectangle(108, 108, 24, 24), paintedBounds.get())
    }

    @Test
    fun toolbarZoomKeepsViewportCenterAsFocalPoint() {
        val viewportRef = AtomicReference<JViewport>()
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB))
            canvas.setActualSize()
            val scrollPane = JScrollPane(canvas)
            scrollPane.setSize(200, 200)
            scrollPane.doLayout()
            canvas.size = canvas.preferredSize
            val viewport = scrollPane.viewport
            viewport.extentSize = Dimension(200, 200)
            viewport.viewSize = canvas.preferredSize
            viewport.viewPosition = Point(300, 400)

            canvas.zoomIn(viewport)

            viewportRef.set(viewport)
            canvasRef.set(canvas)
        }

        SwingUtilities.invokeAndWait {
            assertEquals(1.25, canvasRef.get().zoomScale(), 0.001)
            assertEquals(Point(400, 525), viewportRef.get().viewPosition)
        }
    }

    @Test
    fun manualZoomStillTracksViewportWhenImageIsSmallerThanViewport() {
        val canvasRef = AtomicReference<PagCanvas>()
        val viewportRef = AtomicReference<JViewport>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(202, 202, BufferedImage.TYPE_INT_ARGB))
            val scrollPane = JScrollPane(canvas)
            scrollPane.setSize(900, 700)
            scrollPane.doLayout()
            val viewport = scrollPane.viewport
            viewport.extentSize = Dimension(900, 700)
            viewport.viewSize = Dimension(900, 700)

            canvas.zoomIn(viewport)

            canvasRef.set(canvas)
            viewportRef.set(viewport)
        }

        SwingUtilities.invokeAndWait {
            val canvas = canvasRef.get()
            val viewport = viewportRef.get()
            assertFalse(canvas.isFitZoom())
            assertTrue(canvas.getScrollableTracksViewportWidth())
            assertTrue(canvas.getScrollableTracksViewportHeight())
            val imageBounds = canvas.imageBoundsForTests(viewport.extentSize)
            assertEquals(Rectangle(323, 223, 253, 253), imageBounds)
        }
    }

    @Test
    fun zoomWheelGestureKeepsMousePositionAsFocalPoint() {
        val viewportRef = AtomicReference<JViewport>()
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB))
            canvas.setActualSize()
            val scrollPane = JScrollPane(canvas)
            scrollPane.setSize(200, 200)
            scrollPane.doLayout()
            canvas.size = canvas.preferredSize
            val viewport = scrollPane.viewport
            viewport.extentSize = Dimension(200, 200)
            viewport.viewSize = canvas.preferredSize
            viewport.viewPosition = Point(300, 400)

            val zoomEvent = MouseWheelEvent(
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
                -1.0
            )
            canvas.dispatchEvent(zoomEvent)

            viewportRef.set(viewport)
            canvasRef.set(canvas)
        }

        SwingUtilities.invokeAndWait {
            assertEquals(1.25, canvasRef.get().zoomScale(), 0.001)
            assertEquals(Point(388, 513), viewportRef.get().viewPosition)
        }
    }

    @Test
    fun checkerboardHasVisibleContrastOnDarkBackground() {
        val targetRef = AtomicReference<BufferedImage>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.background = Color(0x1E1F22)
            canvas.setImage(BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB))
            canvas.setCheckerboardVisible(true)
            canvas.setFitZoom()
            canvas.setSize(48, 48)

            val target = BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB)
            val graphics = target.createGraphics()
            try {
                canvas.paint(graphics)
            } finally {
                graphics.dispose()
            }
            targetRef.set(target)
        }

        val bounds = Rectangle(0, 0, 48, 48)
        assertTrue(luminanceRange(targetRef.get(), bounds) >= 24)
    }

    @Test
    fun playbackFramesScheduleDeferredHighQualityPaint() {
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), true)
            canvasRef.set(canvas)
        }

        SwingUtilities.invokeAndWait {
            val canvas = canvasRef.get()
            assertFalse(canvas.isHighQualityRenderingRequestedForTests())
            assertTrue(canvas.isHighQualityRepaintScheduledForTests())

            canvas.setImage(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), false)
            assertTrue(canvas.isHighQualityRenderingRequestedForTests())
            assertFalse(canvas.isHighQualityRepaintScheduledForTests())
        }
    }

    @Test
    fun disposingCanvasStopsDeferredHighQualityPaintTimer() {
        val canvasRef = AtomicReference<PagCanvas>()
        SwingUtilities.invokeAndWait {
            val canvas = PagCanvas()
            canvas.setImage(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), true)
            canvasRef.set(canvas)
        }

        SwingUtilities.invokeAndWait {
            val canvas = canvasRef.get()
            assertTrue(canvas.isHighQualityRepaintScheduledForTests())

            canvas.dispose()

            assertFalse(canvas.isHighQualityRepaintScheduledForTests())
        }
    }

    @Test
    fun decodeAheadFrameCountCanBeDisabledForProfiling() {
        val previousValue = System.getProperty(DECODE_AHEAD_FRAMES_PROPERTY)
        System.setProperty(DECODE_AHEAD_FRAMES_PROPERTY, "0")
        val panelRef = AtomicReference<PagViewerPanel?>()
        try {
            SwingUtilities.invokeAndWait {
                panelRef.set(PagViewerPanel(LightVirtualFile("empty.pag"), NoopLoadReporter(), autoLoad = false))
            }

            SwingUtilities.invokeAndWait { assertEquals(0, panelRef.get()!!.decodeAheadFrameCountForTests()) }
        } finally {
            panelRef.get()?.let { panel -> SwingUtilities.invokeAndWait { panel.dispose() } }
            restoreSystemProperty(DECODE_AHEAD_FRAMES_PROPERTY, previousValue)
        }
    }

    @Test
    fun loadsRealPagFileIntoReadyPreviewState() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath()
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val virtualFile: VirtualFile = PathBackedLightVirtualFile(sample)
        val readyFile = AtomicReference<VirtualFile>()
        val readyInfo = AtomicReference<PagPreviewInfo?>()

        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait {
            panelRef.set(
                PagViewerPanel(
                    virtualFile,
                    object : PagViewerPanel.LoadReporter {
                        override fun previewReady(file: VirtualFile, info: PagPreviewInfo) {
                            readyFile.set(file)
                            readyInfo.set(info)
                        }

                        override fun previewFailed(file: VirtualFile, exception: Exception) {
                            fail<Nothing>("PAG preview should decode the sample file.")
                        }
                    }
                )
            )
        }

        val panel = panelRef.get()
        try {
            waitForReadyPreview(panel)
            val info = readyInfo.get()
            if (info == null ||
                readyFile.get() != virtualFile ||
                info.width != 202 ||
                info.height != 202 ||
                info.frameCount != 72
            ) {
                fail<Nothing>("PAG preview did not report ready metadata for the decoded sample.")
            }
            SwingUtilities.invokeAndWait { assertNull(findLabelText(panel, "Ready")) }
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    @Test
    fun scrubbingRealPagFileRendersDifferentFramePixels() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath()
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val virtualFile: VirtualFile = PathBackedLightVirtualFile(sample)

        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait { panelRef.set(PagViewerPanel(virtualFile, FailingLoadReporter())) }

        val panel = panelRef.get()
        try {
            waitForReadyPreview(panel)
            val firstDigest = waitForCanvasDigest(panel)

            SwingUtilities.invokeAndWait { findFirst(panel, JSlider::class.java)!!.value = 12 }

            waitForChangedCanvasDigest(panel, firstDigest)
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    @Test
    fun hidingPanelStopsPlayback() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath()
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val virtualFile: VirtualFile = PathBackedLightVirtualFile(sample)

        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait { panelRef.set(PagViewerPanel(virtualFile, FailingLoadReporter())) }

        val panel = panelRef.get()
        try {
            waitForReadyPreview(panel)
            SwingUtilities.invokeAndWait { findButtonWithTooltip(panel, "Play")!!.doClick() }
            waitForPlaying(panel)
            waitForButtonWithTooltip(panel, "Pause")
            SwingUtilities.invokeAndWait {
                assertTrue(panel.isPlayingForTests())
                panel.handleShowingChanged(false)
                assertFalse(panel.isPlayingForTests())
            }
            waitForButtonWithTooltip(panel, "Play")
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    @Test
    fun readyPreviewStaysPausedAndSpeedCanBeAdjusted() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath()
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val virtualFile: VirtualFile = PathBackedLightVirtualFile(sample)

        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait { panelRef.set(PagViewerPanel(virtualFile, FailingLoadReporter())) }

        val panel = panelRef.get()
        try {
            waitForReadyPreview(panel)
            waitForButtonWithTooltip(panel, "Play")
            SwingUtilities.invokeAndWait {
                assertFalse(panel.isPlayingForTests())
                findButtonWithTooltip(panel, "Play")!!.doClick()
            }
            waitForPlaying(panel)
            waitForButtonWithTooltip(panel, "Pause")
            SwingUtilities.invokeAndWait {
                assertEquals(1, countTransportButtons(panel))
                assertNull(findLabelContaining(panel, "Speed 1x"))
                val speedBox = findFirst(panel, JComboBox::class.java)!!
                speedBox.selectedItem = "2x"
                assertEquals(2.0, panel.playbackSpeedForTests(), 0.001)
                assertEquals(PagFrameClock.delayMillis(24.0f) / 2, panel.playbackDelayMillisForTests())
                assertNull(findLabelContaining(panel, "Speed 2x"))

                speedBox.selectedItem = "0.25x"
                assertEquals(0.25, panel.playbackSpeedForTests(), 0.001)
                assertEquals(PagFrameClock.delayMillis(24.0f) * 4, panel.playbackDelayMillisForTests())
            }
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    @Test
    fun playbackButtonPausesWithoutRewindingCurrentFrame() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val sample = Path.of("reference/libpag/web/lite/demo/assets/frames.pag").toAbsolutePath()
        assumeTrue(Files.isRegularFile(sample), "libpag sample PAG file is available")

        val virtualFile: VirtualFile = PathBackedLightVirtualFile(sample)

        val panelRef = AtomicReference<PagViewerPanel>()
        SwingUtilities.invokeAndWait { panelRef.set(PagViewerPanel(virtualFile, FailingLoadReporter())) }

        val panel = panelRef.get()
        try {
            waitForReadyPreview(panel)
            SwingUtilities.invokeAndWait { findButtonWithTooltip(panel, "Play")!!.doClick() }
            waitForPlaying(panel)
            waitForButtonWithTooltip(panel, "Pause")
            waitForSliderAboveZero(panel)
            val frameBeforePause = AtomicReference<Int>()
            SwingUtilities.invokeAndWait {
                val slider = findFirst(panel, JSlider::class.java)!!
                frameBeforePause.set(slider.value)
                findButtonWithTooltip(panel, "Pause")!!.doClick()
                assertFalse(panel.isPlayingForTests())
            }
            waitForButtonWithTooltip(panel, "Play")
            SwingUtilities.invokeAndWait {
                val slider = findFirst(panel, JSlider::class.java)!!
                assertTrue(slider.value > 0)
                assertTrue(slider.value >= frameBeforePause.get())
            }
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose() }
        }
    }

    private fun waitForReadyPreview(panel: PagViewerPanel) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val metadata = AtomicReference<String?>()
            val image = AtomicReference<BufferedImage?>()
            SwingUtilities.invokeAndWait {
                metadata.set(findLabelContaining(panel, "72 frames | 24.00 fps"))
                image.set(findFirst(panel, PagCanvas::class.java)?.currentImage())
            }
            if (metadata.get() != null && image.get() != null) {
                return
            }
            Thread.sleep(50)
        }
        fail<Nothing>("PAG preview panel did not reach a ready decoded state.")
    }

    private fun waitForPlaying(panel: PagViewerPanel) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val playing = AtomicReference<Boolean?>()
            SwingUtilities.invokeAndWait { playing.set(panel.isPlayingForTests()) }
            if (playing.get() == true) {
                return
            }
            Thread.sleep(50)
        }
        fail<Nothing>("PAG preview did not auto-play after loading.")
    }

    private fun waitForSliderAboveZero(panel: PagViewerPanel) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val frame = AtomicReference<Int?>()
            SwingUtilities.invokeAndWait { frame.set(findFirst(panel, JSlider::class.java)!!.value) }
            val value = frame.get()
            if (value != null && value > 0) {
                return
            }
            Thread.sleep(50)
        }
        fail<Nothing>("PAG preview did not advance beyond the first frame.")
    }

    private fun waitForButtonWithTooltip(panel: PagViewerPanel, tooltip: String) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val present = AtomicReference<Boolean?>()
            SwingUtilities.invokeAndWait { present.set(findButtonWithTooltip(panel, tooltip) != null) }
            if (present.get() == true) {
                return
            }
            Thread.sleep(50)
        }
        fail<Nothing>("Button with tooltip '$tooltip' did not appear.")
    }

    private fun findLabelText(component: Component, expectedText: String): String? {
        if (component is JLabel && expectedText == component.text) {
            return component.text
        }
        if (component is Container) {
            for (child in component.components) {
                val text = findLabelText(child, expectedText)
                if (text != null) {
                    return text
                }
            }
        }
        return null
    }

    private fun findLabelContaining(component: Component, expectedText: String): String? {
        if (component is JLabel && component.text.contains(expectedText)) {
            return component.text
        }
        if (component is Container) {
            for (child in component.components) {
                val text = findLabelContaining(child, expectedText)
                if (text != null) {
                    return text
                }
            }
        }
        return null
    }

    private fun waitForCanvasDigest(panel: PagViewerPanel): Long {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val image = AtomicReference<BufferedImage?>()
            SwingUtilities.invokeAndWait { image.set(findFirst(panel, PagCanvas::class.java)?.currentImage()) }
            val decoded = image.get()
            if (decoded != null) {
                return digest(decoded)
            }
            Thread.sleep(50)
        }
        fail<Nothing>("PAG preview canvas did not receive a decoded frame.")
        return 0L
    }

    private fun waitForChangedCanvasDigest(panel: PagViewerPanel, firstDigest: Long) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            val image = AtomicReference<BufferedImage?>()
            SwingUtilities.invokeAndWait { image.set(findFirst(panel, PagCanvas::class.java)?.currentImage()) }
            val decoded = image.get()
            if (decoded != null && digest(decoded) != firstDigest) {
                return
            }
            Thread.sleep(50)
        }
        fail<Nothing>("Scrubbing the PAG preview did not render different frame pixels.")
    }

    private fun digest(image: BufferedImage): Long {
        var digest = 1125899906842597L
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                digest = 31 * digest + image.getRGB(x, y)
                x += 7
            }
            y += 7
        }
        return digest
    }

    private fun boundsOfColor(image: BufferedImage, rgb: Int): Rectangle {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) == rgb) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return Rectangle()
        }
        return Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun luminanceRange(image: BufferedImage, bounds: Rectangle): Int {
        var min = 255
        var max = 0
        for (y in bounds.y until bounds.y + bounds.height) {
            for (x in bounds.x until bounds.x + bounds.width) {
                val color = Color(image.getRGB(x, y), true)
                val luminance = Math.round(color.red * 0.2126 + color.green * 0.7152 + color.blue * 0.0722).toInt()
                min = minOf(min, luminance)
                max = maxOf(max, luminance)
            }
        }
        return max - min
    }

    private fun <T : Component> findFirst(component: Component, type: Class<T>): T? {
        if (type.isInstance(component)) {
            return type.cast(component)
        }
        if (component is Container) {
            for (child in component.components) {
                val found = findFirst(child, type)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findButtonWithTooltip(component: Component, tooltip: String): AbstractButton? {
        if (component is AbstractButton && tooltip == component.toolTipText) {
            return component
        }
        if (component is Container) {
            for (child in component.components) {
                val found = findButtonWithTooltip(child, tooltip)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun hasNamedAncestor(component: Component, name: String): Boolean {
        var current: Component? = component
        while (current != null) {
            if (name == current.name) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun countTransportButtons(component: Component): Int {
        var count = 0
        if (component is AbstractButton && hasNamedAncestor(component, "pag-playback-bar")) {
            val tooltip = component.toolTipText
            if ("Play" == tooltip || "Pause" == tooltip || "Stop" == tooltip) {
                count++
            }
        }
        if (component is Container) {
            for (child in component.components) {
                count += countTransportButtons(child)
            }
        }
        return count
    }

    private fun assertToggleHasVisibleSelectedState(button: AbstractButton) {
        assertNotNull(button.icon)
        assertNotNull(button.selectedIcon)
        assertFalse(button.icon === button.selectedIcon)
    }

    private fun assertToolbarButtonUsesIconStyle(button: AbstractButton) {
        assertFalse(button.isContentAreaFilled)
        assertFalse(button.isBorderPainted)
        assertFalse(button.isFocusable)
    }

    private fun assertToolbarIsTight(panel: PagViewerPanel) {
        val toolbarControls = findNamed(panel, "pag-viewer-toolbar-controls")
        assertNotNull(toolbarControls)
        assertTrue(toolbarControls!!.preferredSize.width <= 260)
    }

    private fun findNamed(component: Component, name: String): Component? {
        if (name == component.name) {
            return component
        }
        if (component is Container) {
            for (child in component.components) {
                val found = findNamed(child, name)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun assertComboContains(comboBox: JComboBox<*>, vararg expectedItems: String) {
        for (expectedItem in expectedItems) {
            var found = false
            for (index in 0 until comboBox.itemCount) {
                if (expectedItem == comboBox.getItemAt(index)) {
                    found = true
                    break
                }
            }
            assertTrue(found, "Missing playback speed option: $expectedItem")
        }
    }

    private fun restoreSystemProperty(key: String, previousValue: String?) {
        if (previousValue == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, previousValue)
        }
    }

    private class NoopLoadReporter : PagViewerPanel.LoadReporter {
        override fun previewReady(file: VirtualFile, info: PagPreviewInfo) {}

        override fun previewFailed(file: VirtualFile, exception: Exception) {}
    }

    private class FailingLoadReporter : PagViewerPanel.LoadReporter {
        override fun previewReady(file: VirtualFile, info: PagPreviewInfo) {}

        override fun previewFailed(file: VirtualFile, exception: Exception) {
            fail<Nothing>("PAG preview should decode the sample file.")
        }
    }

    private class PathBackedLightVirtualFile(private val path: Path) : LightVirtualFile(path.fileName.toString()) {
        private val bytes: ByteArray = Files.readAllBytes(path)

        override fun toNioPath(): Path = path

        override fun contentsToByteArray(): ByteArray = bytes.clone()
    }

    companion object {
        private const val DECODE_AHEAD_FRAMES_PROPERTY = "pag.viewer.decodeAhead.frames"
    }
}
