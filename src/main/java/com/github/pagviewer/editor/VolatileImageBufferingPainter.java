package com.github.pagviewer.editor;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.image.VolatileImage;
import java.util.function.Consumer;

final class VolatileImageBufferingPainter {
    private static final int MAX_REPAINT_ATTEMPTS = 3;

    private final int transparency;
    private VolatileImage buffer;

    VolatileImageBufferingPainter(int transparency) {
        this.transparency = transparency;
    }

    void paint(Graphics graphics, Dimension size, Consumer<Graphics2D> painter) {
        if (!(graphics instanceof Graphics2D graphics2D) || size.width <= 0 || size.height <= 0) {
            return;
        }

        GraphicsConfiguration configuration = graphics2D.getDeviceConfiguration();
        if (configuration == null) {
            paintDirect(graphics2D, painter);
            return;
        }

        ensureBuffer(configuration, size);
        if (buffer == null) {
            paintDirect(graphics2D, painter);
            return;
        }

        for (int attempt = 0; attempt < MAX_REPAINT_ATTEMPTS; attempt++) {
            int validationResult = buffer.validate(configuration);
            if (validationResult == VolatileImage.IMAGE_INCOMPATIBLE) {
                flush();
                ensureBuffer(configuration, size);
                if (buffer == null) {
                    paintDirect(graphics2D, painter);
                    return;
                }
            }

            Graphics2D bufferGraphics = buffer.createGraphics();
            try {
                painter.accept(bufferGraphics);
            } finally {
                bufferGraphics.dispose();
            }

            graphics2D.drawImage(buffer, 0, 0, null);
            if (!buffer.contentsLost()) {
                return;
            }
        }

        paintDirect(graphics2D, painter);
    }

    void flush() {
        if (buffer != null) {
            buffer.flush();
            buffer = null;
        }
    }

    private void ensureBuffer(GraphicsConfiguration configuration, Dimension size) {
        if (buffer != null && buffer.getWidth() == size.width && buffer.getHeight() == size.height) {
            return;
        }
        flush();
        try {
            buffer = configuration.createCompatibleVolatileImage(
                    size.width,
                    size.height,
                    new ImageCapabilities(true),
                    transparency
            );
        } catch (Exception exception) {
            buffer = configuration.createCompatibleVolatileImage(size.width, size.height, transparency);
        }
    }

    private static void paintDirect(Graphics2D graphics, Consumer<Graphics2D> painter) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            painter.accept(copy);
        } finally {
            copy.dispose();
        }
    }
}
