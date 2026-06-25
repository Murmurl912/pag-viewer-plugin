package com.github.pagviewer.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PagPlaybackClockTest {
    @Test
    fun intervalScalesWithSpeed() {
        val clock = PagPlaybackClock { 0L }
        assertEquals(42, clock.frameIntervalMillis(24.0f))
        clock.setSpeed(2.0)
        assertEquals(21, clock.frameIntervalMillis(24.0f))
        clock.setSpeed(0.25)
        assertEquals(168, clock.frameIntervalMillis(24.0f))
    }

    @Test
    fun pacesByIntervalWhenOnSchedule() {
        var now = 0L
        val clock = PagPlaybackClock { now }
        clock.start()
        assertEquals(42L, clock.nextDelayMillis(24.0f))
        now = 42_000_000L
        assertEquals(42L, clock.nextDelayMillis(24.0f))
    }

    @Test
    fun rebasesWhenOverdueWithoutBursting() {
        var now = 0L
        val clock = PagPlaybackClock { now }
        clock.start()
        clock.nextDelayMillis(24.0f)
        now = 500_000_000L
        assertEquals(0L, clock.nextDelayMillis(24.0f))
        assertEquals(42L, clock.nextDelayMillis(24.0f))
    }
}
