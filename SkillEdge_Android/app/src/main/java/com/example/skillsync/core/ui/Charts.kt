package com.example.skillsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillsync.theme.skill

/**
 * Charts drawn straight onto Canvas.
 *
 * A charting library was considered and rejected: the dashboard needs five small,
 * highly-styled figures, all of which are a handful of arcs and lines. Pulling in
 * MPAndroidChart or Vico would add a dependency and an interop layer to draw
 * shapes we can express directly, and none of them theme cleanly against the
 * SkillColors palette.
 *
 * Every figure animates from zero via [animateProgressFromZero] — a terminating
 * animation, unlike `rememberInfiniteTransition`, which never idles and hangs
 * Compose tests.
 */

data class Slice(val label: String, val value: Int, val color: Color)

/**
 * Donut with the headline figure in the middle. Renders an empty ring rather
 * than nothing when every slice is zero, so the card keeps its shape while data
 * is missing.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 116.dp,
    thickness: Dp = 14.dp,
) {
    val sk = MaterialTheme.skill
    val total = slices.sumOf { it.value }.coerceAtLeast(0)
    val grow by animateProgressFromZero(1f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = sk.track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            if (total <= 0) return@Canvas

            var start = -90f
            slices.forEach { s ->
                if (s.value <= 0) return@forEach
                val sweep = 360f * s.value / total * grow
                drawArc(
                    color = s.color, startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = sk.bodyText,
            )
            Text(
                centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = sk.subText,
                fontSize = 9.sp,
            )
        }
    }
}

/** Dot-and-label key for a [DonutChart]; zero-valued slices are still listed. */
@Composable
fun ChartLegend(slices: List<Slice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                Spacer(Modifier.width(7.dp))
                Text(
                    s.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.skill.subText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${s.value}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.skill.bodyText,
                )
            }
        }
    }
}

/**
 * A 0-100 arc gauge. [value] null renders the empty track and an em dash — the
 * distinction between "nothing to report" and "zero" matters here, because a
 * gauge pinned at 0 reads as a real, bad measurement.
 */
@Composable
fun GaugeChart(
    value: Int?,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val sk = MaterialTheme.skill
    val grow by animateProgressFromZero((value ?: 0) / 100f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            // 240° sweep starting bottom-left, the usual dial reading direction.
            drawArc(
                color = sk.track, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (value != null) {
                drawArc(
                    color = tint, startAngle = 150f, sweepAngle = 240f * grow, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (value == null) "—" else "$value%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (value == null) sk.subText else sk.bodyText,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 9.sp)
        }
    }
}

data class TrendPoint(val label: String, val value: Int)

/**
 * Filled area line for a metric over time. Shows at most [maxPoints] from the
 * end; the y-axis is anchored at zero so month-to-month movement is not
 * exaggerated by a floating baseline.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
    maxPoints: Int = 12,
) {
    val sk = MaterialTheme.skill
    val pts = points.takeLast(maxPoints)
    val grow by animateProgressFromZero(1f)
    if (pts.isEmpty()) {
        Text(
            "No trend data returned.",
            style = MaterialTheme.typography.labelSmall, color = sk.subText,
            modifier = modifier,
        )
        return
    }
    val peak = (pts.maxOf { it.value }).coerceAtLeast(1)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val step = if (pts.size > 1) w / (pts.size - 1) else w
            fun px(i: Int) = if (pts.size > 1) i * step else w / 2f
            fun py(v: Int) = h - (v.toFloat() / peak) * h * 0.9f * grow - 2f

            // Reference grid at 25/50/75% of peak.
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                val y = h - f * h * 0.9f
                drawLine(sk.track, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            val line = Path().apply {
                pts.forEachIndexed { i, p ->
                    if (i == 0) moveTo(px(i), py(p.value)) else lineTo(px(i), py(p.value))
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(px(pts.lastIndex), h)
                lineTo(px(0), h)
                close()
            }
            drawPath(
                area,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.02f))
                ),
            )
            drawPath(line, color = tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            // Emphasise the latest reading — it is the one being acted on.
            drawCircle(tint, radius = 3.5.dp.toPx(), center = Offset(px(pts.lastIndex), py(pts.last().value)))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pts.first().label, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 8.5.sp)
            Text(pts.last().label, style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 8.5.sp)
        }
    }
}

data class BarDatum(val label: String, val value: Int, val color: Color)

/** Vertical bars with the value above and a short label below. */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 108.dp,
    valueSuffix: String = "",
) {
    val sk = MaterialTheme.skill
    if (bars.isEmpty()) {
        Text("Nothing to plot.", style = MaterialTheme.typography.labelSmall, color = sk.subText, modifier = modifier)
        return
    }
    val peak = bars.maxOf { it.value }.coerceAtLeast(1)
    Row(
        modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            val frac by animateProgressFromZero((b.value.toFloat() / peak).coerceIn(0f, 1f))
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    "${b.value}$valueSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = b.color, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Floor at 3% so a zero bar is still a visible category
                        // rather than a gap that looks like missing data.
                        .fillMaxHeight((frac * 0.70f).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(b.color)
                )
                Text(
                    b.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = sk.subText, fontSize = 8.sp, maxLines = 1,
                )
            }
        }
    }
}

