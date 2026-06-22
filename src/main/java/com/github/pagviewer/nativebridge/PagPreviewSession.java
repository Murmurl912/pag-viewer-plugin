package com.github.pagviewer.nativebridge;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

public final class PagPreviewSession implements AutoCloseable {
    private static final long DEFAULT_FRAME_CACHE_BUDGET_BYTES = 96L * 1024L * 1024L;
    private static final int STREAMING_IMAGE_BUFFER_COUNT = 3;

    private final PagNativeLibrary nativeLibrary;
    private final long fileHandle;
    private final long decoderHandle;
    private final PagPreviewInfo info;
    private final int rowBytes;
    private final ByteBuffer pixelBuffer;
    private final BufferedImage[] frameCache;
    private final BufferedImage[] streamingImages;
    private BufferedImage lastNativeDecodedImage;
    private BufferedImage lastReturnedImage;
    private int nextStreamingImage;
    private boolean closed;

    private PagPreviewSession(PagNativeLibrary nativeLibrary, long fileHandle, long decoderHandle, PagPreviewInfo info) throws IOException {
        this.nativeLibrary = nativeLibrary;
        this.fileHandle = fileHandle;
        this.decoderHandle = decoderHandle;
        this.info = info;
        this.rowBytes = Math.multiplyExact(info.width(), Integer.BYTES);
        this.pixelBuffer = ByteBuffer
                .allocateDirect(Math.toIntExact((long) rowBytes * info.height()))
                .order(ByteOrder.LITTLE_ENDIAN);
        this.frameCache = shouldCacheFrames(info) ? new BufferedImage[info.frameCount()] : null;
        this.streamingImages = frameCache == null ? createStreamingImages(info) : null;
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

    public synchronized PreloadResult preloadFrame(int frameIndex) throws IOException {
        ensureOpen();
        if (frameCache == null) {
            return PreloadResult.UNAVAILABLE;
        }
        int safeFrame = Math.floorMod(frameIndex, info.frameCount());
        if (frameCache[safeFrame] != null) {
            return PreloadResult.ALREADY_CACHED;
        }
        readFrame(safeFrame);
        return PreloadResult.DECODED;
    }

    public synchronized BufferedImage readFrame(int frameIndex) throws IOException {
        ensureOpen();
        int safeFrame = Math.floorMod(frameIndex, info.frameCount());
        if (frameCache != null && frameCache[safeFrame] != null) {
            lastReturnedImage = frameCache[safeFrame];
            return lastReturnedImage;
        }
        if (lastNativeDecodedImage != null
                && lastReturnedImage == lastNativeDecodedImage
                && !nativeLibrary.checkFrameChanged(decoderHandle, safeFrame)) {
            if (frameCache != null) {
                frameCache[safeFrame] = lastNativeDecodedImage;
            }
            lastReturnedImage = lastNativeDecodedImage;
            return lastReturnedImage;
        }

        BufferedImage image = nextWritableImage();
        pixelBuffer.clear();
        boolean ok = nativeLibrary.readFrame(decoderHandle, safeFrame, pixelBuffer, rowBytes);
        if (!ok) {
            throw new IOException("libpag failed to decode frame " + safeFrame + ".");
        }

        pixelBuffer.position(0);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        pixelBuffer.asIntBuffer().get(pixels, 0, pixels.length);
        if (frameCache != null) {
            frameCache[safeFrame] = image;
        }
        lastNativeDecodedImage = image;
        lastReturnedImage = image;
        return image;
    }

    @Override
    public synchronized void close() {
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

    private static boolean shouldCacheFrames(PagPreviewInfo info) {
        long frameBytes = (long) info.width() * info.height() * Integer.BYTES;
        long budgetBytes = Long.getLong("pag.viewer.frame.cache.bytes", DEFAULT_FRAME_CACHE_BUDGET_BYTES);
        return frameBytes > 0
                && budgetBytes > 0
                && info.frameCount() <= budgetBytes / frameBytes;
    }

    private BufferedImage nextWritableImage() {
        if (streamingImages == null) {
            return new BufferedImage(info.width(), info.height(), BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage image = streamingImages[nextStreamingImage];
        nextStreamingImage = (nextStreamingImage + 1) % streamingImages.length;
        return image;
    }

    private static BufferedImage[] createStreamingImages(PagPreviewInfo info) {
        BufferedImage[] images = new BufferedImage[STREAMING_IMAGE_BUFFER_COUNT];
        for (int index = 0; index < images.length; index++) {
            images[index] = new BufferedImage(info.width(), info.height(), BufferedImage.TYPE_INT_ARGB);
        }
        return images;
    }

    public enum PreloadResult {
        DECODED,
        ALREADY_CACHED,
        UNAVAILABLE
    }
}
