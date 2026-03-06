package com.spatium.deamon.db.temi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CountdownRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        color = 0x1AFFFFFF.toInt()
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF1152D4.toInt()
    }

    private val rect = RectF()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = 24f
        rect.set(padding, padding, width - padding, height - padding)

        canvas.drawArc(rect, 0f, 360f, false, bgPaint)
        val sweepAngle = 360f * progress
        canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
    }
}
