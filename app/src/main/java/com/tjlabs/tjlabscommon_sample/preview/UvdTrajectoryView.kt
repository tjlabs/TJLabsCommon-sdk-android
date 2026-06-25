package com.tjlabs.tjlabscommon_sample.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class UvdTrajectoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2E7DEF")
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E5BB8")
    }
    private val pointStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.5f
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3DDC84")
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E64A19")
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 36f
    }

    private var samples: List<UvdSample> = emptyList()

    fun submit(samples: List<UvdSample>) {
        this.samples = samples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.size < 2) {
            canvas.drawText("No UVD data yet", 24f, height / 2f, emptyPaint)
            return
        }

        val xs = FloatArray(samples.size)
        val ys = FloatArray(samples.size)
        var x = 0f
        var y = 0f
        var minX = 0f
        var maxX = 0f
        var minY = 0f
        var maxY = 0f
        for ((i, s) in samples.withIndex()) {
            val rad = Math.toRadians(s.heading.toDouble())
            x += (s.length * cos(rad)).toFloat()
            y += (s.length * sin(rad)).toFloat()
            xs[i] = x
            ys[i] = y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val padding = 32f
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)
        val scaleX = (width - padding * 2) / rangeX
        val scaleY = (height - padding * 2) / rangeY
        val scale = scaleX.coerceAtMost(scaleY)
        val offsetX = padding + (width - padding * 2 - rangeX * scale) / 2f
        val offsetY = padding + (height - padding * 2 - rangeY * scale) / 2f

        val path = Path()
        for (i in xs.indices) {
            val px = offsetX + (xs[i] - minX) * scale
            val py = height - (offsetY + (ys[i] - minY) * scale)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, pathPaint)

        // Draw intermediate index points so every sample is visible.
        val pointRadius = if (xs.size > 200) 2.5f else 4f
        for (i in 1 until xs.lastIndex) {
            val px = offsetX + (xs[i] - minX) * scale
            val py = height - (offsetY + (ys[i] - minY) * scale)
            canvas.drawCircle(px, py, pointRadius, pointPaint)
            canvas.drawCircle(px, py, pointRadius, pointStrokePaint)
        }

        val startX = offsetX + (xs.first() - minX) * scale
        val startY = height - (offsetY + (ys.first() - minY) * scale)
        canvas.drawCircle(startX, startY, 10f, startPaint)
        val endX = offsetX + (xs.last() - minX) * scale
        val endY = height - (offsetY + (ys.last() - minY) * scale)
        canvas.drawCircle(endX, endY, 10f, endPaint)
    }
}
