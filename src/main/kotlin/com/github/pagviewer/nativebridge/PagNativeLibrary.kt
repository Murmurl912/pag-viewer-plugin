package com.github.pagviewer.nativebridge

import java.nio.ByteBuffer

interface PagNativeLibrary {
    fun loadFile(bytes: ByteArray, filePath: String): Long

    fun createDecoder(composition: Long, maxFrameRate: Float, scale: Float): Long

    fun compositionWidth(composition: Long): Int

    fun compositionHeight(composition: Long): Int

    fun width(decoder: Long): Int

    fun height(decoder: Long): Int

    fun frameCount(decoder: Long): Int

    fun frameRate(decoder: Long): Float

    fun checkFrameChanged(decoder: Long, frameIndex: Int): Boolean

    fun readFrame(decoder: Long, frameIndex: Int, destination: ByteBuffer, rowBytes: Int): Boolean

    fun release(handle: Long)

    companion object {
        const val COLOR_TYPE_BGRA_8888 = 3
        const val ALPHA_TYPE_UNPREMULTIPLIED = 3
    }
}
