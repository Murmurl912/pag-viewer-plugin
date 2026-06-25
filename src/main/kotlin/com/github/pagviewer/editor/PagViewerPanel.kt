package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.PagPreviewInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.HierarchyEvent
import javax.swing.AbstractButton
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JToggleButton
import javax.swing.SwingConstants

internal class PagViewerPanel(
    private val file: VirtualFile,
    private val loadReporter: LoadReporter,
    private val autoLoad: Boolean = true
) : JPanel(BorderLayout()) {

    private val canvas = PagCanvas()
    private val canvasScrollPane = JScrollPane(canvas)
    private val checkerboardButton = JToggleButton(ToggleToolbarIcon.checkerboard(false))
    private val gridButton = JToggleButton(ToggleToolbarIcon.grid(false))
    private val zoomInButton = JButton(AllIcons.General.ZoomIn)
    private val zoomOutButton = JButton(AllIcons.General.ZoomOut)
    private val fitZoomButton = JButton(AllIcons.General.FitContent)
    private val actualSizeButton = JButton(AllIcons.General.ActualZoom)
    private val playPauseButton = JButton(AllIcons.Actions.Execute)
    private val frameSlider = JSlider(0, 0, 0)
    private val speedComboBox = JComboBox(PLAYBACK_SPEEDS)
    private val statusLabel: JLabel = JBLabel("")
    private val metadataLabel: JLabel = JBLabel("")
    private val model = PagPlaybackModel()
    private val controller = PagPlaybackController(file, model)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)

    constructor(file: VirtualFile) : this(file, LOGGING)

    init {
        border = JBUI.Borders.empty(8)
        add(toolbar(), BorderLayout.NORTH)
        add(viewer(), BorderLayout.CENTER)
        add(playbackBar(), BorderLayout.SOUTH)
        configureActions()
        addHierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                controller.handleShowingChanged(isShowing)
            }
        }
        bindModel()
        if (autoLoad) {
            controller.load()
        }
    }

    fun preferredFocusedComponent(): JComponent = playPauseButton

    fun dispose() {
        controller.dispose()
        uiScope.cancel()
        canvas.dispose()
    }

    fun handleShowingChanged(showing: Boolean) {
        controller.handleShowingChanged(showing)
    }

    fun isPlayingForTests(): Boolean = controller.isPlaying()

    fun playbackSpeedForTests(): Double = controller.playbackSpeed()

    fun playbackDelayMillisForTests(): Int = controller.playbackDelayMillis()

    fun decodeAheadFrameCountForTests(): Int = controller.decodeAheadFrameCount()

    private fun bindModel() {
        uiScope.launch {
            model.renderedFrame.collect { frame ->
                frame?.let { canvas.setImage(it.image, it.playbackRender) }
            }
        }
        uiScope.launch {
            model.frameIndex.collect { frameSlider.value = it }
        }
        uiScope.launch {
            model.playing.collect { playing -> applyPlaying(playing) }
        }
        uiScope.launch {
            model.state.collect { state -> applyState(state) }
        }
    }

    private fun applyPlaying(playing: Boolean) {
        if (playing) {
            playPauseButton.icon = AllIcons.Actions.Pause
            playPauseButton.toolTipText = "Pause"
        } else {
            playPauseButton.icon = AllIcons.Actions.Execute
            playPauseButton.toolTipText = "Play"
        }
    }

    private fun applyState(state: PagPreviewState) {
        when (state) {
            is PagPreviewState.Loading -> Unit
            is PagPreviewState.Ready -> {
                val info = state.info
                frameSlider.maximum = maxOf(0, info.frameCount - 1)
                frameSlider.isEnabled = true
                playPauseButton.isEnabled = true
                metadataLabel.text = metadataText(info)
                statusLabel.text = ""
                loadReporter.previewReady(file, info)
            }
            is PagPreviewState.Failed -> {
                statusLabel.text = state.error.message
                playPauseButton.isEnabled = false
                frameSlider.isEnabled = false
                loadReporter.previewFailed(file, state.error)
            }
        }
    }

    private fun toolbar(): JComponent {
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.name = "pag-viewer-toolbar"
        toolbarPanel.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0))
        toolbar.name = "pag-viewer-toolbar-controls"
        toolbar.isOpaque = false
        toolbar.border = BorderFactory.createEmptyBorder()

        configureToggleToolbarButton(
            checkerboardButton,
            "Show chessboard",
            ToggleToolbarIcon.checkerboard(false),
            "Hide chessboard",
            ToggleToolbarIcon.checkerboard(true)
        )
        configureToggleToolbarButton(
            gridButton,
            "Show grid",
            ToggleToolbarIcon.grid(false),
            "Hide grid",
            ToggleToolbarIcon.grid(true)
        )
        configureToolbarButton(zoomInButton, "Zoom in")
        configureToolbarButton(zoomOutButton, "Zoom out")
        configureToolbarButton(actualSizeButton, "Actual size")
        configureToolbarButton(fitZoomButton, "Fit zoom")
        toolbar.add(checkerboardButton)
        toolbar.add(gridButton)
        toolbar.add(verticalSeparator())
        toolbar.add(zoomInButton)
        toolbar.add(zoomOutButton)
        toolbar.add(actualSizeButton)
        toolbar.add(fitZoomButton)

        metadataLabel.horizontalAlignment = SwingConstants.RIGHT
        toolbarPanel.add(toolbar, BorderLayout.WEST)
        toolbarPanel.add(metadataLabel, BorderLayout.EAST)
        return toolbarPanel
    }

    private fun viewer(): JComponent {
        canvasScrollPane.border = BorderFactory.createEmptyBorder()
        canvasScrollPane.viewport.isOpaque = false
        return canvasScrollPane
    }

    private fun playbackBar(): JComponent {
        val playbackBar = JPanel(BorderLayout(JBUI.scale(8), 0))
        playbackBar.name = "pag-playback-bar"
        playbackBar.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)

        val transportPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
        transportPanel.isOpaque = false
        playPauseButton.isEnabled = false
        configureToolbarButton(playPauseButton, "Play")
        transportPanel.add(playPauseButton)

        frameSlider.isEnabled = false
        frameSlider.paintTicks = false
        frameSlider.isOpaque = false
        frameSlider.isFocusable = false
        frameSlider.setUI(RoundThumbSliderUI(frameSlider))
        frameSlider.toolTipText = "Frame"

        val statusPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0))
        statusPanel.isOpaque = false
        statusLabel.horizontalAlignment = SwingConstants.LEFT
        speedComboBox.selectedItem = "1x"
        speedComboBox.toolTipText = "Playback speed"
        statusPanel.add(statusLabel)
        statusPanel.add(speedComboBox)

        playbackBar.add(transportPanel, BorderLayout.WEST)
        playbackBar.add(frameSlider, BorderLayout.CENTER)
        playbackBar.add(statusPanel, BorderLayout.EAST)
        return playbackBar
    }

    private fun configureActions() {
        checkerboardButton.addActionListener {
            canvas.setCheckerboardVisible(checkerboardButton.isSelected)
            updateToggleToolbarTooltip(checkerboardButton, "Show chessboard", "Hide chessboard")
        }
        gridButton.addActionListener {
            canvas.setGridVisible(gridButton.isSelected)
            updateToggleToolbarTooltip(gridButton, "Show grid", "Hide grid")
        }
        zoomInButton.addActionListener { canvas.zoomIn(canvasScrollPane.viewport) }
        zoomOutButton.addActionListener { canvas.zoomOut(canvasScrollPane.viewport) }
        actualSizeButton.addActionListener { canvas.setActualSize(canvasScrollPane.viewport) }
        fitZoomButton.addActionListener { canvas.setFitZoom() }
        playPauseButton.addActionListener { controller.togglePlay() }
        speedComboBox.addActionListener { controller.setSpeed(selectedPlaybackSpeed()) }
        frameSlider.addChangeListener {
            controller.seek(frameSlider.value, frameSlider.valueIsAdjusting)
        }
    }

    private fun selectedPlaybackSpeed(): Double {
        val selectedItem = speedComboBox.selectedItem ?: return 1.0
        val selectedSpeed = selectedItem.toString().replace("x", "")
        return selectedSpeed.toDouble()
    }

    interface LoadReporter {
        fun previewReady(file: VirtualFile, info: PagPreviewInfo)

        fun previewFailed(file: VirtualFile, exception: Exception)
    }

    companion object {
        private val LOG = Logger.getInstance(PagViewerPanel::class.java)
        private val PLAYBACK_SPEEDS = arrayOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
        private val ICON_BUTTON_SIZE = JBUI.size(30, 30)

        private val LOGGING: LoadReporter = object : LoadReporter {
            override fun previewReady(file: VirtualFile, info: PagPreviewInfo) {
                LOG.info(
                    "PAG preview ready: ${file.path}, decoder=${info.width}x${info.height}" +
                        ", composition=${info.compositionWidth}x${info.compositionHeight}" +
                        ", frames=${info.frameCount}, fps=${String.format("%.2f", info.frameRate)}"
                )
                if (info.hasScaledDecoderSize()) {
                    LOG.info(
                        "PAG decoder size differs from composition size: ${file.path}" +
                            ", decoder=${info.width}x${info.height}" +
                            ", composition=${info.compositionWidth}x${info.compositionHeight}"
                    )
                }
            }

            override fun previewFailed(file: VirtualFile, exception: Exception) {
                LOG.warn("PAG preview failed: ${file.path}", exception)
            }
        }

        private fun configureToolbarButton(button: AbstractButton, tooltip: String) {
            button.toolTipText = tooltip
            button.isFocusable = false
            button.isContentAreaFilled = false
            button.isBorderPainted = false
            button.isOpaque = false
            button.isRolloverEnabled = true
            button.border = JBUI.Borders.empty(4)
            button.preferredSize = ICON_BUTTON_SIZE
            button.minimumSize = ICON_BUTTON_SIZE
            button.maximumSize = ICON_BUTTON_SIZE
            button.putClientProperty("JButton.buttonType", "toolbar")
        }

        private fun verticalSeparator(): JSeparator {
            val separator = JSeparator(SwingConstants.VERTICAL)
            separator.preferredSize = JBUI.size(1, 24)
            separator.minimumSize = JBUI.size(1, 24)
            separator.maximumSize = JBUI.size(1, 24)
            return separator
        }

        private fun configureToggleToolbarButton(
            button: JToggleButton,
            tooltip: String,
            icon: Icon,
            selectedTooltip: String,
            selectedIcon: Icon
        ) {
            configureToolbarButton(button, tooltip)
            button.icon = icon
            button.selectedIcon = selectedIcon
            updateToggleToolbarTooltip(button, tooltip, selectedTooltip)
        }

        private fun updateToggleToolbarTooltip(button: AbstractButton, tooltip: String, selectedTooltip: String) {
            button.toolTipText = if (button.isSelected) selectedTooltip else tooltip
        }

        private fun metadataText(info: PagPreviewInfo): String =
            sizeText(info) + " | " + info.frameCount + " frames | " + String.format("%.2f fps", info.frameRate)

        private fun sizeText(info: PagPreviewInfo): String {
            val decoderSize = "${info.width} x ${info.height}"
            if (!info.hasScaledDecoderSize()) {
                return decoderSize
            }
            return decoderSize + " decode | " + info.compositionWidth + " x " + info.compositionHeight + " comp"
        }
    }
}
