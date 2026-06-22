package com.github.pagviewer.editor;

import com.intellij.ui.JBColor;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class PagCanvas extends JComponent {
    private BufferedImage image;

    PagCanvas() {
        setOpaque(true);
        setBackground(JBColor.PanelBackground);
        setMinimumSize(new Dimension(240, 180));
        setPreferredSize(new Dimension(640, 420));
    }

    void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    BufferedImage currentImage() {
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (image == null) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(
                    getWidth() / (double) image.getWidth(),
                    getHeight() / (double) image.getHeight()
            );
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            g.drawImage(image, x, y, width, height, null);
        } finally {
            g.dispose();
        }
    }
}
