@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.EditNotifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.wfmarket.app.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private enum class AuctionTab(val label: String, val needsAccount: Boolean = false) {
    SEARCH("Search"), RECENT("Recent"), POPULAR("Popular"), PRICES("Riven prices"), MINE("My auctions", true), BIDS("My bids", true)
}

class AuctionsViewModel(private val container: AppContainer) : ViewModel() {
    private val _lists = MutableStateFlow<Map<String, Load<List<Auction>>>>(emptyMap())
    val lists = _lists.asStateFlow()
    private val _search = MutableStateFlow(AuctionSearch())
    val search = _search.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()

    init {
        // Every tab that's been opened could show what changed, not just the one on screen.
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                if (kind == DataKind.AUCTIONS || kind == DataKind.SCOPE) _lists.value.keys.forEach { update(it) }
            }
        }
    }

    private suspend fun load(tab: String, fetch: suspend () -> List<Auction>): Boolean {
        val current = _lists.value[tab]
        if (current !is Load.Ready) _lists.value += tab to Load.Loading
        val result = runCatching { fetch() }
        result.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        if (result.isSuccess || current !is Load.Ready) _lists.value += tab to result.toLoad()
        return result.isSuccess
    }

    /** A pull on the list: shows the spinner, and says so if nothing new could be fetched. */
    fun refresh(tab: String) {
        viewModelScope.launch {
            val hadList = _lists.value[tab] is Load.Ready
            _refreshing.value += tab
            if (!fetch(tab) && hadList) container.refreshFailed()
            _refreshing.value -= tab
        }
    }

    private fun update(tab: String) {
        viewModelScope.launch { fetch(tab) }
    }

    private suspend fun fetch(tab: String): Boolean = when (tab) {
        AuctionTab.SEARCH.name -> !_search.value.canSearch || load(tab) { container.auctions.search(_search.value) }
        AuctionTab.RECENT.name -> load(tab) { container.auctions.recent() }
        AuctionTab.POPULAR.name -> load(tab) { container.auctions.popular() }
        AuctionTab.MINE.name -> load(tab) { container.auctions.mine() }
        AuctionTab.BIDS.name -> load(tab) { container.auctions.participating() }
        else -> true
    }

    fun ensureLoaded(tab: String) {
        if (tab !in _lists.value) refresh(tab)
    }

    fun search(search: AuctionSearch) {
        _search.value = search
        refresh(AuctionTab.SEARCH.name)
    }

    /** Seller status is filtered here rather than by the site, so changing it needs no new search. */
    fun setSellerStatuses(statuses: Set<String>) {
        _search.value = _search.value.copy(sellerStatuses = statuses)
    }
}

/** Browsing tabs show other players' auctions, so they can be narrowed to sellers who can trade now. */
private val AuctionTab.filtersSellers: Boolean get() = this == AuctionTab.SEARCH || this == AuctionTab.RECENT || this == AuctionTab.POPULAR

/** A name for a saved search, made while the catalogue is at hand for proper names. */
fun alertTitle(search: AuctionSearch, catalog: Catalog): String {
    val kind = when (search.type) {
        AuctionType.RIVEN -> "rivens"
        AuctionType.LICH -> "liches"
        else -> "sisters"
    }
    val subject = search.weaponUrlName?.let { catalog.weaponName(search.type, it) }
        ?: search.positiveStats.takeIf { it.isNotEmpty() }?.joinToString(" + ") { catalog.attributeName(it) }
        ?: search.element?.humanize()
    return listOfNotNull(subject, kind).joinToString(" ").replaceFirstChar { it.uppercase() }
}

/** The rest of a saved search's filters, in a line. */
fun alertSummary(search: AuctionSearch, catalog: Catalog): String = buildList {
    if (search.weaponUrlName != null && search.positiveStats.isNotEmpty()) add(search.positiveStats.joinToString(" + ") { catalog.attributeName(it) })
    if (search.negativeStats.isNotEmpty()) add("−" + search.negativeStats.joinToString { catalog.attributeName(it) })
    search.polarity?.let { add(it.humanize()) }
    if (search.weaponUrlName != null) search.element?.let { add(it.humanize()) }
    search.damageMin?.let { add("$it%+ damage") }
    search.reRollsMax?.let { add("≤ $it rolls") }
    if (search.modRankMaxed) add("Maxed")
    if (search.hasEphemera) add("Ephemera")
    catalog.quirkName(search.type, search.quirk)?.let(::add)
    when (search.buyoutPolicy) {
        "direct" -> add("Direct sell")
        "auction" -> add("Auction")
    }
    if (search.sellerStatuses.isNotEmpty()) add(StatusFilterOptions.filter { it.wire in search.sellerStatuses }.joinToString(" or ") { it.label })
    priceRangeLabel(search)?.let(::add)
}.joinToString(" · ").ifEmpty { "Any listing" }

