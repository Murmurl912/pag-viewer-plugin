package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.PagPreviewInfo
import com.github.pagviewer.nativebridge.PagPreviewSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PagFrameSource(private val session: PagPreviewSession) {
    private val decodeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PAG Viewer Frame Decoder").apply { isDaemon = true }
    }
    private val prefetchExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PAG Viewer Decode Ahead").apply { isDaemon = true }
    }
    private val decodeDispatcher: CoroutineDispatcher = decodeExecutor.asCoroutineDispatcher()
    private val prefetchDispatcher: CoroutineDispatcher = prefetchExecutor.asCoroutineDispatcher()

    val info: PagPreviewInfo get() = session.info

    suspend fun frame(index: Int): BufferedImage = withContext(decodeDispatcher) {
        session.readFrame(index)
    }

    suspend fun prefetch(afterFrame: Int, count: Int) {
        if (count <= 0) {
            return
        }
        withContext(prefetchDispatcher) {
            for (offset in 1..count) {
                if (session.preloadFrame(afterFrame + offset) == PagPreviewSession.PreloadResult.UNAVAILABLE) {
                    return@withContext
                }
            }
        }
    }

    fun close() {
        session.close()
        decodeExecutor.shutdownNow()
        prefetchExecutor.shutdownNow()
    }
}
