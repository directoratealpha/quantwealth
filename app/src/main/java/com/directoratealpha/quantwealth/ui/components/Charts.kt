package com.directoratealpha.quantwealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.directoratealpha.quantwealth.model.ChartPoint
import com.directoratealpha.quantwealth.ui.theme.AppColors
import kotlin.math.roundToInt

@Composable
fun MiniSparkline(
    points: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) AppColors.AccentEmerald else AppColors.AccentRed
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: 1.0

        val path = Path()
        val fillPath = Path()
        val stepX = size.width / (points.size - 1).toFloat()

        points.forEachIndexed { i, v ->
            val x = i * stepX
            val normalized = ((v - minVal) / range).toFloat()
            val y = size.height - (normalized * (size.height - 6f)) - 3f
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun InteractiveAssetPriceChart(
    points: List<ChartPoint>,
    isPositive: Boolean,
    onScrubPointChanged: (ChartPoint?) -> Unit,
    height: Dp = 210.dp
) {
    val lineColor = if (isPositive) AppColors.AccentEmerald else AppColors.AccentRed
    var scrubbedIndex by remember(points) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        if (points.size >= 2 && size.width > 0) {
                            val idx = ((offset.x / size.width.toFloat()) * (points.size - 1))
                                .roundToInt()
                                .coerceIn(0, points.size - 1)
                            scrubbedIndex = idx
                            onScrubPointChanged(points[idx])
                            tryAwaitRelease()
                            scrubbedIndex = null
                            onScrubPointChanged(null)
                        }
                    }
                )
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (points.size >= 2 && size.width > 0) {
                            val idx = ((offset.x / size.width.toFloat()) * (points.size - 1))
                                .roundToInt()
                                .coerceIn(0, points.size - 1)
                            scrubbedIndex = idx
                            onScrubPointChanged(points[idx])
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (points.size >= 2 && size.width > 0) {
                            val idx = ((change.position.x / size.width.toFloat()) * (points.size - 1))
                                .roundToInt()
                                .coerceIn(0, points.size - 1)
                            scrubbedIndex = idx
                            onScrubPointChanged(points[idx])
                        }
                    },
                    onDragEnd = {
                        scrubbedIndex = null
                        onScrubPointChanged(null)
                    },
                    onDragCancel = {
                        scrubbedIndex = null
                        onScrubPointChanged(null)
                    }
                )
            }
    ) {
        if (points.size < 2) return@Canvas

        val minVal = points.minOf { it.price }
        val maxVal = points.maxOf { it.price }
        val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: 1.0

        // Horizontal reference grid
        for (i in 0..3) {
            val y = size.height * (i / 3f)
            drawLine(
                color = AppColors.CardBorder.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        val stepX = size.width / (points.size - 1).toFloat()
        val linePath = Path()
        val fillPath = Path()
        val coords = ArrayList<Offset>(points.size)

        points.forEachIndexed { idx, pt ->
            val x = idx * stepX
            val norm = ((pt.price - minVal) / range).toFloat()
            val y = size.height - (norm * (size.height - 22f)) - 11f
            coords.add(Offset(x, y))
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.32f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Live endpoint dot or scrubbed crosshair
        val activeIdx = scrubbedIndex ?: (coords.size - 1)
        val activeCoord = coords[activeIdx]

        if (scrubbedIndex != null) {
            drawLine(
                color = AppColors.AccentCyan.copy(alpha = 0.75f),
                start = Offset(activeCoord.x, 0f),
                end = Offset(activeCoord.x, size.height),
                strokeWidth = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        }

        drawCircle(
            color = lineColor.copy(alpha = 0.30f),
            radius = 15f,
            center = activeCoord
        )
        drawCircle(
            color = lineColor,
            radius = 7.5f,
            center = activeCoord
        )
    }
}

@Composable
fun BacktestEquityChart(
    portfolioCurve: List<Double>,
    investedCurve: List<Double>,
    height: Dp = 190.dp
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (portfolioCurve.size < 2 || investedCurve.size < 2) return@Canvas

        val allMax = maxOf(
            portfolioCurve.maxOrNull() ?: 1.0,
            investedCurve.maxOrNull() ?: 1.0
        )
        val allMin = minOf(
            portfolioCurve.minOrNull() ?: 0.0,
            investedCurve.minOrNull() ?: 0.0
        ) * 0.92
        val range = (allMax - allMin).coerceAtLeast(1.0)

        for (i in 0..3) {
            val y = size.height * (i / 3f)
            drawLine(
                color = AppColors.CardBorder.copy(alpha = 0.65f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        val stepX = size.width / (portfolioCurve.size - 1).toFloat()
        val portPath = Path()
        val fillPath = Path()
        portfolioCurve.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = size.height - (((value - allMin) / range).toFloat() * (size.height - 14f)) - 7f
            if (idx == 0) {
                portPath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                portPath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(AppColors.AccentEmerald.copy(alpha = 0.30f), Color.Transparent)
            )
        )
        drawPath(
            path = portPath,
            color = AppColors.AccentEmerald,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val invPath = Path()
        investedCurve.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = size.height - (((value - allMin) / range).toFloat() * (size.height - 14f)) - 7f
            if (idx == 0) invPath.moveTo(x, y) else invPath.lineTo(x, y)
        }
        drawPath(
            path = invPath,
            color = AppColors.AccentCyan.copy(alpha = 0.85f),
            style = Stroke(
                width = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
        )
    }
}

@Composable
fun MonteCarloFanChart(
    p10Curve: List<Double>,
    p50Curve: List<Double>,
    p90Curve: List<Double>,
    height: Dp = 200.dp
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (p50Curve.size < 2) return@Canvas

        val maxVal = (p90Curve.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val minVal = ((p10Curve.minOrNull() ?: 0.0) * 0.90).coerceAtLeast(0.0)
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        for (i in 0..3) {
            val y = size.height * (i / 3f)
            drawLine(
                color = AppColors.CardBorder.copy(alpha = 0.65f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f
            )
        }

        val count = p50Curve.size
        val stepX = size.width / (count - 1).toFloat()

        fun mapY(v: Double): Float {
            return size.height - (((v - minVal) / range).toFloat() * (size.height - 16f)) - 8f
        }

        val fanPath = Path()
        for (i in 0 until count) {
            val x = i * stepX
            val y = mapY(p90Curve[i])
            if (i == 0) fanPath.moveTo(x, y) else fanPath.lineTo(x, y)
        }
        for (i in count - 1 downTo 0) {
            val x = i * stepX
            val y = mapY(p10Curve[i])
            fanPath.lineTo(x, y)
        }
        fanPath.close()

        drawPath(
            path = fanPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    AppColors.AccentPurple.copy(alpha = 0.25f),
                    AppColors.AccentCyan.copy(alpha = 0.12f)
                )
            )
        )

        fun drawCurve(values: List<Double>, color: Color, strokeWidth: Float) {
            val path = Path()
            values.forEachIndexed { idx, v ->
                val x = idx * stepX
                val y = mapY(v)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawCurve(p90Curve, AppColors.AccentEmerald, 4.5f)
        drawCurve(p50Curve, AppColors.AccentCyan, 6f)
        drawCurve(p10Curve, AppColors.AccentGold, 4.5f)
    }
}
