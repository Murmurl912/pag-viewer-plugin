package com.github.pagviewer.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PagPlaybackModelTest {
    @Test
    fun startsInLoadingStateWithNoFrame() {
        val model = PagPlaybackModel()
        assertEquals(PagPreviewState.Loading, model.state.value)
        assertNull(model.renderedFrame.value)
        assertEquals(0, model.frameIndex.value)
        assertFalse(model.playing.value)
    }
}
