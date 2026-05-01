@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
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
import java.util.Locale

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
        Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
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

