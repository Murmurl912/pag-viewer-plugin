package com.github.pagviewer.editor

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JSlider
import javax.swing.plaf.basic.BasicSliderUI

internal class RoundThumbSliderUI(slider: JSlider) : BasicSliderUI(slider) {
    override fun getThumbSize(): Dimension = JBUI.size(THUMB_SIZE, THUMB_SIZE)

    override fun paintTrack(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val height = JBUI.scale(TRACK_HEIGHT)
            val y = trackRect.y + (trackRect.height - height) / 2
            val arc = height
            val trackColor = JBColor.namedColor("Slider.trackColor", JBColor(Color(0xC9CED6), Color(0x6D737C)))
            val progressColor = JBColor.namedColor("Slider.thumbColor", JBColor(Color(0x4C8DFF), Color(0x8AB4FF)))
            graphics2D.color = trackColor
            graphics2D.fillRoundRect(trackRect.x, y, trackRect.width, height, arc, arc)
            graphics2D.color = progressColor
            val progressWidth = maxOf(0, thumbRect.x + thumbRect.width / 2 - trackRect.x)
            graphics2D.fillRoundRect(trackRect.x, y, progressWidth, height, arc, arc)
        } finally {
            graphics2D.dispose()
        }
    }

    override fun paintThumb(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val thumbColor = JBColor.namedColor("Slider.thumbColor", JBColor(Color(0x4C8DFF), Color(0xAEB4BE)))
            val borderColor = JBColor.namedColor("Slider.thumbBorderColor", JBColor(Color(0xFFFFFF), Color(0x8C939D)))
            graphics2D.color = thumbColor
            graphics2D.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height)
            graphics2D.color = borderColor
            graphics2D.drawOval(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1)
        } finally {
            graphics2D.dispose()
        }
    }

    companion object {
        private const val THUMB_SIZE = 14
        private const val TRACK_HEIGHT = 3
    }
}
