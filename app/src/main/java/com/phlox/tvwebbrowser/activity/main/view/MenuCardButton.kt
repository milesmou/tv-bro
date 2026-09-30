package com.phlox.tvwebbrowser.activity.main.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.phlox.tvwebbrowser.R

/** Vector icon and live text keep the menu sharp at every screen density. */
class MenuCardButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageButton(context, attrs) {
    private val label: String
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.day_night_text_color_contrast)
        textAlign = Paint.Align.LEFT
        textSize = 14 * resources.displayMetrics.scaledDensity
    }

    init {
        val attributes = context.obtainStyledAttributes(attrs, R.styleable.MenuCardButton)
        label = attributes.getString(R.styleable.MenuCardButton_cardLabel).orEmpty()
        attributes.recycle()
        setPadding(0, 0, 0, 0)
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        labelPaint.alpha = if (isEnabled) 255 else 100
        val iconSize = 24 * density
        val gap = 6 * density
        val availableWidth = (width - 16 * density - iconSize - gap).coerceAtLeast(1f)
        val originalSize = 14 * resources.displayMetrics.scaledDensity
        labelPaint.textSize = originalSize
        val textWidth = labelPaint.measureText(label)
        if (textWidth > availableWidth) labelPaint.textSize = originalSize * availableWidth / textWidth
        val contentWidth = iconSize + gap + labelPaint.measureText(label)
        val start = (width - contentWidth) / 2f
        drawable?.let { icon ->
            val top = (height - iconSize) / 2f
            icon.setBounds(start.toInt(), top.toInt(), (start + iconSize).toInt(), (top + iconSize).toInt())
            icon.draw(canvas)
        }
        val baseline = height / 2f - (labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f
        canvas.drawText(label, start + iconSize + gap, baseline, labelPaint)
    }
}
