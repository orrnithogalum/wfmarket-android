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

