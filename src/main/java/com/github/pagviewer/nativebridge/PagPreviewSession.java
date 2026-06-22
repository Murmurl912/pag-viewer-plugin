package com.github.pagviewer.nativebridge;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Path;

public final class PagPreviewSession implements AutoCloseable {
    private final PagNativeLibrary nativeLibrary;
    private final long fileHandle;
    private final long decoderHandle;
    private final PagPreviewInfo info;
    private boolean closed;

    private PagPreviewSession(PagNativeLibrary nativeLibrary, long fileHandle, long decoderHandle, PagPreviewInfo info) {
        this.nativeLibrary = nativeLibrary;
        this.fileHandle = fileHandle;
        this.decoderHandle = decoderHandle;
        this.info = info;
    }

    public static PagPreviewSession open(
            PagNativeLibrary nativeLibrary,
            byte[] bytes,
            Path filePath,
            float maxFrameRate,
            float scale
    ) throws IOException {
        long fileHandle = nativeLibrary.loadFile(bytes, filePath.toString());
        if (fileHandle == 0) {
            throw new IOException("libpag could not load this PAG file.");
        }
        int compositionWidth = nativeLibrary.compositionWidth(fileHandle);
        int compositionHeight = nativeLibrary.compositionHeight(fileHandle);

        long decoderHandle = nativeLibrary.createDecoder(fileHandle, maxFrameRate, scale);
        if (decoderHandle == 0) {
            nativeLibrary.release(fileHandle);
            throw new IOException("libpag could not create a decoder for this PAG file.");
        }

        PagPreviewInfo info = new PagPreviewInfo(
                nativeLibrary.width(decoderHandle),
                nativeLibrary.height(decoderHandle),
                nativeLibrary.frameCount(decoderHandle),
                nativeLibrary.frameRate(decoderHandle),
                compositionWidth,
                compositionHeight
        );
        if (!info.isRenderable()) {
            nativeLibrary.release(decoderHandle);
            nativeLibrary.release(fileHandle);
            throw new IOException("libpag returned empty preview dimensions.");
        }

        return new PagPreviewSession(nativeLibrary, fileHandle, decoderHandle, info);
    }

    public PagPreviewInfo info() {
        return info;
    }

    public BufferedImage readFrame(int frameIndex) throws IOException {
        ensureOpen();
        int safeFrame = Math.floorMod(frameIndex, info.frameCount());
        int rowBytes = info.width() * Integer.BYTES;
        ByteBuffer buffer = ByteBuffer
                .allocateDirect(rowBytes * info.height())
                .order(ByteOrder.LITTLE_ENDIAN);
        boolean ok = nativeLibrary.readFrame(decoderHandle, safeFrame, buffer, rowBytes);
        if (!ok) {
            throw new IOException("libpag failed to decode frame " + safeFrame + ".");
        }

        int[] argb = new int[info.width() * info.height()];
        buffer.position(0);
        IntBuffer intBuffer = buffer.asIntBuffer();
        intBuffer.get(argb);

        BufferedImage image = new BufferedImage(info.width(), info.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, info.width(), info.height(), argb, 0, info.width());
        return image;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        nativeLibrary.release(decoderHandle);
        nativeLibrary.release(fileHandle);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("PAG preview session is closed.");
        }
    }
}
