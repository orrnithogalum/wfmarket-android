@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.Item
import com.wfmarket.api.model.Order
import com.wfmarket.api.model.PrimeResurgence
import com.wfmarket.api.model.OrderType
import com.wfmarket.api.model.Showcase
import com.wfmarket.api.model.assetUrl
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.humanize
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Browse groups for the catalogue. A group matches items carrying any of its tags and none of its
 * [excluded] ones. Tags describe what an item is for as well as what it is: a Warframe's mods are
 * tagged `warframe`, and a companion's mods `sentinel` or `kubrow`, so mods are kept to Mods.
 */
private enum class Category(val label: String, val tags: Set<String>, val icon: ImageVector, val excluded: Set<String> = emptySet()) {
    SETS("Sets", setOf("set"), Icons.Filled.Layers),
    PRIME("Prime", setOf("prime"), Icons.Filled.Diamond, setOf("mod")),
    WARFRAMES("Warframes", setOf("warframe"), Icons.Filled.SportsMartialArts, setOf("mod", "arcane_enhancement")),
    WEAPONS("Weapons", setOf("weapon"), Icons.Filled.GpsFixed, setOf("mod")),
    MODS("Mods", setOf("mod"), Icons.Filled.Extension),
    // Peculiar mods carry the arcane tag too, but they're mods.
    ARCANES("Arcanes", setOf("arcane_enhancement"), Icons.Filled.AutoFixHigh, setOf("mod")),
    RELICS("Relics", setOf("relic"), Icons.Filled.Hexagon),
    COMPANIONS("Companions", setOf("sentinel", "kubrow", "kavat", "companion"), Icons.Filled.Pets, setOf("mod")),
    SCENES("Scenes", setOf("scene"), Icons.Filled.Landscape),
    // Scenes are tagged misc as well, and have a group of their own.
    MISC("Misc", setOf("misc", "fish", "ayatan_sculpture", "gem", "plant"), Icons.Filled.Category, setOf("scene"));

    fun matches(item: Item): Boolean = item.tags.any { it in tags } && item.tags.none { it in excluded }
}

