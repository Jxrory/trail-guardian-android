package com.trailguardian.core.data.tile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ContourRenderer {
    private val tileSize = 256

    fun generate(terrainBytes: ByteArray, zoom: Int): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(terrainBytes, 0, terrainBytes.size) ?: return null
        val scaled = if (bitmap.width == tileSize && bitmap.height == tileSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, tileSize, tileSize, true).also { bitmap.recycle() }
        }
        val pixels = IntArray(tileSize * tileSize)
        scaled.getPixels(pixels, 0, tileSize, 0, 0, tileSize, tileSize)
        scaled.recycle()

        val height = FloatArray(tileSize * tileSize)
        var minHeight = Float.POSITIVE_INFINITY
        var maxHeight = Float.NEGATIVE_INFINITY
        for (index in height.indices) {
            val pixel = pixels[index]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val elevation = r * 256 + g + b / 256f - 32768f
            height[index] = elevation
            if (elevation < -50f) continue
            if (elevation < minHeight) minHeight = elevation
            if (elevation > maxHeight) maxHeight = elevation
        }
        if (!minHeight.isFinite() || !maxHeight.isFinite()) return null

        var interval = contourIntervalForZoom(zoom).toFloat()
        while ((maxHeight - minHeight) / interval > 36f) interval *= 2f
        if (interval <= 0f) return null

        val firstLevel = ceil(max(0f, minHeight) / interval).toInt() * interval
        val step = if (zoom >= 14) 1 else 2
        val zoomScale = 1f
        val width = max(0.65f, 1.3f / zoomScale)
        val relief = maxHeight - minHeight
        val majorInterval = interval * if (relief > 800f) 5f else 2f

        val overlay = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb((0.62f * 255).toInt(), 15, 23, 42)
            strokeWidth = width + max(1f, 2f / zoomScale)
        }
        val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        var tileLabel: LabelCandidate? = null
        var tileLabelLevel = 0
        var tileLabelCrossings = 0

        var level = firstLevel
        while (level <= maxHeight) {
            val isMajor = interval > 0f && level % majorInterval < 0.001f
            val path = Path()
            var bestCandidate: SegmentCandidate? = null
            var longestSegment = 0f
            var hasContourLine = false
            var crossingCount = 0

            var y = 0
            while (y < tileSize - step) {
                var x = 0
                while (x < tileSize - step) {
                    val index = y * tileSize + x
                    val topLeft = height[index]
                    val topRight = height[index + step]
                    val bottomRight = height[index + tileSize * step + step]
                    val bottomLeft = height[index + tileSize * step]

                    val intersections = arrayListOf<FloatPoint>()
                    if ((topLeft > level) != (topRight > level)) {
                        intersections.add(
                            interpolateEdge(x.toFloat(), y.toFloat(), topLeft, (x + step).toFloat(), y.toFloat(), topRight, level),
                        )
                    }
                    if ((topRight > level) != (bottomRight > level)) {
                        intersections.add(
                            interpolateEdge(
                                (x + step).toFloat(), y.toFloat(), topRight,
                                (x + step).toFloat(), (y + step).toFloat(), bottomRight, level,
                            ),
                        )
                    }
                    if ((bottomRight > level) != (bottomLeft > level)) {
                        intersections.add(
                            interpolateEdge(
                                (x + step).toFloat(), (y + step).toFloat(), bottomRight,
                                x.toFloat(), (y + step).toFloat(), bottomLeft, level,
                            ),
                        )
                    }
                    if ((bottomLeft > level) != (topLeft > level)) {
                        intersections.add(
                            interpolateEdge(
                                x.toFloat(), (y + step).toFloat(), bottomLeft,
                                x.toFloat(), y.toFloat(), topLeft, level,
                            ),
                        )
                    }

                    if (intersections.size == 2) {
                        val start = intersections[0]
                        val end = intersections[1]
                        path.moveTo(start.x, start.y)
                        path.lineTo(end.x, end.y)
                        hasContourLine = true
                        crossingCount += 1
                        if (isMajor) {
                            val segmentLength = hypot(end.x - start.x, end.y - start.y)
                            if (segmentLength > longestSegment) {
                                longestSegment = segmentLength
                                bestCandidate = SegmentCandidate(
                                    x1 = start.x, y1 = start.y, x2 = end.x, y2 = end.y,
                                    labelX = (start.x + end.x) / 2f,
                                    labelY = (start.y + end.y) / 2f,
                                )
                            }
                        }
                    } else if (intersections.size == 4) {
                        val centerElevation =
                            (topLeft + topRight + bottomRight + bottomLeft) / 4f
                        val pairs = if (centerElevation > level) {
                            listOf(intersections[0] to intersections[1], intersections[2] to intersections[3])
                        } else {
                            listOf(intersections[0] to intersections[3], intersections[1] to intersections[2])
                        }
                        pairs.forEach { (start, end) ->
                            path.moveTo(start.x, start.y)
                            path.lineTo(end.x, end.y)
                            hasContourLine = true
                            crossingCount += 1
                        }
                    }
                    x += step
                }
                y += step
            }

            if (hasContourLine) {
                canvas.drawPath(path, darkPaint)
                colorPaint.color = contourColor(level.toInt(), isMajor)
                colorPaint.strokeWidth = if (isMajor) width + 0.35f else width
                canvas.drawPath(path, colorPaint)

                if (isMajor && crossingCount > tileLabelCrossings && bestCandidate != null) {
                    tileLabelCrossings = crossingCount
                    tileLabel = LabelCandidate(bestCandidate, level.toInt())
                    tileLabelLevel = level.toInt()
                }
            }
            level += interval
        }

        val label = tileLabel
        if (label != null) {
            drawLabel(canvas, label.segment, "${tileLabelLevel}m", zoomScale)
        }

        return ByteArrayOutputStream().use { stream ->
            overlay.compress(Bitmap.CompressFormat.PNG, 100, stream)
            overlay.recycle()
            stream.toByteArray()
        }
    }

    private fun contourIntervalForZoom(zoom: Int): Int = when {
        zoom >= 14 -> 50
        zoom >= 12 -> 100
        zoom >= 10 -> 200
        else -> 500
    }

    private fun interpolateEdge(
        x1: Float,
        y1: Float,
        v1: Float,
        x2: Float,
        y2: Float,
        v2: Float,
        level: Float,
    ): FloatPoint {
        val difference = v2 - v1
        val ratio = if (abs(difference) < 1e-6f) 0.5f else (level - v1) / difference
        return FloatPoint(x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio)
    }

    private fun contourColor(level: Int, isMajor: Boolean): Int {
        if (isMajor) return Color.argb((0.96f * 255).toInt(), 251, 191, 36)
        val altitudeTone = (level / 3200f).coerceIn(0f, 1f)
        val green = (248 - altitudeTone * 48).roundToInt()
        val blue = (235 - altitudeTone * 70).roundToInt()
        return Color.argb((0.82f * 255).toInt(), 255, green, blue)
    }

    private fun drawLabel(
        canvas: Canvas,
        segment: SegmentCandidate,
        text: String,
        zoomScale: Float,
    ) {
        val angle = atan2(segment.y2 - segment.y1, segment.x2 - segment.x1)
        val rotation = if (abs(angle) > Math.PI / 2) angle + Math.PI else angle
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = max(1.7f, 10f / zoomScale)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.save()
        canvas.translate(segment.labelX, segment.labelY)
        canvas.rotate(Math.toDegrees(rotation.toDouble()).toFloat())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(0.8f, 2.8f / zoomScale)
        paint.color = Color.argb((0.9f * 255).toInt(), 15, 23, 42)
        canvas.drawText(text, 0f, 0f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((0.98f * 255).toInt(), 255, 251, 235)
        canvas.drawText(text, 0f, 0f, paint)
        canvas.restore()
    }

    private data class FloatPoint(val x: Float, val y: Float)

    private data class SegmentCandidate(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val labelX: Float,
        val labelY: Float,
    )

    private data class LabelCandidate(
        val segment: SegmentCandidate,
        val level: Int,
    )
}
