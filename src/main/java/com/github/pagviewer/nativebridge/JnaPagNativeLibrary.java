package com.github.pagviewer.nativebridge;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class JnaPagNativeLibrary implements PagNativeLibrary {
    private final LibPagC libPag;

    private JnaPagNativeLibrary(LibPagC libPag) {
        this.libPag = libPag;
    }

    public static JnaPagNativeLibrary load(Path libraryPath) {
        return new JnaPagNativeLibrary(Native.load(libraryPath.toAbsolutePath().toString(), LibPagC.class));
    }

    @Override
    public long loadFile(byte[] bytes, String filePath) {
        if (bytes.length == 0) {
            return 0;
        }
        Memory memory = new Memory(bytes.length);
        memory.write(0, bytes, 0, bytes.length);
        Pointer file = libPag.pag_file_load(memory, new NativeLong(bytes.length), filePath);
        return pointerValue(file);
    }

    @Override
    public long createDecoder(long composition, float maxFrameRate, float scale) {
        Pointer decoder = libPag.pag_decoder_create(pointer(composition), maxFrameRate, scale);
        return pointerValue(decoder);
    }

    @Override
    public int compositionWidth(long composition) {
        return libPag.pag_composition_get_width(pointer(composition));
    }

    @Override
    public int compositionHeight(long composition) {
        return libPag.pag_composition_get_height(pointer(composition));
    }

    @Override
    public int width(long decoder) {
        return libPag.pag_decoder_get_width(pointer(decoder));
    }

    @Override
    public int height(long decoder) {
        return libPag.pag_decoder_get_height(pointer(decoder));
    }

    @Override
    public int frameCount(long decoder) {
        return libPag.pag_decoder_get_num_frames(pointer(decoder));
    }

    @Override
    public float frameRate(long decoder) {
        return libPag.pag_decoder_get_frame_rate(pointer(decoder));
    }

    @Override
    public boolean checkFrameChanged(long decoder, int frameIndex) {
        return libPag.pag_decoder_check_frame_changed(pointer(decoder), frameIndex) != 0;
    }

    @Override
    public boolean readFrame(long decoder, int frameIndex, ByteBuffer destination, int rowBytes) {
        byte result = libPag.pag_decoder_read_frame(
                pointer(decoder),
                frameIndex,
                destination,
                new NativeLong(rowBytes),
                COLOR_TYPE_BGRA_8888,
                ALPHA_TYPE_UNPREMULTIPLIED
        );
        return result != 0;
    }

    @Override
    public void release(long handle) {
        if (handle != 0) {
            libPag.pag_release(pointer(handle));
        }
    }

    private static Pointer pointer(long value) {
        return value == 0 ? null : new Pointer(value);
    }

    private static long pointerValue(Pointer pointer) {
        return pointer == null ? 0 : Pointer.nativeValue(pointer);
    }

    private interface LibPagC extends Library {
        Pointer pag_file_load(Pointer bytes, NativeLong length, String filePath);

        Pointer pag_decoder_create(Pointer composition, float maxFrameRate, float scale);

        int pag_composition_get_width(Pointer composition);

        int pag_composition_get_height(Pointer composition);

        int pag_decoder_get_width(Pointer decoder);

        int pag_decoder_get_height(Pointer decoder);

        int pag_decoder_get_num_frames(Pointer decoder);

        float pag_decoder_get_frame_rate(Pointer decoder);

        byte pag_decoder_check_frame_changed(Pointer decoder, int index);

        byte pag_decoder_read_frame(
                Pointer decoder,
                int index,
                ByteBuffer pixels,
                NativeLong rowBytes,
                int colorType,
                int alphaType
        );

        void pag_release(Pointer object);
    }
}