/** Each category gets its own expressive shape, so the row reads as a set of distinct places. */
private val Category.shape: RoundedPolygon
    get() = when (this) {
        Category.SETS -> MaterialShapes.Cookie9Sided
        Category.PRIME -> MaterialShapes.Gem
        Category.WARFRAMES -> MaterialShapes.Arch
        Category.WEAPONS -> MaterialShapes.SoftBurst
        Category.MODS -> MaterialShapes.Cookie4Sided
        Category.ARCANES -> MaterialShapes.Sunny
        Category.RELICS -> MaterialShapes.Cookie6Sided
        Category.COMPANIONS -> MaterialShapes.Clover4Leaf
        Category.SCENES -> MaterialShapes.Square
        Category.MISC -> MaterialShapes.Flower
    }

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // The chosen tile grows a little and takes the accent color, both eased rather than snapped.
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.move(250), label = "categoryScale")
    val fill by animateColorAsState(if (selected) colors.primary else colors.secondaryContainer, Motion.fadeIn(), label = "categoryFill")
    val ink by animateColorAsState(if (selected) colors.onPrimary else colors.onSecondaryContainer, Motion.fadeIn(), label = "categoryInk")
    val label by animateColorAsState(if (selected) colors.primary else colors.onSurface, Motion.fadeIn(), label = "categoryLabel")
    Column(
        // At least the icon's width plus a margin, and wider when a larger font needs it, so a name is never cut short.
        Modifier.widthIn(min = 76.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(60.dp).scale(scale).clip(category.shape.toShape()).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.icon, null, Modifier.size(26.dp), tint = ink)
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelMedium,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The home page's lists are rounded cards split into rows, like the settings. */
@Composable
private fun homeRowColors() = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L
private const val ORDERS_UPDATE_MILLIS = 30_000L

class MarketViewModel(private val container: AppContainer) : ViewModel() {
    private val _showcase = MutableStateFlow<Showcase?>(null)
    val showcase = _showcase.asStateFlow()
    private val _resurgence = MutableStateFlow<PrimeResurgence?>(null)
    val resurgence = _resurgence.asStateFlow()
    private val _recent = MutableStateFlow<Load<List<Order>>>(Load.Loading)
    val recent = _recent.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private var lastRefreshMillis = 0L
    private var resurgenceEnd: Job? = null

    init {
        refresh()
        // A failed load retries from its error box; this covers what was on screen already.
        viewModelScope.launch {
            container.connectivity.reconnected.collect { if (_recent.value is Load.Ready) refresh() }
        }
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                when (kind) {
                    DataKind.ORDERS -> updateOrders()
                    DataKind.SCOPE -> refresh()
                    else -> Unit
                }
            }
        }
    }

    /** Quietly swaps in the newest orders. A failed update just waits for the next one. */
    fun updateOrders() {
        viewModelScope.launch {
            runCatching { container.market.recentOrders() }.onSuccess { _recent.value = Load.Ready(it) }
        }
    }

    /** This screen can stay alive in the background for days, so it catches up when it's seen again. */
    fun refreshIfOld() {
        if (System.currentTimeMillis() - lastRefreshMillis > REFRESH_INTERVAL_MILLIS) refresh()
    }

    fun refresh() {
        lastRefreshMillis = System.currentTimeMillis()
        viewModelScope.launch {
            _refreshing.value = _recent.value !is Load.Loading
            launch { runCatching { container.market.showcase() }.onSuccess { _showcase.value = it } }
            launch {
                runCatching { container.market.primeResurgence() }
                    .onSuccess { current ->
                        _resurgence.value = current
                        resurgenceEnd?.cancel()
                        resurgenceEnd = current?.let {
                            viewModelScope.launch {
                                delay(it.expiresAtMillis - System.currentTimeMillis())
                                refresh()
                            }
                        }
                    }
                    .onFailure { android.util.Log.w("MarketViewModel", "Couldn't load the Prime Resurgence", it) }
            }
            val hadOrders = _recent.value is Load.Ready
            val updated = _recent.accept(runCatching { container.market.recentOrders() })
            if (hadOrders && !updated) container.refreshFailed()
            _refreshing.value = false
        }
    }
}

/** Search is ranked: name prefix, then word prefix, then anywhere. */
private fun searchItems(catalog: Catalog, query: String): List<Item> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    val terms = needle.split(' ').filter { it.isNotBlank() }
    return catalog.items.mapNotNull { item ->
        val name = catalog.itemName(item).lowercase()
        if (!terms.all { name.contains(it) }) return@mapNotNull null
        val rank = when {
            name.startsWith(needle) -> 0
            name.split(' ').any { it.startsWith(terms.first()) } -> 1
            else -> 2
        }
        Triple(rank, name, item)
    }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
}