/**
 * Single stacked bar showing how a whole divides up — used for team health,
 * where the split matters more than the individual counts.
 */
@Composable
fun StackedBar(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val sk = MaterialTheme.skill
    val total = slices.sumOf { it.value }
    val grow by animateProgressFromZero(1f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2f
        drawRoundRect(
            color = sk.track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        if (total <= 0) return@Canvas
        var x = 0f
        slices.forEach { s ->
            if (s.value <= 0) return@forEach
            val w = size.width * s.value / total * grow
            drawRoundRect(
                color = s.color,
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            x += w
        }
    }
}

// ── Command-centre figures ───────────────────────────────────────────────────

/**
 * Inline trend for a KPI tile. Bezier-smoothed, gradient area fill, and an
 * emphasised endpoint — the endpoint is the reading being acted on, so it gets
 * the only solid mark on the figure.
 *
 * Baseline floats between the series min and max rather than anchoring at zero:
 * on a 20dp-tall figure a zero baseline flattens every real movement into a
 * straight line, which is worse than no chart at all.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    tint: Color,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    endpointTint: Color = tint,
) {
    if (values.size < 2) return
    val grow by animateProgressFromZero(1f)
    val lo = values.min()
    val hi = values.max()
    val span = (hi - lo).coerceAtLeast(1)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val pad = 3.dp.toPx()
        val step = w / (values.size - 1)
        fun px(i: Int) = i * step
        fun py(v: Int) = h - pad - ((v - lo).toFloat() / span) * (h - pad * 2)

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = px(i)
            val y = py(v)
            if (i == 0) {
                line.moveTo(x, y)
            } else {
                // Horizontal control points give a smooth curve without the
                // overshoot a naive cubic through raw points produces.
                val prevX = px(i - 1)
                val prevY = py(values[i - 1])
                val cx = (prevX + x) / 2f
                line.cubicTo(cx, prevY, cx, y, x, y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(px(values.lastIndex), h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            area,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0f))
            ),
        )
        drawPath(
            line,
            color = tint,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
            alpha = grow,
        )
        drawCircle(
            endpointTint,
            radius = 2.4.dp.toPx(),
            center = Offset(px(values.lastIndex), py(values.last())),
        )
    }
}

/**
 * Twin-arc readiness gauge. The outer arc is the headline score, the inner arc
 * a second related measure (deployable share), so the hero carries two readings
 * without two separate figures competing for the same glance.
 */
@Composable
fun ReadinessRing(
    score: Int?,
    innerValue: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
) {
    val sk = MaterialTheme.skill
    val grow by animateProgressFromZero(1f)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = 7.dp.toPx()
            val inner = 3.5.dp.toPx()

            fun ring(strokeW: Float, inset: Float, sweepFrac: Float, brush: androidx.compose.ui.graphics.Brush?, track: Boolean) {
                val off = inset + strokeW / 2f
                val arcSize = Size(this.size.width - off * 2, this.size.height - off * 2)
                val topLeft = Offset(off, off)
                if (track) {
                    drawArc(
                        color = sk.track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(strokeW, cap = StrokeCap.Round),
                    )
                } else if (brush != null) {
                    drawArc(
                        brush = brush, startAngle = -90f, sweepAngle = 360f * sweepFrac * grow,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round),
                    )
                }
            }

            ring(outer, 0f, 0f, null, track = true)
            if (score != null) {
                ring(
                    outer, 0f, (score / 100f).coerceIn(0f, 1f),
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(com.example.skillsync.theme.AzureBlue, com.example.skillsync.theme.Cyan, com.example.skillsync.theme.Aqua)
                    ),
                    track = false,
                )
            }
            if (innerValue != null) {
                ring(inner, outer + 3.dp.toPx(), 0f, null, track = true)
                ring(
                    inner, outer + 3.dp.toPx(), (innerValue / 100f).coerceIn(0f, 1f),
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(com.example.skillsync.theme.SkyBlue, com.example.skillsync.theme.IceBlue)
                    ),
                    track = false,
                )
            }
        }
        Text(
            if (score == null) "—" else "$score",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = sk.frost,
        )
    }
}

