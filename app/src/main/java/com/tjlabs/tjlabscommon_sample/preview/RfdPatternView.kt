package com.tjlabs.tjlabscommon_sample.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class RfdPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 36f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 22f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val palette = listOf(
        "#E53935", "#3949AB", "#43A047", "#FB8C00", "#8E24AA",
        "#00897B", "#6D4C41", "#C0CA33", "#1E88E5", "#D81B60"
    ).map { Color.parseColor(it) }

    private val rssiMin = -100f
    private val rssiMax = -30f

    private var samples: List<RfdSample> = emptyList()

    fun submit(samples: List<RfdSample>) {
        this.samples = samples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.isEmpty()) {
            canvas.drawText("No RFD data yet", 24f, height / 2f, emptyPaint)
            return
        }

        val padding = 36f
        val plotLeft = padding
        val plotRight = width - padding
        val plotTop = padding
        val plotBottom = height - padding

        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, axisPaint)
        canvas.drawText("${rssiMax.toInt()} dBm", plotLeft + 4f, plotTop + 22f, labelPaint)
        canvas.drawText("${rssiMin.toInt()} dBm", plotLeft + 4f, plotBottom - 6f, labelPaint)

        val firstTs = samples.first().mobileTime
        val lastTs = samples.last().mobileTime
        val tsRange = (lastTs - firstTs).coerceAtLeast(1L).toFloat()

        val topBeacons = samples.flatMap { it.rfs.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, list) -> list.max() }
            .entries
            .sortedByDescending { it.value }
            .take(palette.size)
            .map { it.key }

        if (topBeacons.isEmpty()) {
            canvas.drawText("No RFD entries", plotLeft + 8f, height / 2f, emptyPaint)
            return
        }

        val rssiRange = (rssiMax - rssiMin)
        topBeacons.forEachIndexed { idx, beacon ->
            val color = palette[idx % palette.size]
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val path = Path()
            var started = false
            samples.forEach { sample ->
                val rssi = sample.rfs[beacon] ?: return@forEach
                val xRatio = if (tsRange == 0f) 0f else (sample.mobileTime - firstTs).toFloat() / tsRange
                val x = plotLeft + xRatio * (plotRight - plotLeft)
                val yRatio = ((rssi - rssiMin) / rssiRange).coerceIn(0f, 1f)
                val y = plotBottom - yRatio * (plotBottom - plotTop)
                if (!started) {
                    path.moveTo(x, y); started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)
            val legendY = plotTop + 22f + (idx + 1) * 22f
            canvas.drawText(beacon, plotRight - 240f, legendY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = 22f
            })
        }
    }
}
