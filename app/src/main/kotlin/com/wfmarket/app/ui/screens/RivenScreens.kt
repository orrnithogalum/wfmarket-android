@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.api.model.*
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.RivenWeeks
import com.wfmarket.app.data.weekKey
import com.wfmarket.app.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Grades

/** A listed riven's grades, unless they're turned off in Settings. */
@Composable
fun rememberRivenRolls(item: AuctionItem, catalog: Catalog): RivenRolls? {
    val container = appContainer()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val classes by container.rivens.classes.collectAsStateWithLifecycle()
    if (!settings.rivenGrades) return null
    return remember(item, catalog, classes) { catalog.rivenRolls(item, classes) }
}

private val WidestGrades = listOf("A+", "B+", "C+")

/** How far apart the bars start filling, and how long after its bar starts a grade appears. */
private const val BAR_STEP_MILLIS = 50L
private const val GRADE_DELAY_MILLIS = 200L

private fun RivenGrade.spoken(): String = label.replace("+", " plus").replace("-", " minus")

/**
 * A stat's grade as a letter. Every grade takes the room of the widest, with the letters at its
 * start, so a list of stats lines up at any text size. A value no roll can reach shows a question
 * mark, and a stat that can't be graded leaves the space empty.
 */
@Composable
fun RivenGradeLabel(roll: StatRoll?, modifier: Modifier = Modifier, revealIndex: Int? = null) {
    val grade = roll?.grade
    val text = when {
        roll?.outOfRange == true -> "?"
        grade != null -> grade.label
        else -> ""
    }
    val spoken = when {
        roll?.outOfRange == true -> "can't be graded"
        grade != null -> "grade ${grade.spoken()}"
        else -> ""
    }
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    // Beside a bar, the letter fades in as the bar nears its value, so the two read as one reading.
    val reveal = remember { Animatable(if (revealIndex == null) 1f else 0f) }
    if (revealIndex != null) {
        LaunchedEffect(Unit) {
            delay(BAR_STEP_MILLIS * revealIndex + GRADE_DELAY_MILLIS)
            reveal.animateTo(1f, Motion.fadeIn())
        }
    }
    Box(
        modifier.semantics(mergeDescendants = true) { if (spoken.isNotEmpty()) contentDescription = spoken }.graphicsLayer { alpha = reveal.value },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Unseen, these size the box to the widest grades.
        WidestGrades.forEach { Text(it, Modifier.alpha(0f).clearAndSetSemantics {}, style = style, maxLines = 1) }
        Text(text, Modifier.clearAndSetSemantics {}, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Where a stat's roll landed between the worst and best it could be, with those ends under it.
 * The bar fills in as it appears, so it reads as a measurement rather than a slider.
 */
@Composable
fun RivenRollBar(roll: StatRoll, worst: String, best: String, modifier: Modifier = Modifier, index: Int = 0) {
    // The bars fill one shortly after another, so a riven's rolls read top to bottom.
    val fill = remember { Animatable(0f) }
    LaunchedEffect(roll.position) {
        if (fill.value == 0f) delay(BAR_STEP_MILLIS * index)
        fill.animateTo(roll.position.toFloat(), Motion.arrive(500))
    }
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinearProgressIndicator(progress = { fill.value.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row {
            Text("Worst $worst", style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
            Text("Best $best", style = labelStyle, color = labelColor)
        }
    }
}

/** What a riven's grading had to assume, in a line, or null when it assumed nothing. */
fun rivenRollsNote(rolls: RivenRolls, listedRank: Int?): String? = buildList {
    rolls.nowFactor?.let { factor ->
        val percent = ((factor - 1) * 100).roundToInt()
        val change = if (percent >= 0) "$percent% higher" else "${-percent}% lower"
        add("Listed before Riven Disposition was retired, so in game these stats are now about $change.")
    }
    if (rolls.rank != (listedRank ?: 0)) add("These look like maxed stats, so they're graded at rank ${rolls.rank}.")
}.joinToString(" ").ifEmpty { null }

// ---------------------------------------------------------------- Trade figures

private fun Catalog.tradeWeapon(trade: RivenTrade): RivenWeapon? = trade.compatibility?.let { rivenWeaponsByEnglishName[it.lowercase()] }

private fun Catalog.tradeTitle(trade: RivenTrade): String {
    val name = trade.compatibility ?: return "Veiled ${trade.rivenClass?.label?.lowercase() ?: ""} riven".replace("  ", " ")
    return tradeWeapon(trade)?.let { weaponName(AuctionType.RIVEN, it.slug) } ?: name
}

/** Whole platinum with separators, except for the odd small median like 9.5. */
private fun plat(value: Double): String =
    if (value < 10 && value % 1.0 != 0.0) String.format(Locale.US, "%.1f", value) else String.format(Locale.getDefault(), "%,d", value.roundToLong())

private fun day(millis: Long, pattern: String = "d MMM"): String =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

/** How a figure moved since the week before, with an arrow so the direction reads without color. */
private fun change(now: Double, before: Double?): String? {
    if (before == null || before <= 0) return null
    val percent = ((now - before) / before * 100).roundToInt()
    return when {
        percent > 0 -> "▲ $percent%"
        percent < 0 -> "▼ ${-percent}%"
        else -> "– 0%"
    }
}

/** A weapon's riven trades last week, if Warframe's figures have it. */
@Composable
private fun rememberLatestWeek(weaponSlug: String, catalog: Catalog): Pair<RivenWeeks, RivenTrade>? {
    val container = appContainer()
    val weeks by container.rivens.weeks.collectAsStateWithLifecycle()
    val all = weeks ?: return null
    return remember(all, weaponSlug, catalog) {
        val weapon = catalog.rivenWeaponsBySlug[weaponSlug] ?: return@remember null
        val week = all.history(catalog.englishName(weapon)).firstOrNull()?.takeIf { it.publishedMillis == all.latest?.publishedMillis }
        (week?.unrolled ?: week?.rolled)?.let { all to it }
    }
}

/** Last week's going rates for an auction's weapon, as figures. Opens the full figures when tapped. */
@Composable
fun RivenTradeCard(weaponSlug: String, catalog: Catalog, modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    val latest = rememberLatestWeek(weaponSlug, catalog)
    ArrivingContent(latest, modifier, gap) { (weeks, trade) -> TradeCardContent(weeks, trade, catalog) }
}

@Composable
private fun TradeCardContent(weeks: RivenWeeks, trade: RivenTrade, catalog: Catalog) {
    val week = remember(weeks, trade) { weeks.history(trade.weekKey).firstOrNull() } ?: return
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(onClick = { open = true }, shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Last week's trades", style = MaterialTheme.typography.titleSmall)
                    Text("Warframe's figures for this weapon", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                val figure = MaterialTheme.typography.titleLarge
                week.unrolled?.let { Figure("unrolled median") { Platinum(it.median, style = figure) } }
                week.rolled?.let { Figure("rolled median") { Platinum(it.median, style = figure) } }
            }
        }
    }
    if (open) RivenTradeSheet(trade, weeks, catalog) { open = false }
}

/** The same in a line, for where there's less room: under a search or a weapon being listed. */
@Composable
fun RivenTradeLine(weaponSlug: String, catalog: Catalog, modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    val latest = rememberLatestWeek(weaponSlug, catalog)
    ArrivingContent(latest, modifier, gap) { (weeks, trade) -> TradeLineContent(weeks, trade, catalog) }
}

/**
 * Content that can turn up after the screen around it, like last week's figures: it opens out into
 * its space rather than pushing everything down in one jump. Kept while it closes, so it doesn't
 * vanish mid-animation. [gap] is the space above it, which goes when it does: a parent's own
 * spacing would leave a gap around nothing.
 */
@Composable
private fun <T : Any> ArrivingContent(value: T?, modifier: Modifier = Modifier, gap: Dp = 0.dp, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    AnimatedVisibility(
        visible = value != null,
        modifier = modifier,
        enter = expandVertically(Motion.move(), expandFrom = Alignment.Top) + fadeIn(Motion.fadeIn(300)),
        exit = shrinkVertically(Motion.move(250)) + fadeOut(Motion.fadeOut()),
    ) { last?.let { Box(Modifier.padding(top = gap)) { content(it) } } }
}

@Composable
private fun TradeLineContent(weeks: RivenWeeks, trade: RivenTrade, catalog: Catalog) {
    val week = remember(weeks, trade) { weeks.history(trade.weekKey).firstOrNull() } ?: return
    var open by remember { mutableStateOf(false) }
    val text = listOfNotNull(
        week.unrolled?.let { "${plat(it.median)}p unrolled" },
        week.rolled?.let { "${plat(it.median)}p rolled" },
    ).joinToString(" · ")
    Row(
        Modifier.clip(MaterialTheme.shapes.small).clickable { open = true }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Last week's median: $text",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) RivenTradeSheet(trade, weeks, catalog) { open = false }
}

/** How often a riven traded, against the week's most traded, as a short meter and its number. */
@Composable
private fun PopularityMeter(pop: Double, modifier: Modifier = Modifier) {
    // Switching between rolled and unrolled slides each meter to its new reading.
    val shown by animateFloatAsState((pop / 100).toFloat().coerceIn(0f, 1f), Motion.move(), label = "popularity")
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { shown.coerceIn(0f, 1f) }, modifier = Modifier.width(64.dp))
        RollingValue(pop.roundToInt()) {
            Text("popularity $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Everything Warframe's figures say about one weapon's rivens, and every week the app has kept. */
@Composable
fun RivenTradeSheet(trade: RivenTrade, weeks: RivenWeeks, catalog: Catalog, onDismiss: () -> Unit) {
    val navigator = LocalNavigator.current
    val weapon = catalog.tradeWeapon(trade)
    val history = remember(weeks, trade) { weeks.history(trade.weekKey) }
    val latest = history.firstOrNull()?.takeIf { it.publishedMillis == weeks.latest?.publishedMillis }
    val colors = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fadingVerticalScroll().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            var step = 0
            Row(Modifier.staggeredEntrance(step++), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                WeaponThumb(weapon?.let { catalog.weaponImage(AuctionType.RIVEN, it.slug) }, height = 56.dp)
                Column {
                    Text(catalog.tradeTitle(trade), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(trade.rivenClass?.let { "${it.label} riven" }, weeks.latest?.let { "week to ${day(it.publishedMillis)}" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            listOf("Unrolled" to latest?.unrolled, "Rolled" to latest?.rolled).forEach { (label, line) ->
                Column(Modifier.staggeredEntrance(step++), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    if (line == null) {
                        Text("None traded last week", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    } else {
                        val before = weeks.before(line)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Median", Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Platinum(line.median)
                                    change(line.median, before?.median)?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                            StatTile("Average", Modifier.weight(1f)) { Platinum(line.avg.roundToLong()) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Range", "${plat(line.min)}–${plat(line.max)}", Modifier.weight(1f))
                            StatTile("Popularity", "${line.pop.roundToInt()} of 100", Modifier.weight(1f))
                        }
                    }
                }
            }
            Column(Modifier.staggeredEntrance(step++), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Median by week", style = MaterialTheme.typography.titleSmall)
                if (history.size < 2) {
                    Text(
                        "Warframe only publishes the latest week, so the app keeps each one it sees. They'll build up here from now on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    // A table rather than a chart: a few weeks are read as numbers anyway.
                    val head = MaterialTheme.typography.labelMedium
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("Week to", style = head, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("Unrolled", style = head, color = colors.onSurfaceVariant, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                        Text("Rolled", style = head, color = colors.onSurfaceVariant, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                    }
                    history.forEach { week ->
                        HorizontalDivider(color = colors.outlineVariant)
                        Row(Modifier.padding(vertical = 8.dp)) {
                            Text(day(week.publishedMillis, "d MMM yyyy"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(week.unrolled?.let { "${plat(it.median)}p" } ?: "–", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                            Text(week.rolled?.let { "${plat(it.median)}p" } ?: "–", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
            if (weapon != null) {
                Button(
                    onClick = {
                        // The sheet slides away first, then the tab moves over to the search.
                        scope.launch { sheet.hide() }.invokeOnCompletion {
                            onDismiss()
                            navigator.auctionSearch(AuctionSearch(weaponUrlName = weapon.slug))
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.staggeredEntrance(step).fillMaxWidth(),
                ) { Text("See auctions") }
            }
        }
    }
}

// ---------------------------------------------------------------- The Riven prices tab

private enum class PricesState { MOBILE, FAILED, LOADING, READY }

private enum class RivenSort(val label: String, val needsHistory: Boolean = false) {
    POPULAR("Most traded"),
    PRICE_HIGH("Highest price"),
    PRICE_LOW("Lowest price"),
    RISING("Rising", true),
    FALLING("Falling", true),
    NAME("Name"),
}

/** Last week's riven trades from Warframe's own figures, weapon by weapon. */
@Composable
fun RivenPricesPage(catalog: Catalog) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val allWeeks by container.rivens.weeks.collectAsStateWithLifecycle()
    val platform = settings.platform
    val weeks = allWeeks?.takeIf { it.platform == platform }

    var refreshing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var rolled by rememberSaveable { mutableStateOf(true) }
    var kind by rememberSaveable { mutableStateOf<RivenClass?>(null) }
    var sort by rememberSaveable { mutableStateOf(RivenSort.POPULAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var opened by remember { mutableStateOf<RivenTrade?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    fun refresh(pulled: Boolean) {
        scope.launch {
            refreshing = pulled
            val hadData = weeks != null
            runCatching { container.rivens.refresh() }
                .onSuccess { failure = null }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    failure = it.userMessage()
                    if (pulled && hadData) container.refreshFailed()
                }
            refreshing = false
        }
    }

    // The repository loads these on its own, but a failed first try has nobody else to retry it.
    LaunchedEffect(platform) { if (weeks == null && platform != Platform.MOBILE.wire) refresh(pulled = false) }

    val latest = weeks?.latest
    val state = when {
        platform == Platform.MOBILE.wire -> PricesState.MOBILE
        latest == null && failure != null -> PricesState.FAILED
        latest == null || weeks == null -> PricesState.LOADING
        else -> PricesState.READY
    }
    // Keyed on the kind of state, as LoadContent is, so fresh figures replace old ones without a fade.
    Crossfade(state, animationSpec = Motion.fadeIn(), label = "prices") { shown ->
    when {
        shown == PricesState.MOBILE -> PullablePage {
            MessageBox(Icons.Outlined.PhoneAndroid, "No riven figures for mobile", "Warframe publishes riven trades for PC, PlayStation, Xbox and Switch only.")
        }
        shown == PricesState.FAILED -> PullablePage { ErrorBox(failure.orEmpty(), onRetry = { refresh(pulled = false) }) }
        shown == PricesState.LOADING || latest == null || weeks == null -> PullablePage { LoadingBox() }
        else -> RefreshBox(isRefreshing = refreshing, onRefresh = { refresh(pulled = true) }) {
            val hasHistory = weeks.previous != null
            val shownSort = if (sort.needsHistory && !hasHistory) RivenSort.POPULAR else sort
            val rows = remember(latest, weeks, rolled, kind, shownSort, query, catalog) {
                val filtered = latest.trades.filter { trade ->
                    trade.rerolled == rolled &&
                        (kind == null || trade.rivenClass == kind) &&
                        (query.isBlank() || catalog.tradeTitle(trade).contains(query.trim(), ignoreCase = true))
                }
                fun movement(trade: RivenTrade): Double? = weeks.before(trade)?.median?.takeIf { it > 0 }?.let { (trade.median - it) / it }
                when (shownSort) {
                    RivenSort.POPULAR -> filtered.sortedByDescending { it.pop }
                    RivenSort.PRICE_HIGH -> filtered.sortedByDescending { it.median }
                    RivenSort.PRICE_LOW -> filtered.sortedBy { it.median }
                    RivenSort.RISING -> filtered.sortedByDescending { movement(it) ?: Double.NEGATIVE_INFINITY }
                    RivenSort.FALLING -> filtered.sortedBy { movement(it) ?: Double.POSITIVE_INFINITY }
                    RivenSort.NAME -> filtered.sortedBy { catalog.tradeTitle(it) }
                }
            }
            FadingLazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
            ) {
                item {
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Warframe's figures for ${Platform.of(platform).label}, week to ${day(latest.publishedMillis, "EEE d MMM")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showHelp = true }) { Icon(Icons.Outlined.Info, "About these figures") }
                    }
                }
                item {
                    // The same filled pill as the home page's search bar.
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter weapons") },
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
                }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChoiceButtonGroup(listOf(true, false), rolled, { if (it) "Rolled" else "Unrolled" }, { rolled = it })
                        DropdownChip("Type", kind, listOf(null) + RivenClass.entries, { it?.label ?: "All" }, { kind = it }, isDefault = kind == null)
                        DropdownChip("", shownSort, RivenSort.entries.filter { hasHistory || !it.needsHistory }, { it.label }, { sort = it }, isDefault = shownSort == RivenSort.POPULAR)
                    }
                }
                if (rows.isEmpty()) item { EmptyBox("No rivens match") }
                // Rows are keyed by weapon, so a new sort or filter moves them to their places, and
                // switching rolled and unrolled keeps a weapon's row while its figures roll over.
                itemsIndexed(rows, key = { _, trade -> trade.weekKey }) { index, trade ->
                    RivenTradeRow(trade, weeks.before(trade), catalog, ListItemDefaults.segmentedShapes(index, rows.size), listItemMotion()) { opened = trade }
                }
            }
        }
    }

    }

    opened?.let { trade -> weeks?.let { RivenTradeSheet(trade, it, catalog) { opened = null } } }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Close") } },
            title = { Text("Riven prices") },
            text = {
                Text(
                    "Warframe publishes what each weapon's rivens sold for in trades between players, once a week on Mondays. These are its figures for your platform.\n\n" +
                        "Rolled rivens have been cycled at least once. Popularity is how often a riven traded, where the week's most traded is 100.\n\n" +
                        "Only the latest week is published, so the app keeps each week it sees. Rising and falling appear once it has two.",
                )
            },
        )
    }
}

@Composable
private fun RivenTradeRow(trade: RivenTrade, before: RivenTrade?, catalog: Catalog, shapes: ListItemShapes, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val weapon = catalog.tradeWeapon(trade)
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
        modifier = modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { WeaponThumb(weapon?.let { catalog.weaponImage(AuctionType.RIVEN, it.slug) }, height = 40.dp) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(trade.rivenClass?.label, "avg ${plat(trade.avg)}", "${plat(trade.min)}–${plat(trade.max)}").joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PopularityMeter(trade.pop)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                RollingPlatinum(trade.median)
                Text(change(trade.median, before?.median) ?: "median", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        },
    ) { Text(catalog.tradeTitle(trade), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

