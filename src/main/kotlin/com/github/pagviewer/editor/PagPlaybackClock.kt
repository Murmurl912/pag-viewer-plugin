package com.github.pagviewer.editor

import com.github.pagviewer.nativebridge.PagFrameClock

internal class PagPlaybackClock(private val nanoTime: () -> Long = System::nanoTime) {
    @Volatile
    private var speed: Double = 1.0
    private var dueNanos: Long = 0L

    fun start() {
        dueNanos = nanoTime()
    }

    fun setSpeed(speed: Double) {
        this.speed = speed
    }

    fun frameIntervalMillis(frameRate: Float): Int =
        maxOf(1, Math.round(PagFrameClock.delayMillis(frameRate) / speed).toInt())

    fun nextDelayMillis(frameRate: Float): Long {
        dueNanos += frameIntervalMillis(frameRate).toLong() * 1_000_000L
        val now = nanoTime()
        if (dueNanos <= now) {
            dueNanos = now
            return 0L
        }
        return (dueNanos - now) / 1_000_000L
    }
}
