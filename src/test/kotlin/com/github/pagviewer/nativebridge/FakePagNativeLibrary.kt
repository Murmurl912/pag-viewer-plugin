package com.github.pagviewer.nativebridge

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class FakePagNativeLibrary(
    private val width: Int,
    private val height: Int,
    private val frames: Int,
    private val frameRate: Float,
    private val pixels: ByteArray,
    private val changedFrames: BooleanArray? = null
) : PagNativeLibrary {
    private val handles = AtomicLong(1)
    private val readFrameCalls = AtomicInteger()

    override fun loadFile(bytes: ByteArray, filePath: String): Long =
        if (bytes.isEmpty()) 0 else handles.getAndIncrement()

    override fun createDecoder(composition: Long, maxFrameRate: Float, scale: Float): Long =
        if (composition == 0L) 0 else handles.getAndIncrement()

    override fun compositionWidth(composition: Long): Int = width

    override fun compositionHeight(composition: Long): Int = height

    override fun width(decoder: Long): Int = width

    override fun height(decoder: Long): Int = height

    override fun frameCount(decoder: Long): Int = frames

    override fun frameRate(decoder: Long): Float = frameRate

    override fun checkFrameChanged(decoder: Long, frameIndex: Int): Boolean {
        val changed = changedFrames ?: return true
        return changed[Math.floorMod(frameIndex, changed.size)]
    }

    override fun readFrame(decoder: Long, frameIndex: Int, destination: ByteBuffer, rowBytes: Int): Boolean {
        readFrameCalls.incrementAndGet()
        destination.clear()
        destination.put(pixels)
        destination.flip()
        return true
    }

    fun readFrameCalls(): Int = readFrameCalls.get()

    override fun release(handle: Long) {}
}