@Composable
fun MarketScreen() {
    val container = appContainer()
    val viewModel = containerViewModel { MarketViewModel(it) }
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val catalogError by container.catalog.loadError.collectAsStateWithLifecycle()
    val catalogLoading by container.catalog.loading.collectAsStateWithLifecycle()
    val isOnline by container.connectivity.online.collectAsStateWithLifecycle()
    val showcase by viewModel.showcase.collectAsStateWithLifecycle()
    val resurgence by viewModel.resurgence.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val onlineCount by container.account.onlineCount.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LifecycleResume(viewModel::refreshIfOld)

    // Only while the screen is showing and there's a network to ask.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(ORDERS_UPDATE_MILLIS)
                if (container.connectivity.online.value) viewModel.updateOrders()
            }
        }
    }

    var category by rememberSaveable { mutableStateOf<Category?>(null) }

    val scope = rememberCoroutineScope()
    val searchState = rememberSearchBarState()
    val searchText = rememberTextFieldState()
    val query = searchText.text.toString()
    var searchKind by rememberSaveable { mutableStateOf<Category?>(null) }
    val closeSearch: () -> Unit = { scope.launch { searchState.animateToCollapsed() } }
    // However search was closed, it opens empty next time.
    LaunchedEffect(searchState.currentValue) {
        if (searchState.currentValue == SearchBarValue.Collapsed) {
            searchText.clearText()
            searchKind = null
        }
    }
    val searchField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = searchText,
            searchBarState = searchState,
            onSearch = {},
            placeholder = { Text("Search ${if (catalog.isLoaded) catalog.items.size.toString() + " items" else "items"}") },
            leadingIcon = {
                if (searchState.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = closeSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                } else {
                    Icon(Icons.Filled.Search, null)
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = searchText::clearText) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
        )
    }

    Scaffold(contentWindowInsets = WindowInsets.statusBars) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(searchState, searchField, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            // Opens over the page in its own window, so the page never gets pushed around.
            ExpandedFullScreenSearchBar(searchState, searchField) {
                if (!catalog.isLoaded) {
                    when (val error = catalogError) {
                        null -> LoadingBox(Modifier.height(240.dp))
                        else -> ErrorBox(error, onRetry = container.catalog::retry)
                    }
                } else {
                    ItemSearchContent(catalog, query, searchKind, { searchKind = it }) { item ->
                        closeSearch()
                        navigator.item(item.slug)
                    }
                }
            }

            // The first load has nothing cached to show, so it says what's happening.
            AnimatedVisibility(catalogLoading && !catalog.isLoaded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading items…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                MarketContent(
                    catalog = catalog,
                    catalogError = catalogError,
                    onRetryCatalog = container.catalog::retry,
                    showcase = showcase,
                    resurgence = resurgence,
                    recent = recent,
                    onRetry = viewModel::refresh,
                    category = category,
                    onCategory = { category = if (category == it) null else it },
                    online = onlineCount?.connections?.takeIf { isOnline },
                )
            }
        }
    }
}

