@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wfmarket.api.model.Item
import com.wfmarket.api.model.ItemStatistics
import com.wfmarket.api.model.OrderType
import com.wfmarket.api.model.StatPoint
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.charts.*
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import java.util.Locale

private enum class Range(val label: String) { HOURS_48("48 hours"), DAYS_90("90 days") }

/** Rank, subtype and stars split an item's statistics into separate series. */
private data class Variant(val rank: Int?, val subtype: String?, val amber: Int?, val cyan: Int?, val charges: Int?) {
    val label: String
        get() = listOfNotNull(
            rank?.let { "Rank $it" },
            subtype?.humanize(),
            amber?.let { "$it amber" },
            cyan?.let { "$it cyan" },
            charges?.let { "$it charges" },
        ).joinToString(" · ").ifEmpty { "All" }
}

private fun StatPoint.variant() = Variant(modRank, subtype, amberStars, cyanStars, charges)

private fun xLabels(points: List<StatPoint>, range: Range): List<Pair<Int, String>> {
    if (points.isEmpty()) return emptyList()
    val pattern = if (range == Range.HOURS_48) "HH:mm" else "d MMM"
    val indices = if (points.size < 4) points.indices.toList() else listOf(0, points.size / 3, points.size * 2 / 3, points.lastIndex)
    return indices.map { it to formatDate(points[it].datetime, pattern) }
}

private fun num(value: Double?): String = when {
    value == null -> "–"
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

@Composable
fun StatisticsTab(item: Item, statistics: Load<ItemStatistics>, onRetry: () -> Unit) {
    LoadContent(statistics, onRetry) { stats ->
        var range by rememberSaveable { mutableStateOf(Range.DAYS_90) }
        val closedAll = if (range == Range.HOURS_48) stats.closed.hours48 else stats.closed.days90
        val liveAll = if (range == Range.HOURS_48) stats.live.hours48 else stats.live.days90

        val variants = remember(closedAll) { closedAll.map { it.variant() }.distinct().sortedWith(compareBy({ it.rank ?: -1 }, { it.subtype ?: "" }, { it.amber ?: -1 }, { it.cyan ?: -1 }, { it.charges ?: -1 })) }
        var variant by remember(variants) { mutableStateOf(variants.firstOrNull()) }
        val closed = remember(closedAll, variant) { closedAll.filter { it.variant() == variant }.sortedBy { it.datetime } }
        val live = remember(liveAll, variant) { liveAll.filter { it.variant() == variant }.sortedBy { it.datetime } }

        var selected by remember(closed) { mutableStateOf<Int?>(null) }
        var showCandles by rememberSaveable { mutableStateOf(true) }
        var showDonchian by rememberSaveable { mutableStateOf(true) }
        var showMedian by rememberSaveable { mutableStateOf(true) }
        var showSma by rememberSaveable { mutableStateOf(false) }
        var showAverage by rememberSaveable { mutableStateOf(false) }
        var showTable by rememberSaveable { mutableStateOf(false) }

        val semantic = LocalSemanticColors.current
        val labels = remember(closed, range) { xLabels(closed, range) }

        FadingLazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChoiceButtonGroup(Range.entries, range, { it.label }, { range = it })
                    if (variants.size > 1) {
                        DropdownChip("", variant, variants, { it?.label ?: "All" }, { variant = it })
                    }
                }
            }

            if (closed.isEmpty()) {
                item { EmptyBox("No trades recorded", "Nobody has closed an order for this in the last ${range.label}.") }
            } else {
                item { SummaryTiles(closed) }

                item {
                    ChartCard("Price", "Closed trades, per ${if (range == Range.HOURS_48) "hour" else "day"}") {
                        Readout(closed.getOrNull(selected ?: -1), range) { point ->
                            "Open ${num(point.openPrice)} · Close ${num(point.closedPrice)} · Low ${num(point.minPrice)} · High ${num(point.maxPrice)} · Median ${num(point.median)}"
                        }
                        val overlays = buildList {
                            if (showMedian) add(LineSeries("Median", semantic.seriesBlue, closed.map { it.median.toFloat() }))
                            if (showSma) add(LineSeries("SMA", semantic.seriesOrange, closed.map { it.movingAvg?.toFloat() }))
                            if (showAverage) add(LineSeries("Average", semantic.seriesAqua, closed.map { it.avgPrice.toFloat() }))
                        }
                        PriceChart(
                            candles = closed.map {
                                Candle(
                                    open = (it.openPrice ?: it.avgPrice).toFloat(),
                                    close = (it.closedPrice ?: it.avgPrice).toFloat(),
                                    low = it.minPrice.toFloat(),
                                    high = it.maxPrice.toFloat(),
                                    median = it.median.toFloat(),
                                    average = it.avgPrice.toFloat(),
                                    sma = it.movingAvg?.toFloat(),
                                    donchTop = it.donchTop?.toFloat(),
                                    donchBottom = it.donchBot?.toFloat(),
                                )
                            },
                            showCandles = showCandles,
                            showDonchian = showDonchian,
                            overlays = overlays,
                            xLabels = labels,
                            selected = selected,
                            onSelect = { selected = it },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ToggleChip("Candles", showCandles) { showCandles = it }
                            ToggleChip("Donchian", showDonchian) { showDonchian = it }
                            ToggleChip("Median", showMedian) { showMedian = it }
                            ToggleChip("SMA", showSma) { showSma = it }
                            ToggleChip("Average", showAverage) { showAverage = it }
                        }
                        ChartLegend(
                            buildList {
                                if (showCandles) {
                                    add(LegendEntry("Close above open", semantic.positive))
                                    add(LegendEntry("Close below open", semantic.negative))
                                }
                                if (showDonchian) add(LegendEntry("Donchian channel", semantic.seriesViolet))
                                overlays.forEach { add(LegendEntry(it.label, it.color)) }
                            },
                        )
                    }
                }

                item {
                    ChartCard("Volume", "Items traded in each period") {
                        Readout(closed.getOrNull(selected ?: -1), range) { "${it.volume} traded" }
                        VolumeChart(closed.map { it.volume.toFloat() }, labels, selected, { selected = it })
                    }
                }

                item {
                    ChartCard("Price change", "Close minus open in each period") {
                        Readout(closed.getOrNull(selected ?: -1), range) { point ->
                            val change = (point.closedPrice ?: 0.0) - (point.openPrice ?: 0.0)
                            "${if (change > 0) "+" else ""}${num(change)} platinum"
                        }
                        DifferenceChart(
                            closed.map { point ->
                                val open = point.openPrice
                                val close = point.closedPrice
                                if (open != null && close != null) (close - open).toFloat() else null
                            },
                            labels, selected, { selected = it },
                        )
                    }
                }
            }

            spreadSection(this, live, range)

            if (closed.isNotEmpty()) {
                item {
                    TextButton(onClick = { showTable = !showTable }) { Text(if (showTable) "Hide data table" else "Show data table") }
                }
                if (showTable) {
                    item { TableHeader() }
                    items(closed.reversed(), key = { it.datetime + it.variant().label }) { point -> TableRow(point, range) }
                }
            }
        }
    }
}

