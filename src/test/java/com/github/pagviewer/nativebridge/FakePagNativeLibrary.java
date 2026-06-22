package com.github.pagviewer.nativebridge;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

final class FakePagNativeLibrary implements PagNativeLibrary {
    private final AtomicLong handles = new AtomicLong(1);
    private final int width;
    private final int height;
    private final int frames;
    private final float frameRate;
    private final byte[] pixels;
    private final boolean[] changedFrames;
    private final AtomicInteger readFrameCalls = new AtomicInteger();

    FakePagNativeLibrary(int width, int height, int frames, float frameRate, byte[] pixels) {
        this(width, height, frames, frameRate, pixels, null);
    }

    FakePagNativeLibrary(int width, int height, int frames, float frameRate, byte[] pixels, boolean[] changedFrames) {
        this.width = width;
        this.height = height;
        this.frames = frames;
        this.frameRate = frameRate;
        this.pixels = pixels;
        this.changedFrames = changedFrames;
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
    public boolean checkFrameChanged(long decoder, int frameIndex) {
        return changedFrames == null || changedFrames[Math.floorMod(frameIndex, changedFrames.length)];
    }

    @Override
    public boolean readFrame(long decoder, int frameIndex, ByteBuffer destination, int rowBytes) {
        readFrameCalls.incrementAndGet();
        destination.clear();
        destination.put(pixels);
        destination.flip();
        return true;
    }

    int readFrameCalls() {
        return readFrameCalls.get();
    }

    @Override
    public void release(long handle) {
    }
}