/** A search's price range in a few characters, such as `100–300p`, `≤ 300p` or `≥ 100p`. */
fun priceRangeLabel(search: AuctionSearch): String? {
    val min = search.priceMin
    val max = search.priceMax
    return when {
        min != null && max != null -> "$min–${max}p"
        max != null -> "≤ ${max}p"
        min != null -> "≥ ${min}p"
        else -> null
    }
}

@Composable
fun AuctionsScreen() {
    val container = appContainer()
    val viewModel = containerViewModel { AuctionsViewModel(it) }
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    val alerts by container.alerts.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val tabs = AuctionTab.entries.filter { !it.needsAccount || signedIn }
    val pager = rememberPagerState(initialPage = AuctionTab.RECENT.ordinal) { tabs.size }
    var filtersOpen by remember { mutableStateOf(false) }

    // A link, notification or shortcut asked for a search, or for the filters to choose one.
    val request by container.auctionRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val asked = request ?: return@LaunchedEffect
        container.auctionRequest.value = null
        asked.search?.let(viewModel::search)
        if (asked.openFilters) filtersOpen = true
        // Clearing the request restarts this effect, which would stop a scroll started in it.
        scope.launch { pager.animateScrollToPage(AuctionTab.SEARCH.ordinal) }
    }

    fun toggleAlert(search: AuctionSearch) {
        scope.launch {
            val existing = container.alerts.alertFor(search)
            if (existing != null) {
                container.alerts.removeAlert(existing.id)
                snackbar.showSnackbar("Alert removed")
            } else {
                container.alerts.addAlert(search, alertTitle(search, catalog))
                val result = snackbar.showSnackbar("You'll be notified about new matches", actionLabel = "Manage", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) navigator.alerts()
            }
        }
    }

    LaunchedEffect(pager.currentPage, tabs.size) {
        tabs.getOrNull(pager.currentPage)?.takeIf { it != AuctionTab.PRICES }?.let { viewModel.ensureLoaded(it.name) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Auctions") },
                    actions = {
                        // Where saved searches are managed. A plain bell or a count here would read as something having arrived.
                        IconButton(onClick = navigator::alerts) { Icon(Icons.Outlined.EditNotifications, "Manage alerts") }
                        IconButton(onClick = {
                            filtersOpen = true
                            scope.launch { pager.animateScrollToPage(AuctionTab.SEARCH.ordinal) }
                        }) { Icon(Icons.Filled.FilterList, "Search filters") }
                    },
                )
                PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (signedIn) navigator.editAuction(null) else navigator.signIn() }) {
                Icon(Icons.Filled.Add, "New auction")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding), key = { tabs.getOrNull(it)?.name ?: it }) { page ->
            val tab = tabs.getOrNull(page) ?: return@HorizontalPager
            val load = lists[tab.name]
            if (tab == AuctionTab.PRICES) {
                RivenPricesPage(catalog)
            } else if (tab == AuctionTab.SEARCH && !search.canSearch) {
                MessageBox(Icons.Filled.FilterList, "Search rivens, liches and sisters", "Pick a weapon, stats or an element to see matching auctions.") {
                    FilledTonalButton(onClick = { filtersOpen = true }) { Text("Choose filters") }
                }
            } else {
                // A list's first load shows its own spinner, so the pull spinner waits until there's a list.
                RefreshBox(isRefreshing = tab.name in refreshing && load is Load.Ready, onRefresh = { viewModel.refresh(tab.name) }) {
                    LoadContent(load ?: Load.Loading, { viewModel.refresh(tab.name) }) { loaded ->
                        // The search's own filters, price included, apply to its results. The other
                        // browsing tabs share only the seller chips.
                        val auctions = when {
                            tab == AuctionTab.SEARCH -> loaded.filter(search::matches)
                            tab.filtersSellers -> loaded.filter { search.matchesSeller(it.owner.status) }
                            else -> loaded
                        }
                        FadingLazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                        ) {
                            if (tab == AuctionTab.SEARCH) {
                                item {
                                    val alertOn = alerts.alerts.any { it.search == search }
                                    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { filtersOpen = true }, modifier = Modifier.weight(1f, fill = false)) {
                                            Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("${search.type.humanize()} filters · ${auctions.size} results", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.weight(1f))
                                        FilterChip(
                                            selected = alertOn,
                                            onClick = { toggleAlert(search) },
                                            label = { Text(if (alertOn) "Notifying" else "Notify me") },
                                            leadingIcon = {
                                                Icon(if (alertOn) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone, null, Modifier.size(18.dp))
                                            },
                                        )
                                    }
                                }
                                val weapon = search.weaponUrlName
                                if (search.type == AuctionType.RIVEN && weapon != null) {
                                    item { RivenTradeLine(weapon, catalog, Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                                }
                            }
                            if (tab.filtersSellers) {
                                item { StatusFilterChips(search.sellerStatuses, viewModel::setSellerStatuses, Modifier.padding(bottom = 8.dp)) }
                            }
                            if (auctions.isEmpty()) {
                                item {
                                    when {
                                        loaded.isEmpty() -> EmptyBox("No auctions here")
                                        tab == AuctionTab.SEARCH && search.hasPriceRange -> EmptyBox(
                                            "Nothing in that price range",
                                            // The site returns at most 100 listings, so a range far from
                                            // the sort's end can miss some that exist.
                                            "${loaded.size} listings are hidden by your filters." +
                                                if (loaded.size >= 100) " The site only sends 100 at a time, so a narrower search may find more." else "",
                                        )
                                        else -> EmptyBox("No sellers with that status", "${loaded.size} auctions are hidden by the status filter.")
                                    }
                                }
                            }
                            // One grouped list, like the home page's orders: round outer corners, tight inner ones.
                            itemsIndexed(auctions, key = { _, auction -> auction.id }) { index, auction ->
                                AuctionCard(
                                    auction, catalog,
                                    shape = ListItemDefaults.segmentedShapes(index, auctions.size).shape,
                                    // Changing the seller filter slides the rest into the gaps.
                                    modifier = listItemMotion().padding(bottom = ListItemDefaults.SegmentedGap),
                                    transitionKey = tab.name,
                                ) { navigator.auction(auction.id) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        AuctionFiltersSheet(search, catalog, { filtersOpen = false }) {
            filtersOpen = false
            viewModel.search(it)
        }
    }
}

/** A stat's value with its sign and unit, such as `+104.6%`, or a multiplier such as `x0.96`. */
fun formatAttributeValue(stat: RivenStat, catalog: Catalog): String {
    val unit = when (catalog.attributesBySlug[stat.urlName]?.unit) {
        // Faction damage is a multiplier, which the game writes as x0.96, and one decimal would
        // round a small curse up to a misleading x1.0.
        "multiply" -> return "x${String.format(Locale.US, "%.2f", stat.value)}"
        "percent" -> "%"
        "seconds" -> "s"
        else -> ""
    }
    val sign = if (stat.value > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", stat.value)}$unit"
}

/**
 * A riven's stats as a table: the stat, then its value at the end in the color of whether it
 * helps. The printed sign keeps that readable without hue. With [rolls], each value has its grade
 * after it, and [detailed] adds a bar for where in its range each stat rolled.
 */
@Composable
fun RivenStats(attributes: List<RivenStat>, catalog: Catalog, modifier: Modifier = Modifier, rolls: RivenRolls? = null, detailed: Boolean = false) {
    val semantic = LocalSemanticColors.current
    val note = MaterialTheme.typography.labelSmall
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (detailed && rolls != null) 12.dp else 4.dp)) {
        attributes.forEachIndexed { index, stat ->
            val roll = rolls?.stats?.getOrNull(index)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        catalog.attributeName(stat.urlName),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The grade sits close to its value, so the two read as one.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatAttributeValue(stat, catalog),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (stat.positive) semantic.positive else semantic.negative,
                        )
                        if (rolls != null) RivenGradeLabel(roll)
                    }
                }
                if (detailed && roll != null) {
                    val (worst, best) = rollEnds(stat, roll, catalog)
                    when {
                        roll.outOfRange -> Text("It can only roll $worst to $best, so this may be mistyped.", style = note, color = noteColor)
                        roll.tooRounded -> Text("Too small at rank ${rolls.rank} to grade. It rolls $worst to $best.", style = note, color = noteColor)
                        else -> RivenRollBar(roll, worst, best, index = index)
                    }
                }
            }
        }
    }
}

