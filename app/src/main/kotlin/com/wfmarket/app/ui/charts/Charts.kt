package com.wfmarket.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One bucket of the price chart. */
data class Candle(
    val open: Float,
    val close: Float,
    val low: Float,
    val high: Float,
    val median: Float,
    val average: Float,
    val sma: Float?,
    val donchTop: Float?,
    val donchBottom: Float?,
)

data class LineSeries(val label: String, val color: Color, val values: List<Float?>)

data class LegendEntry(val label: String, val color: Color, val outlined: Boolean = false)

@Composable
fun ChartLegend(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(entry.color))
                Text(entry.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A "nice" ascending set of about [count] tick values covering [lo]..[hi]. */
private fun niceTicks(lo: Float, hi: Float, count: Int): List<Float> {
    val range = (hi - lo).takeIf { it > 0f } ?: 1f
    val rough = range / count
    val magnitude = 10f.pow(floor(log10(rough)))
    val step = when (rough / magnitude) {
        in 0f..1.5f -> 1f
        in 1.5f..3f -> 2f
        in 3f..7f -> 5f
        else -> 10f
    } * magnitude
    val first = floor(lo / step) * step
    val last = ceil(hi / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= last + step / 2 }.toList()
}

private fun tickLabel(value: Float): String =
    if (abs(value) >= 1000) "${(value / 100).toInt() / 10f}k" else if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

/**
 * The frame shared by every chart: a recessive grid, price axis, date axis, and a crosshair driven
 * by touch. The owning screen shows the selected values in a readout above the chart, because a
 * floating tooltip would sit under the user's finger.
 */
@Composable
private fun ChartFrame(
    count: Int,
    yMin: Float,
    yMax: Float,
    xLabels: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    height: Dp,
    description: String,
    modifier: Modifier = Modifier,
    tickCount: Int = 4,
    draw: DrawScope.(x: (Int) -> Float, y: (Float) -> Float, slot: Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val density = LocalDensity.current
    val ticks = remember(yMin, yMax, tickCount) { niceTicks(yMin, yMax, tickCount) }
    val tickLayouts = remember(ticks, labelStyle) { ticks.map { measurer.measure(tickLabel(it), labelStyle) } }
    val labelGap = with(density) { 6.dp.toPx() }
    // As wide as the widest price label, so the labels sit at the card's edge and the plot isn't
    // pushed right by room that short labels like "6" don't use.
    val leftPad = (tickLayouts.maxOfOrNull { it.size.width } ?: 0) + labelGap
    val rightPad = with(density) { 6.dp.toPx() }
    val bottomPad = with(density) { 20.dp.toPx() }
    val topPad = with(density) { 6.dp.toPx() }

    fun indexAt(px: Float, width: Float): Int? {
        if (count == 0) return null
        val plot = width - leftPad - rightPad
        return (((px - leftPad) / plot) * count).toInt().coerceIn(0, count - 1)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(count) {
                detectTapGestures(onTap = { onSelect(indexAt(it.x, size.width.toFloat())) })
            }
            .pointerInput(count) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(indexAt(it.x, size.width.toFloat())) },
                    onHorizontalDrag = { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat())) },
                )
            },
    ) {
        val plotWidth = size.width - leftPad - rightPad
        val plotHeight = size.height - topPad - bottomPad
        val low = min(ticks.first(), yMin)
        val high = max(ticks.last(), yMax).let { if (it == low) low + 1 else it }
        val slot = if (count > 0) plotWidth / count else plotWidth
        val x: (Int) -> Float = { i -> leftPad + slot * (i + 0.5f) }
        val y: (Float) -> Float = { v -> topPad + plotHeight * (1 - (v - low) / (high - low)) }

        ticks.forEachIndexed { index, tick ->
            val py = y(tick)
            drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(leftPad, py), Offset(size.width - rightPad, py), strokeWidth = 1f)
            val layout = tickLayouts[index]
            drawText(layout, topLeft = Offset(leftPad - layout.size.width - labelGap, py - layout.size.height / 2f))
        }

        clipRect(leftPad, topPad, size.width - rightPad, topPad + plotHeight) { draw(x, y, slot) }

        xLabels.forEach { (index, label) ->
            val layout = measurer.measure(label, labelStyle)
            val px = (x(index) - layout.size.width / 2f).coerceIn(leftPad, size.width - rightPad - layout.size.width)
            drawText(layout, topLeft = Offset(px, size.height - bottomPad + 6f))
        }

        selected?.takeIf { it in 0 until count }?.let { index ->
            drawLine(colors.onSurface.copy(alpha = 0.6f), Offset(x(index), topPad), Offset(x(index), topPad + plotHeight), strokeWidth = 2f)
        }
    }
}

