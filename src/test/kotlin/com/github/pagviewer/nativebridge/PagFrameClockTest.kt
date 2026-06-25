package com.github.pagviewer.nativebridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PagFrameClockTest {
    @Test
    fun delayUsesRoundedFrameDurationWithMinimumOneMillisecond() {
        assertEquals(42, PagFrameClock.delayMillis(24.0f))
        assertEquals(17, PagFrameClock.delayMillis(60.0f))
        assertEquals(1, PagFrameClock.delayMillis(0.0f))
        assertEquals(1, PagFrameClock.delayMillis(2400.0f))
    }

    @Test
    fun nextFrameWrapsAtEndAndHandlesEmptyAnimations() {
        assertEquals(1, PagFrameClock.nextFrame(0, 10))
        assertEquals(0, PagFrameClock.nextFrame(9, 10))
        assertEquals(0, PagFrameClock.nextFrame(0, 0))
    }
}
