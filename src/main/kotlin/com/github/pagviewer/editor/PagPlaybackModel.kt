package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.PagPreviewInfo
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.image.BufferedImage

internal sealed interface PagPreviewState {
    data object Loading : PagPreviewState
    data class Ready(val info: PagPreviewInfo) : PagPreviewState
    data class Failed(val error: Exception) : PagPreviewState
}

internal data class RenderedFrame(val image: BufferedImage, val playbackRender: Boolean)

internal class PagPlaybackModel {
    val state = MutableStateFlow<PagPreviewState>(PagPreviewState.Loading)
    val renderedFrame = MutableStateFlow<RenderedFrame?>(null)
    val frameIndex = MutableStateFlow(0)
    val playing = MutableStateFlow(false)
}