private fun DrawScope.drawSeriesLine(x: (Int) -> Float, y: (Float) -> Float, values: List<Float?>, color: Color, width: Float) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value == null) {
            started = false
        } else if (!started) {
            path.moveTo(x(index), y(value))
            started = true
        } else {
            path.lineTo(x(index), y(value))
        }
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Candles, Donchian channel and overlay lines. Volume sits in its own chart below: one axis per chart. */
@Composable
fun PriceChart(
    candles: List<Candle>,
    showCandles: Boolean,
    showDonchian: Boolean,
    overlays: List<LineSeries>,
    xLabels: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalSemanticColors.current
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val highs = buildList {
        candles.forEach { candle ->
            if (showCandles) add(candle.high)
            if (showDonchian) candle.donchTop?.let(::add)
        }
    }
    val lows = buildList {
        candles.forEach { candle ->
            if (showCandles) add(candle.low)
            if (showDonchian) candle.donchBottom?.let(::add)
        }
    }
    val lines = overlays.flatMap { it.values.filterNotNull() }
    // One troll trade at 10x the price would otherwise flatten everything else into a line.
    val highBound = robustBound(highs.sorted().asReversed()) { typical, extreme -> extreme > typical * 1.25f }
    val lowBound = robustBound(lows.sorted()) { typical, extreme -> extreme < typical * 0.75f }
    val yMin = (lines + listOfNotNull(lowBound.value)).minOrNull() ?: 0f
    val yMax = (lines + listOfNotNull(highBound.value)).maxOrNull() ?: 1f
    val trimmed = highBound.trimmed || lowBound.trimmed
    val density = LocalDensity.current
    val lineWidth = with(density) { 2.dp.toPx() }
    val wickWidth = with(density) { 1.dp.toPx() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    ChartFrame(
        count = candles.size,
        yMin = yMin,
        yMax = yMax,
        xLabels = xLabels,
        selected = selected,
        onSelect = onSelect,
        height = 240.dp,
        description = "Price chart with ${candles.size} data points, from $yMin to $yMax platinum",
        tickCount = 5,
    ) { x, y, slot ->
        if (showDonchian) {
            val band = Path()
            val tops = candles.mapIndexedNotNull { i, c -> c.donchTop?.let { i to it } }
            val bottoms = candles.mapIndexedNotNull { i, c -> c.donchBottom?.let { i to it } }
            if (tops.isNotEmpty() && bottoms.isNotEmpty()) {
                band.moveTo(x(tops.first().first), y(tops.first().second))
                tops.drop(1).forEach { (i, v) -> band.lineTo(x(i), y(v)) }
                bottoms.reversed().forEach { (i, v) -> band.lineTo(x(i), y(v)) }
                band.close()
                drawPath(band, semantic.seriesViolet.copy(alpha = 0.13f))
            }
        }

        if (showCandles) {
            val bodyWidth = (slot * 0.68f).coerceAtLeast(1.5f)
            candles.forEachIndexed { index, candle ->
                val up = candle.close >= candle.open
                val color = if (up) semantic.positive else semantic.negative
                drawLine(outline.copy(alpha = 0.7f), Offset(x(index), y(candle.high)), Offset(x(index), y(candle.low)), strokeWidth = wickWidth)
                val top = y(max(candle.open, candle.close))
                val bottom = y(min(candle.open, candle.close))
                val bodyHeight = (bottom - top).coerceAtLeast(wickWidth * 1.5f)
                val topLeft = Offset(x(index) - bodyWidth / 2, top)
                // Rising candles are hollow, so direction survives without relying on hue.
                if (up && bodyWidth >= 4f) {
                    drawRect(color, topLeft, Size(bodyWidth, bodyHeight), style = Stroke(wickWidth))
                } else {
                    drawRect(color, topLeft, Size(bodyWidth, bodyHeight))
                }
            }
        }

        overlays.forEach { series -> drawSeriesLine(x, y, series.values, series.color, lineWidth) }
    }
    if (trimmed) {
        Text(
            "Extreme outliers run off the chart. Touch one for its exact price.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

private data class Bound(val value: Float?, val trimmed: Boolean)

/**
 * [ordered] runs from most to least extreme. Uses the 97th percentile as the axis limit when the
 * values beyond it are far enough away for [isOutlier], and the true extreme otherwise.
 */
private fun robustBound(ordered: List<Float>, isOutlier: (typical: Float, extreme: Float) -> Boolean): Bound {
    val extreme = ordered.firstOrNull() ?: return Bound(null, false)
    if (ordered.size < 10) return Bound(extreme, false)
    val typical = ordered[(ordered.size * 0.03f).toInt() + 1]
    return if (isOutlier(typical, extreme)) Bound(typical, true) else Bound(extreme, false)
}

/** Thin rounded bars rising from zero, e.g. traded volume. */
@Composable
fun VolumeChart(
    values: List<Float>,
    xLabels: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    ChartFrame(
        count = values.size,
        yMin = 0f,
        yMax = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f,
        xLabels = xLabels,
        selected = selected,
        onSelect = onSelect,
        height = 130.dp,
        description = "Volume bars for ${values.size} periods",
        modifier = modifier,
        tickCount = 3,
    ) { x, y, slot ->
        val barWidth = (slot * 0.68f).coerceAtLeast(1.5f)
        val radius = CornerRadius(min(barWidth / 2, 4.dp.toPx()))
        val zero = y(0f)
        values.forEachIndexed { index, value ->
            if (value <= 0f) return@forEachIndexed
            val top = y(value)
            drawRoundRect(
                color = if (selected == index) color else color.copy(alpha = 0.72f),
                topLeft = Offset(x(index) - barWidth / 2, top),
                size = Size(barWidth, zero - top),
                cornerRadius = radius,
            )
        }
    }
}

/** Bars above and below a neutral zero line: price change per period, or the buy/sell gap. */
@Composable
fun DifferenceChart(
    values: List<Float?>,
    xLabels: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    positive: Color = LocalSemanticColors.current.positive,
    negative: Color = LocalSemanticColors.current.negative,
) {
    val zeroLine = MaterialTheme.colorScheme.onSurfaceVariant
    val present = values.filterNotNull()
    val reach = present.maxOfOrNull { abs(it) }?.coerceAtLeast(1f) ?: 1f
    ChartFrame(
        count = values.size,
        yMin = present.minOrNull()?.coerceAtMost(0f) ?: -reach,
        yMax = present.maxOrNull()?.coerceAtLeast(0f) ?: reach,
        xLabels = xLabels,
        selected = selected,
        onSelect = onSelect,
        height = 150.dp,
        description = "Difference bars for ${values.size} periods",
        modifier = modifier,
        tickCount = 4,
    ) { x, y, slot ->
        val barWidth = (slot * 0.68f).coerceAtLeast(1.5f)
        val zero = y(0f)
        values.forEachIndexed { index, value ->
            if (value == null || value == 0f) return@forEachIndexed
            val end = y(value)
            drawRect(
                color = if (value > 0) positive else negative,
                topLeft = Offset(x(index) - barWidth / 2, min(zero, end)),
                size = Size(barWidth, abs(end - zero)),
            )
        }
        drawLine(zeroLine, Offset(x(0) - slot / 2, zero), Offset(x(values.lastIndex.coerceAtLeast(0)) + slot / 2, zero), strokeWidth = 2f)
    }
}

