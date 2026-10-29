@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.DucatEntry
import com.wfmarket.api.model.Ducats
import com.wfmarket.api.model.assetUrl
import com.wfmarket.api.model.localized
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class DucanatorViewModel(private val container: AppContainer) : ViewModel() {
    private val _data = MutableStateFlow<Load<Ducats>>(Load.Loading)
    val data = _data.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.changes.flow.collect { if (it == DataKind.SCOPE) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hadData = _data.value is Load.Ready
            _refreshing.value = hadData
            val updated = _data.accept(runCatching { container.market.ducats() })
            if (hadData && !updated) container.refreshFailed()
            _refreshing.value = false
        }
    }
}

private enum class DucatSort(val label: String) {
    DPP("Ducats/Plat"),
    DPP_WA("Ducats/Plat (WA)"),
    WA_PRICE("WA price"),
    MEDIAN("Median"),
    DUCATS("Ducats"),
    VOLUME("Volume"),
    NAME("Name"),
    POS_DAY("Rank change, day"),
    POS_WEEK("Rank change, week"),
    POS_MONTH("Rank change, month"),
}

private enum class DucatPeriod(val label: String) { HOUR("Hour"), DAY("Day") }

/** The Ducanator: the items that return the most ducats per platinum spent. */
@Composable
fun DucanatorScreen() {
    val container = appContainer()
    val viewModel = containerViewModel { DucanatorViewModel(it) }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()

    var period by rememberSaveable { mutableStateOf(DucatPeriod.HOUR) }
    var sort by rememberSaveable { mutableStateOf(DucatSort.DPP) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ducanator") },
                actions = {
                    IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, "About the Ducanator") }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The same filled pill as the home page's search bar.
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Filter items") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChoiceButtonGroup(DucatPeriod.entries, period, { it.label }, { period = it })
                DropdownChip("Sort", sort, DucatSort.entries, { it.label }, { sort = it; descending = it != DucatSort.NAME && it != DucatSort.WA_PRICE && it != DucatSort.MEDIAN }, isDefault = sort == DucatSort.DPP)
                FilterChip(selected = false, onClick = { descending = !descending }, label = { Text(if (descending) "High → low" else "Low → high") })
            }
            RefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                LoadContent(data, viewModel::refresh) { ducats ->
                    val rows = remember(ducats, period, sort, descending, query, catalog) {
                        val source = if (period == DucatPeriod.HOUR) ducats.previousHour else ducats.previousDay
                        val filtered = if (query.isBlank()) source else source.filter { entry ->
                            catalog.itemsById[entry.item]?.let { catalog.itemName(it).contains(query.trim(), ignoreCase = true) } == true
                        }
                        val sorted = when (sort) {
                            DucatSort.DPP -> filtered.sortedBy { it.ducatsPerPlatinum }
                            DucatSort.DPP_WA -> filtered.sortedBy { it.ducatsPerPlatinumWa }
                            DucatSort.WA_PRICE -> filtered.sortedBy { it.waPrice }
                            DucatSort.MEDIAN -> filtered.sortedBy { it.median }
                            DucatSort.DUCATS -> filtered.sortedBy { it.ducats }
                            DucatSort.VOLUME -> filtered.sortedBy { it.volume }
                            DucatSort.NAME -> filtered.sortedBy { entry -> catalog.itemsById[entry.item]?.let(catalog::itemName).orEmpty() }
                            DucatSort.POS_DAY -> filtered.sortedBy { it.positionChangeDay }
                            DucatSort.POS_WEEK -> filtered.sortedBy { it.positionChangeWeek }
                            DucatSort.POS_MONTH -> filtered.sortedBy { it.positionChangeMonth }
                        }
                        if (descending) sorted.reversed() else sorted
                    }
                    FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        if (rows.isEmpty()) item { EmptyBox("No results") }
                        itemsIndexed(rows, key = { _, entry -> entry.item }) { index, entry ->
                            DucatRow(entry, catalog, ListItemDefaults.segmentedShapes(index, rows.size))
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } },
            title = { Text("Ducanator") },
            text = {
                Text(
                    "Which items return the most ducats per platinum spent.\n\n" +
                        "WA price is the volume-weighted average price. Data comes only from this site's orders by online users, and is collected hourly and daily.\n\n" +
                        "Rank change shows how far an item moved up or down the list over a day, week and month.",
                )
            },
        )
    }
}

@Composable
private fun DucatRow(entry: DucatEntry, catalog: Catalog, shapes: ListItemShapes) {
    val navigator = LocalNavigator.current
    val item = catalog.itemsById[entry.item]
    val i18n = item?.i18n?.localized(catalog.language)
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = { item?.let { navigator.item(it.slug) } },
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { if (item != null) ItemThumb(catalog.art(item)) else ItemThumb(null as String?) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Each figure stays whole, and a larger font moves the last ones down a line instead of squeezing them.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LabelledValue("WA", fmt(entry.waPrice))
                    LabelledValue("Med", fmt(entry.median))
                    LabelledValue("Duc", entry.ducats.toString())
                    LabelledValue("Vol", entry.volume.toString())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PositionChange("D", entry.positionChangeDay)
                    PositionChange("W", entry.positionChangeWeek)
                    PositionChange("M", entry.positionChangeMonth)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(entry.ducatsPerPlatinum), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = colors.primary)
                Text("ducats/plat", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text("WA ${fmt(entry.ducatsPerPlatinumWa)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        },
    ) {
        Text(i18n?.name ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A rank that improved has a positive change, and the arrow keeps the direction readable without hue. */
@Composable
private fun PositionChange(label: String, change: Int) {
    val semantic = LocalSemanticColors.current
    val (arrow, color) = when {
        change > 0 -> "▲" to semantic.positive
        change < 0 -> "▼" to semantic.negative
        else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text("$arrow${if (change != 0) kotlin.math.abs(change).toString() else ""}", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false)
    }
}

private fun fmt(value: Double): String = when {
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(Locale.US, "%.2f", value)
}