/**
 * Utilisation trend with the healthy corridor drawn behind the bars.
 *
 * The corridor is the point of the chart: a manager does not need the exact
 * percentage, they need to know whether the team is inside 70–85%. Bars in the
 * band read blue, bars outside it take the status colour, and the current
 * period is always cyan.
 */
@Composable
fun CorridorBars(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 82.dp,
    bandLow: Int = 70,
    bandHigh: Int = 85,
) {
    val sk = MaterialTheme.skill
    if (values.isEmpty()) return
    val peak = (values.max().coerceAtLeast(bandHigh) * 1.12f)

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(height)) {
            // Target corridor, drawn first so bars sit over it.
            Canvas(Modifier.fillMaxSize()) {
                val top = size.height * (1f - bandHigh / peak)
                val bottom = size.height * (1f - bandLow / peak)
                drawRect(
                    color = com.example.skillsync.theme.IceBlue.copy(alpha = 0.07f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, (bottom - top).coerceAtLeast(1f)),
                )
                drawLine(
                    sk.track,
                    Offset(0f, top), Offset(size.width, top),
                    strokeWidth = 1f,
                )
                drawLine(
                    sk.track,
                    Offset(0f, bottom), Offset(size.width, bottom),
                    strokeWidth = 1f,
                )
            }
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                values.forEachIndexed { i, v ->
                    val isCurrent = i == values.lastIndex
                    val tint = when {
                        isCurrent -> com.example.skillsync.theme.Cyan
                        v > bandHigh -> sk.crit
                        v < bandLow -> sk.warn
                        else -> com.example.skillsync.theme.SkyBlue
                    }
                    val frac by animateProgressFromZero((v / peak).coerceIn(0f, 1f))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(frac.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(tint, tint.copy(alpha = 0.25f))
                                )
                            )
                    )
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.skill.labelText,
                        fontSize = 8.5.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Segmented distribution bar with an inline key.
 *
 * Replaces the donut for capacity and readiness splits: at phone width, segment
 * lengths on a single bar are far easier to compare than arc angles, and the
 * bar leaves room for the counts to sit beside it rather than inside a ring.
 */
@Composable
fun DistributionBar(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    height: Dp = 13.dp,
) {
    val total = slices.sumOf { it.value }
    val grow by animateProgressFromZero(1f)
    val sk = MaterialTheme.skill

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = sk.track, cornerRadius = r)
            if (total <= 0) return@Canvas
            var x = 0f
            slices.forEach { s ->
                if (s.value <= 0) return@forEach
                val w = size.width * s.value / total * grow
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(s.color, s.color.copy(alpha = 0.55f)),
                        startX = x, endX = x + w,
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = r,
                )
                x += w
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        s.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = sk.subText,
                        fontSize = 9.5.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${s.value}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sk.frost,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }
    }
}

/** Label + value + proportional track. The workhorse row for distributions. */
@Composable
fun MeterRow(
    label: String,
    value: Int,
    max: Int,
    tint: Color,
    caption: String = "",
    valueText: String? = null,
) {
    val sk = MaterialTheme.skill
    val frac by animateProgressFromZero(if (max > 0) value.toFloat() / max else 0f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall, color = sk.bodyText,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text(
                valueText ?: "$value",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = tint,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(sk.track)) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint)
            )
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall, color = sk.subText, fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
