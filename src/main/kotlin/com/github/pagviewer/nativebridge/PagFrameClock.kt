package com.github.pagviewer.nativebridge

object PagFrameClock {
    fun delayMillis(frameRate: Float): Int {
        if (frameRate <= 0.0f) {
            return 1
        }
        return maxOf(1, Math.round(1000.0f / frameRate))
    }

    fun nextFrame(currentFrame: Int, frameCount: Int): Int {
        if (frameCount <= 0) {
            return 0
        }
        return (currentFrame + 1) % frameCount
    }
}