@Composable
fun AuctionItemSummary(item: AuctionItem, catalog: Catalog) {
    if (item.type == AuctionType.RIVEN && item.attributes.isNotEmpty()) RivenStats(item.attributes, catalog)
    AuctionDetailsLine(item, catalog, Modifier.padding(top = 8.dp))
}

/** The rest of what an auction is, as one line of text: fewer boxes than a tag for each. */
@Composable
fun AuctionDetailsLine(item: AuctionItem, catalog: Catalog, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = with(LocalDensity.current) { style.fontSize.toDp() } * 1.2f
    val parts: List<@Composable () -> Unit> = buildList {
        fun text(value: String) = add { Text(value, style = style, color = color, maxLines = 1) }
        when (item.type) {
            AuctionType.RIVEN -> {
                item.masteryLevel?.let { text("MR $it") }
                item.modRank?.let { text("Rank $it") }
                item.reRolls?.let { text(if (it == 1) "1 roll" else "$it rolls") }
                item.polarity?.let { polarity ->
                    add {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            // A little over the label's font size, so the symbol stands as tall as the capitals beside it.
                            PolarityIcon(polarity, Modifier.size(iconSize), tint = color)
                            Text(polarity.humanize(), style = style, color = color, maxLines = 1)
                        }
                    }
                }
            }
            else -> {
                item.element?.let { text(it.humanize()) }
                item.damage?.let { text("$it% damage") }
                if (item.havingEphemera == true) text("Ephemera")
                catalog.quirkName(item.type, item.quirk)?.let { text(it) }
            }
        }
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text("·", style = style, color = color)
            part()
        }
    }
}

