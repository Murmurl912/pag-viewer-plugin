package com.github.pagviewer.nativebridge;

public final class PagFrameClock {
    private PagFrameClock() {
    }

    public static int delayMillis(float frameRate) {
        if (frameRate <= 0.0f) {
            return 1;
        }
        return Math.max(1, Math.round(1000.0f / frameRate));
    }

    public static int nextFrame(int currentFrame, int frameCount) {
        if (frameCount <= 0) {
            return 0;
        }
        return (currentFrame + 1) % frameCount;
    }
}

