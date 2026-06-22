package com.github.pagviewer.nativebridge;

import java.nio.ByteBuffer;

public interface PagNativeLibrary {
    int COLOR_TYPE_BGRA_8888 = 3;
    int ALPHA_TYPE_UNPREMULTIPLIED = 3;

    long loadFile(byte[] bytes, String filePath);

    long createDecoder(long composition, float maxFrameRate, float scale);

    int compositionWidth(long composition);

    int compositionHeight(long composition);

    int width(long decoder);

    int height(long decoder);

    int frameCount(long decoder);

    float frameRate(long decoder);

    boolean readFrame(long decoder, int frameIndex, ByteBuffer destination, int rowBytes);

    void release(long handle);
}