/**
 * What the auction goes for, as the home page shows an order's price: the figure, and under it what
 * kind of price it is. A bidding auction with a buyout names the buyout there too.
 */
@Composable
private fun AuctionPriceColumn(auction: Auction) {
    val main = if (auction.isDirectSell) auction.buyoutPrice ?: auction.startingPrice else auction.topBid ?: auction.startingPrice
    val label = when {
        auction.isDirectSell -> "buyout"
        auction.topBid != null -> "top bid"
        else -> "starting bid"
    } + (auction.buyoutPrice?.takeIf { !auction.isDirectSell }?.let { " · ${it}p buyout" } ?: "")
    Figure(label, alignment = Alignment.End) { Platinum(main) }
}

/**
 * An auction in a list, the same wherever one is listed. In a grouped list, [shape] is its place's.
 */
@Composable
fun AuctionCard(
    auction: Auction,
    catalog: Catalog,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    /** Names the list, so the picture can fly from this card to the auction's page and back. */
    transitionKey: String? = null,
    onClick: () -> Unit,
) {
    val item = auction.item
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val openedFrom = LocalOpenedFrom.current
    val auctions = appContainer().auctions
    val shared = transitionKey?.let { "$it:${auction.id}" }
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier.fillMaxWidth().clip(shape).combinedClickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = {
                openedFrom.key = shared
                auctions.handoff = auction
                onClick()
            },
        ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                WeaponThumb(catalog.weaponImage(item.type, item.weaponUrlName), height = 40.dp, tile = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(catalog.weaponName(item.type, item.weaponUrlName), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.name?.replaceFirstChar { it.uppercase() }, relativeTime(auction.updated).ifEmpty { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AuctionPriceColumn(auction)
            }
            if (item.type == AuctionType.RIVEN && item.attributes.isNotEmpty()) {
                // On the card's own background: one color per card.
                RivenStats(item.attributes, catalog, Modifier.fillMaxWidth())
            }
            AuctionDetailsLine(item, catalog)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The seller is a way to their profile of its own, apart from the card around it.
                val navigator = LocalNavigator.current
                Row(
                    Modifier.weight(1f, fill = false).clip(MaterialTheme.shapes.small)
                        .clickable(enabled = auction.owner.slug != null, onClickLabel = "Open profile") { auction.owner.slug?.let(navigator::user) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UserAvatar(assetUrl(auction.owner.avatar), auction.owner.ingameName, size = 24.dp)
                    Text(auction.owner.ingameName.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    StatusLabel(auction.owner.status)
                    Text("+${auction.owner.reputation}", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
                if (!auction.visible) SmallTag("Hidden")
                if (auction.closed) SmallTag("Closed")
            }
        }
    }
}

