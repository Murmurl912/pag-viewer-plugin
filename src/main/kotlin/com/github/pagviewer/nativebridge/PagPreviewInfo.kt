package com.github.pagviewer.nativebridge

data class PagPreviewInfo(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val frameRate: Float,
    val compositionWidth: Int,
    val compositionHeight: Int
) {
    fun isRenderable(): Boolean =
        width > 0 && height > 0 && frameCount > 0 && frameRate > 0.0f

    fun hasScaledDecoderSize(): Boolean =
        compositionWidth > 0 &&
            compositionHeight > 0 &&
            (compositionWidth != width || compositionHeight != height)
}
