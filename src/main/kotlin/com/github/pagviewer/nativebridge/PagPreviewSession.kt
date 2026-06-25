package com.github.pagviewer.nativebridge

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

class PagPreviewSession private constructor(
    private val nativeLibrary: PagNativeLibrary,
    private val fileHandle: Long,
    private val decoderHandle: Long,
    val info: PagPreviewInfo
) : AutoCloseable {
    private val rowBytes: Int = Math.multiplyExact(info.width, Int.SIZE_BYTES)
    private val pixelBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(Math.toIntExact(rowBytes.toLong() * info.height))
        .order(ByteOrder.LITTLE_ENDIAN)
    private val frameCache: Array<BufferedImage?>? =
        if (shouldCacheFrames(info)) arrayOfNulls(info.frameCount) else null
    private val streamingImages: Array<BufferedImage>? =
        if (frameCache == null) createStreamingImages(info) else null
    private var lastNativeDecodedImage: BufferedImage? = null
    private var lastReturnedImage: BufferedImage? = null
    private var nextStreamingImage: Int = 0
    private var closed: Boolean = false

    @Synchronized
    @Throws(IOException::class)
    fun preloadFrame(frameIndex: Int): PreloadResult {
        ensureOpen()
        val cache = frameCache ?: return PreloadResult.UNAVAILABLE
        val safeFrame = Math.floorMod(frameIndex, info.frameCount)
        if (cache[safeFrame] != null) {
            return PreloadResult.ALREADY_CACHED
        }
        readFrame(safeFrame)
        return PreloadResult.DECODED
    }

    @Synchronized
    @Throws(IOException::class)
    fun readFrame(frameIndex: Int): BufferedImage {
        ensureOpen()
        val safeFrame = Math.floorMod(frameIndex, info.frameCount)
        val cache = frameCache
        val cachedImage = cache?.get(safeFrame)
        if (cachedImage != null) {
            lastReturnedImage = cachedImage
            return cachedImage
        }
        val lastDecoded = lastNativeDecodedImage
        if (lastDecoded != null &&
            lastReturnedImage === lastDecoded &&
            !nativeLibrary.checkFrameChanged(decoderHandle, safeFrame)
        ) {
            cache?.set(safeFrame, lastDecoded)
            lastReturnedImage = lastDecoded
            return lastDecoded
        }

        val image = nextWritableImage()
        pixelBuffer.clear()
        val ok = nativeLibrary.readFrame(decoderHandle, safeFrame, pixelBuffer, rowBytes)
        if (!ok) {
            throw IOException("libpag failed to decode frame $safeFrame.")
        }

        pixelBuffer.position(0)
        val pixels = (image.raster.dataBuffer as DataBufferInt).data
        pixelBuffer.asIntBuffer().get(pixels, 0, pixels.size)
        cache?.set(safeFrame, image)
        lastNativeDecodedImage = image
        lastReturnedImage = image
        return image
    }

    @Synchronized
    override fun close() {
        if (closed) {
            return
        }
        closed = true
        nativeLibrary.release(decoderHandle)
        nativeLibrary.release(fileHandle)
    }

    @Throws(IOException::class)
    private fun ensureOpen() {
        if (closed) {
            throw IOException("PAG preview session is closed.")
        }
    }

    private fun nextWritableImage(): BufferedImage {
        val images = streamingImages
            ?: return BufferedImage(info.width, info.height, BufferedImage.TYPE_INT_ARGB)
        val image = images[nextStreamingImage]
        nextStreamingImage = (nextStreamingImage + 1) % images.size
        return image
    }

    enum class PreloadResult {
        DECODED,
        ALREADY_CACHED,
        UNAVAILABLE
    }

    companion object {
        private const val DEFAULT_FRAME_CACHE_BUDGET_BYTES = 96L * 1024L * 1024L
        private const val STREAMING_IMAGE_BUFFER_COUNT = 3

        @JvmStatic
        @Throws(IOException::class)
        fun open(
            nativeLibrary: PagNativeLibrary,
            bytes: ByteArray,
            filePath: Path,
            maxFrameRate: Float,
            scale: Float
        ): PagPreviewSession {
            val fileHandle = nativeLibrary.loadFile(bytes, filePath.toString())
            if (fileHandle == 0L) {
                throw IOException("libpag could not load this PAG file.")
            }
            val compositionWidth = nativeLibrary.compositionWidth(fileHandle)
            val compositionHeight = nativeLibrary.compositionHeight(fileHandle)

            val decoderHandle = nativeLibrary.createDecoder(fileHandle, maxFrameRate, scale)
            if (decoderHandle == 0L) {
                nativeLibrary.release(fileHandle)
                throw IOException("libpag could not create a decoder for this PAG file.")
            }

            val info = PagPreviewInfo(
                nativeLibrary.width(decoderHandle),
                nativeLibrary.height(decoderHandle),
                nativeLibrary.frameCount(decoderHandle),
                nativeLibrary.frameRate(decoderHandle),
                compositionWidth,
                compositionHeight
            )
            if (!info.isRenderable()) {
                nativeLibrary.release(decoderHandle)
                nativeLibrary.release(fileHandle)
                throw IOException("libpag returned empty preview dimensions.")
            }

            return PagPreviewSession(nativeLibrary, fileHandle, decoderHandle, info)
        }

        private fun shouldCacheFrames(info: PagPreviewInfo): Boolean {
            val frameBytes = info.width.toLong() * info.height * Int.SIZE_BYTES
            val budgetBytes = java.lang.Long.getLong("pag.viewer.frame.cache.bytes", DEFAULT_FRAME_CACHE_BUDGET_BYTES)
            return frameBytes > 0 &&
                budgetBytes > 0 &&
                info.frameCount <= budgetBytes / frameBytes
        }

        private fun createStreamingImages(info: PagPreviewInfo): Array<BufferedImage> =
            Array(STREAMING_IMAGE_BUFFER_COUNT) {
                BufferedImage(info.width, info.height, BufferedImage.TYPE_INT_ARGB)
            }
    }
}
