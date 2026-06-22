package com.github.pagviewer.nativebridge;

public record PagPreviewInfo(
        int width,
        int height,
        int frameCount,
        float frameRate,
        int compositionWidth,
        int compositionHeight
) {
    public boolean isRenderable() {
        return width > 0 && height > 0 && frameCount > 0 && frameRate > 0.0f;
    }

    public boolean hasScaledDecoderSize() {
        return compositionWidth > 0
                && compositionHeight > 0
                && (compositionWidth != width || compositionHeight != height);
    }
}
