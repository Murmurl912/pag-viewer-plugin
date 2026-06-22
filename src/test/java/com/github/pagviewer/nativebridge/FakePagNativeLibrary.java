package com.github.pagviewer.nativebridge;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

final class FakePagNativeLibrary implements PagNativeLibrary {
    private final AtomicLong handles = new AtomicLong(1);
    private final int width;
    private final int height;
    private final int frames;
    private final float frameRate;
    private final byte[] pixels;

    FakePagNativeLibrary(int width, int height, int frames, float frameRate, byte[] pixels) {
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.frameRate = frameRate;
        this.pixels = pixels;
    }

    @Override
    public long loadFile(byte[] bytes, String filePath) {
        return bytes.length == 0 ? 0 : handles.getAndIncrement();
    }

    @Override
    public long createDecoder(long composition, float maxFrameRate, float scale) {
        return composition == 0 ? 0 : handles.getAndIncrement();
    }

    @Override
    public int compositionWidth(long composition) {
        return width;
    }

    @Override
    public int compositionHeight(long composition) {
        return height;
    }

    @Override
    public int width(long decoder) {
        return width;
    }

    @Override
    public int height(long decoder) {
        return height;
    }

    @Override
    public int frameCount(long decoder) {
        return frames;
    }

    @Override
    public float frameRate(long decoder) {
        return frameRate;
    }

    @Override
    public boolean readFrame(long decoder, int frameIndex, ByteBuffer destination, int rowBytes) {
        destination.clear();
        destination.put(pixels);
        destination.flip();
        return true;
    }

    @Override
    public void release(long handle) {
    }
}