@Composable
private fun MarketContent(
    catalog: Catalog,
    catalogError: String?,
    onRetryCatalog: () -> Unit,
    showcase: Showcase?,
    resurgence: PrimeResurgence?,
    recent: Load<List<Order>>,
    onRetry: () -> Unit,
    category: Category?,
    onCategory: (Category) -> Unit,
    online: Int?,
) {
    val navigator = LocalNavigator.current
    var orderType by rememberSaveable { mutableStateOf(OrderType.SELL) }

    FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        // Without the catalogue nothing below can be named, so one message covers the whole page.
        if (catalogError != null && !catalog.isLoaded) {
            item {
                ErrorBox(catalogError, onRetry = {
                    onRetryCatalog()
                    onRetry()
                })
            }
            return@FadingLazyColumn
        }

        // Every item is keyed, so switching between the home page and a category fades one set of
        // rows out and the other in, and what stays slides to its new place.
        // Always the first row, empty until the count arrives. Added later above the categories,
        // it would be scrolled up under the search bar, since a list keeps its first row in place.
        item(key = "online") {
            var shown by remember { mutableIntStateOf(0) }
            if (online != null && online > 0) shown = online
            AnimatedVisibility(
                online != null && online > 0,
                modifier = listItemMotion(),
                enter = expandVertically(Motion.move(), expandFrom = Alignment.Top) + fadeIn(Motion.fadeIn(300)),
                exit = shrinkVertically(Motion.move(250)) + fadeOut(Motion.fadeOut()),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor("online")))
                        Text(
                            "${java.text.NumberFormat.getIntegerInstance().format(shown)} tenno online",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "categories") {
            LazyRow(
                listItemMotion(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(Category.entries) { entry -> CategoryTile(entry, category == entry) { onCategory(entry) } }
            }
        }

        if (category != null) {
            val items = catalog.items
                .filter(category::matches)
                .sortedBy { catalog.itemName(it) }
            if (!catalog.isLoaded) {
                item(key = "category-loading") { LoadingBox(listItemMotion().height(240.dp)) }
                return@FadingLazyColumn
            }
            item(key = "category-${category.name}") {
                Text("${category.label} · ${items.size}", style = MaterialTheme.typography.titleLarge, modifier = listItemMotion().padding(horizontal = 16.dp, vertical = 12.dp))
            }
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                ItemRow(catalog, item, shapes = ListItemDefaults.segmentedShapes(index, items.size), modifier = listItemMotion()) { navigator.item(item.slug) }
            }
            return@FadingLazyColumn
        }

        item(key = "featured") {
            // The placeholder gives way to the carousel by fading, in the same space.
            Crossfade(catalog.isLoaded, listItemMotion(), animationSpec = Motion.fadeIn(), label = "featured") { loaded ->
                if (loaded) FeaturedCarousel(catalog, showcase, resurgence) else CarouselSkeleton()
            }
        }

        item(key = "latest") {
            Row(
                listItemMotion().fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Latest orders", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ChoiceButtonGroup(listOf(OrderType.SELL, OrderType.BUY), orderType, { if (it == OrderType.SELL) "Sell" else "Buy" }, { orderType = it })
            }
        }

        when (recent) {
            Load.Loading -> item(key = "orders-loading") { LoadingBox(listItemMotion().height(240.dp)) }
            is Load.Failed -> item(key = "orders-failed") { ErrorBox(recent.message, listItemMotion(), onRetry = onRetry) }
            is Load.Ready -> {
                val orders = recent.value.filter { it.type == orderType }
                itemsIndexed(orders, key = { _, order -> order.id }) { index, order ->
                    RecentOrderRow(catalog, order, ListItemDefaults.segmentedShapes(index, orders.size), listItemMotion()) {
                        catalog.itemsById[order.itemId]?.let { navigator.item(it.slug) }
                    }
                }
            }
        }
    }
}

/**
 * The home search, laid out as the weapon picker is: chips to narrow it to one kind of item, and the
 * results in one rounded group. With a kind chosen and nothing typed, it lists that whole kind.
 */
