package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.JnaPagNativeLibrary
import com.github.pagviewer.nativebridge.PagFrameClock
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver
import com.github.pagviewer.nativebridge.PagPreviewSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal class PagPlaybackController(
    private val file: VirtualFile,
    private val model: PagPlaybackModel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    private val clock = PagPlaybackClock()

    @Volatile
    private var frameSource: PagFrameSource? = null
    private var playbackJob: Job? = null
    private var seekJob: Job? = null
    private var playbackSpeed = 1.0

    private var performanceWindowStartedNanos = 0L
    private var renderedPlaybackFrames = 0
    private var lastSlowFrameLogNanos = 0L

    fun load() {
        scope.launch {
            try {
                val source = openFrameSource()
                val first = source.frame(0)
                model.frameIndex.value = 0
                model.renderedFrame.value = RenderedFrame(first, playbackRender = false)
                model.state.value = PagPreviewState.Ready(source.info)
                LOG.info("PAG preview ready: ${file.path}, frames=${source.info.frameCount}")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                model.state.value = PagPreviewState.Failed(exception)
            }
        }
    }

    fun togglePlay() {
        if (model.playing.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val state = model.state.value
        if (state !is PagPreviewState.Ready || model.playing.value) {
            return
        }
        model.playing.value = true
        val frameRate = state.info.frameRate
        val frameCount = state.info.frameCount
        clock.start()
        resetPerformanceWindow()
        LOG.info("PAG playback started: ${file.path}, fps=${String.format("%.2f", frameRate)}, speed=$playbackSpeed")
        playbackJob = scope.launch {
            try {
                while (isActive) {
                    val source = frameSource ?: break
                    val next = PagFrameClock.nextFrame(model.frameIndex.value, frameCount)
                    val startedNanos = System.nanoTime()
                    val image = source.frame(next)
                    val decodeNanos = System.nanoTime() - startedNanos
                    model.frameIndex.value = next
                    model.renderedFrame.value = RenderedFrame(image, playbackRender = true)
                    updatePerformanceLog(decodeNanos)
                    maybeLogSlowFrame(next, decodeNanos, frameRate)
                    scope.launch { prefetch(source, next) }
                    delay(clock.nextDelayMillis(frameRate))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                LOG.warn("PAG playback failed: ${file.path}", exception)
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        model.playing.value = false
        LOG.info("PAG playback stopped: ${file.path}, frame=${model.frameIndex.value}")
    }

    fun seek(frameIndex: Int, valueIsAdjusting: Boolean) {
        if (!valueIsAdjusting && model.playing.value) {
            return
        }
        val source = frameSource ?: return
        seekJob?.cancel()
        seekJob = scope.launch {
            try {
                val image = source.frame(frameIndex)
                model.frameIndex.value = frameIndex
                model.renderedFrame.value = RenderedFrame(image, playbackRender = false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                LOG.warn("PAG render failed: ${file.path}, frame=$frameIndex", exception)
            }
        }
    }

    fun setSpeed(speed: Double) {
        playbackSpeed = speed
        clock.setSpeed(speed)
        LOG.info("PAG playback speed changed: ${file.path}, speed=$speed")
    }

    fun handleShowingChanged(showing: Boolean) {
        if (!showing && model.playing.value) {
            LOG.info("PAG playback stopped because tab is hidden: ${file.path}")
            pause()
        }
    }

    fun dispose() {
        LOG.info("PAG preview disposed: ${file.path}, frame=${model.frameIndex.value}")
        scope.cancel()
        frameSource?.close()
        frameSource = null
    }

    fun isPlaying(): Boolean = model.playing.value

    fun playbackSpeed(): Double = playbackSpeed

    fun playbackDelayMillis(): Int {
        val state = model.state.value
        return if (state is PagPreviewState.Ready) clock.frameIntervalMillis(state.info.frameRate) else 0
    }

    fun decodeAheadFrameCount(): Int {
        val configuredValue = System.getProperty("pag.viewer.decodeAhead.frames") ?: return DEFAULT_DECODE_AHEAD_FRAMES
        return try {
            maxOf(0, configuredValue.toInt())
        } catch (exception: NumberFormatException) {
            LOG.info("Ignoring invalid PAG decode-ahead frame count: $configuredValue")
            DEFAULT_DECODE_AHEAD_FRAMES
        }
    }

    private suspend fun openFrameSource(): PagFrameSource = withContext(Dispatchers.IO) {
        val nativePath = PagNativeLibraryResolver().resolve().orElse(null)
            ?: throw IllegalStateException(
                "Set -Dpag.viewer.libpag.path or PAG_VIEWER_LIBPAG_PATH to a libpag dynamic library."
            )
        val nativeLibrary = JnaPagNativeLibrary.load(nativePath)
        val path = file.toNioPath()
            ?: throw IllegalStateException("This PAG file is not backed by a local path.")
        val bytes = file.contentsToByteArray()
        val session = PagPreviewSession.open(nativeLibrary, bytes, path, 60.0f, 1.0f)
        val source = PagFrameSource(session)
        frameSource = source
        source
    }

    private suspend fun prefetch(source: PagFrameSource, afterFrame: Int) {
        try {
            source.prefetch(afterFrame, decodeAheadFrameCount())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            LOG.warn("PAG decode-ahead failed: ${file.path}, frame=$afterFrame", exception)
        }
    }

    private fun resetPerformanceWindow() {
        performanceWindowStartedNanos = System.nanoTime()
        renderedPlaybackFrames = 0
    }

    private fun updatePerformanceLog(decodeNanos: Long) {
        renderedPlaybackFrames++
        val now = System.nanoTime()
        if (performanceWindowStartedNanos == 0L) {
            performanceWindowStartedNanos = now
        }
        val elapsedNanos = now - performanceWindowStartedNanos
        if (elapsedNanos >= PERFORMANCE_WINDOW_NANOS) {
            val measuredFps = renderedPlaybackFrames / (elapsedNanos / 1_000_000_000.0)
            LOG.info(
                "PAG playback performance: ${file.path}" +
                    ", measuredFps=${String.format("%.1f", measuredFps)}" +
                    ", decodeMillis=${TimeUnit.NANOSECONDS.toMillis(decodeNanos)}"
            )
            resetPerformanceWindow()
        }
    }

    private fun maybeLogSlowFrame(frameIndex: Int, decodeNanos: Long, frameRate: Float) {
        val decodeMillis = TimeUnit.NANOSECONDS.toMillis(decodeNanos)
        val budgetMillis = clock.frameIntervalMillis(frameRate)
        val now = System.nanoTime()
        if (decodeMillis > budgetMillis && now - lastSlowFrameLogNanos >= SLOW_FRAME_LOG_INTERVAL_NANOS) {
            lastSlowFrameLogNanos = now
            LOG.info(
                "PAG playback slow frame: ${file.path}" +
                    ", frame=$frameIndex" +
                    ", decodeMillis=$decodeMillis" +
                    ", budgetMillis=$budgetMillis" +
                    ", speed=${String.format("%.2fx", playbackSpeed)}"
            )
        }
    }

    companion object {
        private val LOG = Logger.getInstance(PagPlaybackController::class.java)
        private const val DEFAULT_DECODE_AHEAD_FRAMES = 6
        private val PERFORMANCE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1)
        private val SLOW_FRAME_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2)
    }
}
