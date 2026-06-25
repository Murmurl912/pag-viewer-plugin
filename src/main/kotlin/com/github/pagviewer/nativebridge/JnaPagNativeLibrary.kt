package com.github.pagviewer.nativebridge

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.nio.ByteBuffer
import java.nio.file.Path

class JnaPagNativeLibrary private constructor(private val libPag: LibPagC) : PagNativeLibrary {

    override fun loadFile(bytes: ByteArray, filePath: String): Long {
        if (bytes.isEmpty()) {
            return 0
        }
        val memory = Memory(bytes.size.toLong())
        memory.write(0L, bytes, 0, bytes.size)
        val file = libPag.pag_file_load(memory, NativeLong(bytes.size.toLong()), filePath)
        return pointerValue(file)
    }

    override fun createDecoder(composition: Long, maxFrameRate: Float, scale: Float): Long {
        val decoder = libPag.pag_decoder_create(pointer(composition), maxFrameRate, scale)
        return pointerValue(decoder)
    }

    override fun compositionWidth(composition: Long): Int = libPag.pag_composition_get_width(pointer(composition))

    override fun compositionHeight(composition: Long): Int = libPag.pag_composition_get_height(pointer(composition))

    override fun width(decoder: Long): Int = libPag.pag_decoder_get_width(pointer(decoder))

    override fun height(decoder: Long): Int = libPag.pag_decoder_get_height(pointer(decoder))

    override fun frameCount(decoder: Long): Int = libPag.pag_decoder_get_num_frames(pointer(decoder))

    override fun frameRate(decoder: Long): Float = libPag.pag_decoder_get_frame_rate(pointer(decoder))

    override fun checkFrameChanged(decoder: Long, frameIndex: Int): Boolean =
        libPag.pag_decoder_check_frame_changed(pointer(decoder), frameIndex).toInt() != 0

    override fun readFrame(decoder: Long, frameIndex: Int, destination: ByteBuffer, rowBytes: Int): Boolean {
        val result = libPag.pag_decoder_read_frame(
            pointer(decoder),
            frameIndex,
            destination,
            NativeLong(rowBytes.toLong()),
            PagNativeLibrary.COLOR_TYPE_BGRA_8888,
            PagNativeLibrary.ALPHA_TYPE_UNPREMULTIPLIED
        )
        return result.toInt() != 0
    }

    override fun release(handle: Long) {
        if (handle != 0L) {
            libPag.pag_release(pointer(handle))
        }
    }

    private interface LibPagC : Library {
        fun pag_file_load(bytes: Pointer?, length: NativeLong, filePath: String): Pointer?

        fun pag_decoder_create(composition: Pointer?, maxFrameRate: Float, scale: Float): Pointer?

        fun pag_composition_get_width(composition: Pointer?): Int

        fun pag_composition_get_height(composition: Pointer?): Int

        fun pag_decoder_get_width(decoder: Pointer?): Int

        fun pag_decoder_get_height(decoder: Pointer?): Int

        fun pag_decoder_get_num_frames(decoder: Pointer?): Int

        fun pag_decoder_get_frame_rate(decoder: Pointer?): Float

        fun pag_decoder_check_frame_changed(decoder: Pointer?, index: Int): Byte

        fun pag_decoder_read_frame(
            decoder: Pointer?,
            index: Int,
            pixels: ByteBuffer,
            rowBytes: NativeLong,
            colorType: Int,
            alphaType: Int
        ): Byte

        fun pag_release(`object`: Pointer?)
    }

    companion object {
        @JvmStatic
        fun load(libraryPath: Path): JnaPagNativeLibrary =
            JnaPagNativeLibrary(Native.load(libraryPath.toAbsolutePath().toString(), LibPagC::class.java))

        private fun pointer(value: Long): Pointer? = if (value == 0L) null else Pointer(value)

        private fun pointerValue(pointer: Pointer?): Long = if (pointer == null) 0 else Pointer.nativeValue(pointer)
    }
}