@Composable
private fun ItemSearchContent(
    catalog: Catalog,
    query: String,
    kind: Category?,
    onKind: (Category?) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val results = remember(query, catalog, kind) {
        when {
            query.isNotBlank() -> searchItems(catalog, query).let { list -> if (kind == null) list else list.filter(kind::matches) }
            kind != null -> catalog.items.filter(kind::matches).sortedBy { catalog.itemName(it) }
            else -> emptyList()
        }
    }
    val shown = results
    FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "kinds") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = kind == null, onClick = { onKind(null) }, label = { Text("All") })
                Category.entries.forEach { entry ->
                    FilterChip(selected = kind == entry, onClick = { onKind(if (kind == entry) null else entry) }, label = { Text(entry.label) })
                }
            }
        }
        // Nothing to list until something's typed or a kind is picked.
        if (query.isBlank() && kind == null) return@FadingLazyColumn
        item(key = "count") {
            Text(
                "${results.size} ${if (results.size == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = listItemMotion().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (shown.isEmpty()) item(key = "empty") { EmptyBox(if (query.isBlank()) "Nothing here" else "No items match “$query”") }
        itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
            SegmentedListItem(
                onClick = { onOpen(item) },
                shapes = ListItemDefaults.segmentedShapes(index, shown.size),
                colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainerHighest),
                modifier = listItemMotion().padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap),
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = { ItemThumb(catalog.art(item)) },
                supportingContent = {
                    // What the row's kind doesn't already say: a mod's rarity and what it fits, a part's ducats.
                    Text(
                        listOfNotNull(
                            Category.entries.firstOrNull { it != Category.SETS && it != Category.PRIME && it.matches(item) }?.takeIf { it != kind }?.label,
                            item.rarity?.takeIf { "mod" in item.tags || "arcane_enhancement" in item.tags }?.humanize(),
                            ItemFits.firstOrNull { it in item.tags }?.takeIf { "mod" in item.tags }?.humanize(),
                            item.ducats?.let { "$it ducats" },
                            if (item.vaulted == true) "vaulted" else null,
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ) { Text(catalog.itemName(item), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}



/** What a mod is for, as its tags say. */
private val ItemFits = listOf("warframe", "primary", "secondary", "melee", "sentinel", "kubrow", "kavat", "archwing", "necramech", "railjack", "parazon", "stance", "aura")


private enum class FeaturedKind(val label: String) { PRIME_ACCESS("Prime Access"), RESURGENCE("Prime Resurgence") }

private data class FeaturedCard(
    val kind: FeaturedKind,
    val title: String,
    /** Art that fills the card. */
    val background: String? = null,
    /** A cut-out item image, centred on a tinted card. */
    val foreground: String? = null,
    val slug: String? = null,
    val link: String? = null,
)

@Composable
private fun FeaturedCarousel(catalog: Catalog, showcase: Showcase?, resurgence: PrimeResurgence?) {
    val navigator = LocalNavigator.current
    val openUrl = rememberOpenUrl()
    val cards = remember(catalog, showcase, resurgence) { featuredCards(catalog, showcase, resurgence) }
    if (cards.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val accessName = catalog.primeAccess.firstOrNull()?.let { catalog.itemName(it).removeSuffix(" Set") }
    Column(Modifier.padding(vertical = 8.dp)) {
        accessName?.let {
            Text("$it Access", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }
        resurgence?.let {
            val names = it.headline?.substringAfter(':', "")?.trim()?.trimEnd('!')?.takeIf(String::isNotEmpty)
            Text(
                listOfNotNull("Prime Resurgence", names).joinToString(": ") + " · until " +
                    DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it.expiresAtMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        val carousel = rememberCarouselState { cards.size }
        HorizontalMultiBrowseCarousel(
            state = carousel,
            preferredItemWidth = 230.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) { index ->
            // The list can shrink while the carousel is on screen, when a Prime Resurgence ends.
            val card = cards.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
            val tint = if (card.kind == FeaturedKind.PRIME_ACCESS) colors.primaryContainer else colors.tertiaryContainer
            val drawInfo = carouselItemDrawInfo
            Box(
                Modifier.fillMaxSize()
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.verticalGradient(listOf(tint, colors.surfaceContainerHigh)))
                    .clickable {
                        // Any card opens, narrowed or not: a tap on a card means that card.
                        when {
                            card.slug != null -> navigator.item(card.slug)
                            card.link != null -> openUrl(card.link)
                        }
                    },
            ) {
                card.background?.let { RemoteImage(it, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, spinner = true) }
                card.foreground?.let { RemoteImage(it, Modifier.fillMaxSize().padding(8.dp), spinner = true) }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)),
                    ),
                )
                // The carousel narrows cards by masking them, which would slice through this text.
                // So it stays with the card's left edge and fades before the card gets too narrow.
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val shown = drawInfo.size / drawInfo.maxSize.coerceAtLeast(1f)
                        alpha = ((shown - TEXT_FADE_START) / (1f - TEXT_FADE_START)).coerceIn(0f, 1f)
                        translationX = drawInfo.maskRect.left
                    },
                ) {
                    Text(
                        card.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (card.kind == FeaturedKind.PRIME_ACCESS) colors.onPrimary else colors.onTertiary,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(CircleShape)
                            .background(if (card.kind == FeaturedKind.PRIME_ACCESS) colors.primary else colors.tertiary)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Text(
                        card.title,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** How far a card can narrow, as a fraction of full width, before its text starts to fade. */
private const val TEXT_FADE_START = 0.85f


/** Holds the carousel's place while the catalogue loads, so the page doesn't jump when it arrives. */
@Composable
private fun CarouselSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBox(Modifier.weight(5f).fillMaxHeight(), MaterialTheme.shapes.extraLarge)
        SkeletonBox(Modifier.weight(3f).fillMaxHeight(), MaterialTheme.shapes.extraLarge)
        SkeletonBox(Modifier.weight(1f).fillMaxHeight(), MaterialTheme.shapes.extraLarge)
    }
}

private fun featuredCards(catalog: Catalog, showcase: Showcase?, resurgence: PrimeResurgence?): List<FeaturedCard> = buildList {
    // The site's showcase art is only used when it's about the current release, since it goes stale.
    val siteArt = showcase?.items.orEmpty().associate { it.item to it.background }
    catalog.primeAccess.forEach { item ->
        val art = siteArt[item.id]
        add(
            FeaturedCard(
                kind = FeaturedKind.PRIME_ACCESS,
                title = catalog.itemName(item),
                background = assetUrl(art),
                foreground = if (art == null) catalog.art(item).full else null,
                slug = item.slug,
            ),
        )
    }
    if (resurgence != null) {
        resurgence.bannerUrl?.let { banner ->
            add(
                FeaturedCard(
                    kind = FeaturedKind.RESURGENCE,
                    title = resurgence.headline?.substringAfter(':')?.trim()?.trimEnd('!') ?: "Prime Resurgence",
                    background = banner,
                    link = resurgence.link,
                ),
            )
        }
        catalog.resurgenceItems(resurgence).forEach { item ->
            add(FeaturedCard(FeaturedKind.RESURGENCE, catalog.itemName(item), foreground = catalog.art(item).full, slug = item.slug))
        }
    }
    // Only if the catalogue somehow has no Prime release to go on.
    if (isEmpty()) {
        showcase?.items.orEmpty().forEach { entry ->
            catalog.itemsById[entry.item]?.let { item ->
                add(FeaturedCard(FeaturedKind.PRIME_ACCESS, catalog.itemName(item), background = assetUrl(entry.background), slug = item.slug))
            }
        }
    }
}

/** An item in a list. Given [shapes], it's a row of a rounded card, as on the home page. */
@Composable
fun ItemRow(catalog: Catalog, item: Item, modifier: Modifier = Modifier, shapes: ListItemShapes? = null, onClick: () -> Unit) {
    val headline: @Composable () -> Unit = {
        Text(catalog.itemName(item), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val tags: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
            item.tags.take(3).forEach { SmallTag(it.replace('_', ' ')) }
            if (item.vaulted == true) SmallTag("vaulted", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
    val ducats: @Composable () -> Unit = {
        item.ducats?.let { Text("$it d", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (shapes != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = homeRowColors(),
            modifier = modifier.padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap),
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = { ItemThumb(catalog.art(item)) },
            supportingContent = tags,
            trailingContent = ducats,
            content = headline,
        )
        return
    }
    ClickableRow(onClick, modifier) {
        ItemThumb(catalog.art(item))
        Column(Modifier.weight(1f)) {
            headline()
            tags()
        }
        ducats()
    }
}

@Composable
private fun RecentOrderRow(catalog: Catalog, order: Order, shapes: ListItemShapes, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val item = catalog.itemsById[order.itemId]
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        colors = homeRowColors(),
        modifier = modifier.padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { if (item != null) ItemThumb(catalog.art(item)) else ItemThumb(null as String?) },
        supportingContent = {
            // The seller opens their profile; the rest of the row opens the item.
            val navigator = LocalNavigator.current
            val slug = order.user?.slug
            Row(
                Modifier.clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = slug != null, onClickLabel = "Open profile") { slug?.let(navigator::user) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(order.user?.ingameName.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                StatusLabel(order.user?.status)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Platinum(order.platinum)
                Text(relativeTime(order.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        Text(item?.let(catalog::itemName) ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