/** Live order prices: the gap between what sellers ask and what buyers offer. */
private fun spreadSection(scope: androidx.compose.foundation.lazy.LazyListScope, live: List<StatPoint>, range: Range) {
    val sell = live.filter { it.orderType == OrderType.SELL }.associateBy { it.datetime }
    val buy = live.filter { it.orderType == OrderType.BUY }.associateBy { it.datetime }
    val times = (sell.keys + buy.keys).sorted()
    if (times.isEmpty()) return
    scope.item {
        val semantic = LocalSemanticColors.current
        var selected by remember(times) { mutableStateOf<Int?>(null) }
        val ordered = times.map { sell[it] ?: buy.getValue(it) }
        val labels = remember(times, range) { xLabels(ordered, range) }
        val upper = LineSeries("Sell orders (median)", semantic.seriesOrange, times.map { sell[it]?.median?.toFloat() })
        val lower = LineSeries("Buy orders (median)", semantic.seriesBlue, times.map { buy[it]?.median?.toFloat() })
        val gaps = times.map { time ->
            val s = sell[time]?.median
            val b = buy[time]?.median
            if (s != null && b != null) (s - b).toFloat() else null
        }

        ChartCard("Buy/sell spread", "What sellers ask against what buyers offer on open orders") {
            val index = selected
            if (index != null && index in times.indices) {
                val time = times[index]
                Text(
                    "${formatDate(time, if (range == Range.HOURS_48) "d MMM HH:mm" else "d MMM yyyy")}: sell ${num(sell[time]?.median)}, buy ${num(buy[time]?.median)}, gap ${gaps[index]?.let { num(it.toDouble()) } ?: "–"}",
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                Text("Touch a chart for exact values", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SpreadChart(upper, lower, labels, selected, { selected = it })
            ChartLegend(listOf(LegendEntry(upper.label, upper.color), LegendEntry(lower.label, lower.color)))
            Text("Gap between them", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            DifferenceChart(gaps, labels, selected, { selected = it }, positive = semantic.seriesViolet, negative = semantic.negative)
        }
    }
}

@Composable
private fun SummaryTiles(closed: List<StatPoint>) {
    val last = closed.last()
    val first = closed.first()
    val change = last.median - first.median
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Latest median", Modifier.weight(1f)) { Platinum(last.median) }
        StatTile("Change", Modifier.weight(1f)) {
            val semantic = LocalSemanticColors.current
            Text(
                "${if (change > 0) "▲ +" else if (change < 0) "▼ " else ""}${num(change)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (change > 0) semantic.positive else if (change < 0) semantic.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        StatTile("Volume", closed.sumOf { it.volume }.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    // Wide enough padding that axis labels clear the rounded corners.
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Readout(point: StatPoint?, range: Range, text: (StatPoint) -> String) {
    if (point == null) {
        Text("Touch a chart for exact values", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            "${formatDate(point.datetime, if (range == Range.HOURS_48) "d MMM HH:mm" else "d MMM yyyy")}: ${text(point)}",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text(label) })
}

