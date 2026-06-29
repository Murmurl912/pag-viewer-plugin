package com.github.pagviewer.editor

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.UIManager

internal class ToggleToolbarIcon private constructor(
    private val grid: Boolean,
    private val active: Boolean
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(SIZE)

    override fun getIconHeight(): Int = JBUI.scale(SIZE)

    override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val size = iconWidth
            val inset = JBUI.scale(2)
            if (active) {
                graphics2D.color = ACTIVE_FILL
                graphics2D.fillRoundRect(x + inset, y + inset, size - inset * 2, size - inset * 2, JBUI.scale(6), JBUI.scale(6))
                graphics2D.color = ACTIVE_STROKE
                graphics2D.drawRoundRect(x + inset, y + inset, size - inset * 2 - 1, size - inset * 2 - 1, JBUI.scale(6), JBUI.scale(6))
            }
            if (grid) {
                paintGrid(component, graphics2D, x, y, size)
            } else {
                paintCheckerboard(component, graphics2D, x, y, size)
            }
        } finally {
            graphics2D.dispose()
        }
    }

    private fun paintCheckerboard(component: Component, graphics2D: Graphics2D, x: Int, y: Int, size: Int) {
        val square = JBUI.scale(3)
        val boardSize = square * 4
        val left = x + (size - boardSize) / 2
        val top = y + (size - boardSize) / 2
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                graphics2D.color = if (((row + column) and 1) == 0) CHESS_LIGHT else CHESS_DARK
                graphics2D.fillRect(left + column * square, top + row * square, square, square)
            }
        }
        graphics2D.color = withAlpha(foreground(component), if (active) 210 else 150)
        graphics2D.drawRect(left, top, boardSize - 1, boardSize - 1)
    }

    private fun paintGrid(component: Component, graphics2D: Graphics2D, x: Int, y: Int, size: Int) {
        val left = x + JBUI.scale(5)
        val top = y + JBUI.scale(5)
        val gridSize = size - JBUI.scale(10)
        val step = gridSize / 3
        graphics2D.stroke = BasicStroke(JBUI.scale(135) / 100f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics2D.color = withAlpha(foreground(component), if (active) 230 else 175)
        for (index in 0..3) {
            val offset = index * step
            graphics2D.drawLine(left + offset, top, left + offset, top + gridSize)
            graphics2D.drawLine(left, top + offset, left + gridSize, top + offset)
        }
    }

    companion object {
        private const val SIZE = 22
        private val ACTIVE_FILL = Color(0x4C, 0x8D, 0xFF, 72)
        private val ACTIVE_STROKE = Color(0x75, 0xA8, 0xFF, 210)
        private val CHESS_LIGHT = Color(0xD9, 0xDE, 0xE7, 230)
        private val CHESS_DARK = Color(0x72, 0x7B, 0x8A, 230)

        fun checkerboard(active: Boolean): ToggleToolbarIcon = ToggleToolbarIcon(false, active)

        fun grid(active: Boolean): ToggleToolbarIcon = ToggleToolbarIcon(true, active)

        private fun foreground(component: Component): Color {
            val color = component.foreground
            if (color != null) {
                return color
            }
            return UIManager.getColor("Label.foreground") ?: Color.GRAY
        }

        private fun withAlpha(color: Color, alpha: Int): Color =
            Color(color.red, color.green, color.blue, alpha)
    }
}
