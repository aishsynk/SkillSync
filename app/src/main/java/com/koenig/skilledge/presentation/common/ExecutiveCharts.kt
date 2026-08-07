package com.koenig.skilledge.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koenig.skilledge.core.theme.CyanAccent
import com.koenig.skilledge.core.theme.ElectricBlue
import com.koenig.skilledge.core.theme.ErrorRed
import com.koenig.skilledge.core.theme.SuccessGreen
import com.koenig.skilledge.core.theme.WarningYellow

/**
 * High-density Bezier Sparkline chart for utilization trend visualization
 */
@Composable
fun SparklineChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier.height(36.dp).fillMaxWidth(),
    lineColor: Color = ElectricBlue,
    fillColor: Color = ElectricBlue.copy(alpha = 0.15f)
) {
    if (dataPoints.isEmpty()) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val min = (dataPoints.minOrNull() ?: 0f) * 0.9f
        val max = (dataPoints.maxOrNull() ?: 100f) * 1.1f
        val range = if (max == min) 1f else max - min

        val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

        val points = dataPoints.mapIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - min) / range * height)
            Offset(x, y)
        }

        val strokePath = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val cx = (p1.x + p2.x) / 2
                    cubicTo(cx, p1.y, cx, p2.y, p2.x, p2.y)
                }
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Donut distribution chart for capacity (Bench / Optimal / Stretched)
 */
@Composable
fun CapacityDonutChart(
    benchCount: Int,
    optimalCount: Int,
    stretchedCount: Int,
    modifier: Modifier = Modifier.size(54.dp)
) {
    val total = (benchCount + optimalCount + stretchedCount).coerceAtLeast(1).toFloat()
    val benchAngle = (benchCount / total) * 360f
    val optimalAngle = (optimalCount / total) * 360f
    val stretchedAngle = (stretchedCount / total) * 360f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            var startAngle = -90f

            // Optimal (Cyan)
            if (optimalAngle > 0) {
                drawArc(
                    color = CyanAccent,
                    startAngle = startAngle,
                    sweepAngle = optimalAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                startAngle += optimalAngle
            }

            // Bench (Yellow/Green)
            if (benchAngle > 0) {
                drawArc(
                    color = WarningYellow,
                    startAngle = startAngle,
                    sweepAngle = benchAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                startAngle += benchAngle
            }

            // Stretched (Red)
            if (stretchedAngle > 0) {
                drawArc(
                    color = ErrorRed,
                    startAngle = startAngle,
                    sweepAngle = stretchedAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        Text(
            text = "${total.toInt()}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Circular Arc Progress Ring for Readiness Score
 */
@Composable
fun ReadinessRingGauge(
    score: Int,
    modifier: Modifier = Modifier.size(54.dp)
) {
    val sweep = (score / 100f) * 270f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background Track
            drawArc(
                color = Color(0xFF334155),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Progress Ring
            drawArc(
                color = if (score >= 85) SuccessGreen else (if (score >= 70) WarningYellow else ErrorRed),
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
        }
        Text(
            text = "$score%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
