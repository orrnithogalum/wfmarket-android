@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class ItemViewModel(private val container: AppContainer, slug: String) : ViewModel() {
    private val _slug = MutableStateFlow(slug)
    val slug = _slug.asStateFlow()
    private val _item = MutableStateFlow<Load<Item>>(Load.Loading)
    val item = _item.asStateFlow()
    private val _setItems = MutableStateFlow<List<Item>>(emptyList())
    val setItems = _setItems.asStateFlow()
    private val _orders = MutableStateFlow<Load<List<Order>>>(Load.Loading)
    val orders = _orders.asStateFlow()
    private val _statistics = MutableStateFlow<Load<ItemStatistics>>(Load.Loading)
    val statistics = _statistics.asStateFlow()
    private val _drops = MutableStateFlow<Load<List<DropSource>>>(Load.Loading)
    val drops = _drops.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Parts already visited, so going back to one shows it at once while it updates. */
    private val visited = mutableMapOf<String, VisitedPart>()
    private var partJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                when (kind) {
                    DataKind.ORDERS -> launch { fetchOrders() }
                    DataKind.SCOPE -> load()
                    else -> Unit
                }
            }
        }
    }

    /** Switches to another part of the set in place: only what differs between parts reloads. */
    fun select(part: Item) {
        if (part.slug == _slug.value) return
        remember()
        _slug.value = part.slug
        val known = visited[part.slug]
        _item.value = Load.Ready(known?.item ?: part)
        _orders.value = known?.orders ?: Load.Loading
        _statistics.value = known?.statistics ?: Load.Loading
        _drops.value = known?.drops ?: Load.Loading
        loadPart()
    }

    private fun remember() {
        val item = (_item.value as? Load.Ready)?.value ?: return
        visited[_slug.value] = VisitedPart(item, _orders.value, _statistics.value, _drops.value)
    }

    fun load() {
        viewModelScope.launch {
            runCatching { container.market.itemSet(_slug.value) }.onSuccess { _setItems.value = it.items }
        }
        loadPart()
    }

    private fun loadPart() {
        partJob?.cancel()
        partJob = viewModelScope.launch { fetchPart() }
    }

    private suspend fun fetchPart(): Boolean = coroutineScope {
        listOf(
            async { fetchItem() },
            async { fetchOrders() },
            async { fetchStatistics() },
            async { fetchDrops() },
        ).awaitAll().all { it }
    }

    private suspend fun fetchItem() = _item.accept(runCatching { container.market.item(_slug.value) })

    private suspend fun fetchOrders() = _orders.accept(runCatching { container.market.orders(_slug.value) })

    private suspend fun fetchStatistics() = _statistics.accept(runCatching { container.market.statistics(_slug.value) })

    private suspend fun fetchDrops() = _drops.accept(
        // Items with no known sources answer 404, which simply means an empty list here.
        runCatching { container.market.dropSources(_slug.value) }.recoverCatching { failure ->
            if ((failure as? com.wfmarket.api.WfmException)?.kind == com.wfmarket.api.WfmErrorKind.NOT_FOUND) emptyList() else throw failure
        },
    )

    fun refresh() {
        partJob?.cancel()
        partJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                if (!fetchPart()) container.refreshFailed()
            } finally {
                _refreshing.value = false
            }
        }
    }

    suspend fun deleteOrder(order: Order) {
        container.market.deleteOrder(order.id)
        _orders.update { state -> if (state is Load.Ready) Load.Ready(state.value.filterNot { it.id == order.id }) else state }
    }
}

private class VisitedPart(
    val item: Item,
    val orders: Load<List<Order>>,
    val statistics: Load<ItemStatistics>,
    val drops: Load<List<DropSource>>,
)

fun <T> Result<T>.toLoad(): Load<T> = fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })

@Composable
fun ItemScreen(routeSlug: String) {
    val container = appContainer()
    val viewModel = containerViewModel(key = "item:$routeSlug") { ItemViewModel(it, routeSlug) }
    val slug by viewModel.slug.collectAsStateWithLifecycle()
    val itemLoad by viewModel.item.collectAsStateWithLifecycle()
    val setItems by viewModel.setItems.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val drops by viewModel.drops.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val openUrl = rememberOpenUrl()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pager = rememberPagerState { ItemTab.entries.size }
    var composer by remember { mutableStateOf<OrderComposer?>(null) }

    val ready = (itemLoad as? Load.Ready)?.value
    val i18n = ready?.i18n?.localized(catalog.language)
    // The catalogue already knows the name, so the title is there before the item has loaded.
    val title = i18n?.name ?: catalog.itemsBySlug[slug]?.let(catalog::itemName).orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    i18n?.wikiLink?.let { link ->
                        TextButton(onClick = { openUrl(link) }) { Text("Wiki") }
                    }
                    IconButton(onClick = { openUrl("https://warframe.market/items/$slug") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on warframe.market")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (ready != null && pager.currentPage == ItemTab.ORDERS.ordinal) {
                ExtendedFloatingActionButton(
                    onClick = { if (signedIn) composer = OrderComposer(ready, null) else navigator.signIn() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Place order") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val load = itemLoad) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> RefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh) {
                    PullablePage { ErrorBox(load.message, onRetry = viewModel::load) }
                }
                is Load.Ready -> CollapsingHeaderLayout(header = { ItemHeader(load.value, setItems, catalog, orders, viewModel::select) }) {
                    PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                        ItemTab.entries.forEach { tab ->
                            Tab(
                                selected = pager.currentPage == tab.ordinal,
                                onClick = { scope.launch { pager.animateScrollToPage(tab.ordinal) } },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                    HorizontalPager(pager, Modifier.weight(1f), key = { it }) { page ->
                      // A part still loading shows its own spinner, so the pull spinner waits for it.
                      RefreshBox(isRefreshing = refreshing && orders is Load.Ready, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                        when (ItemTab.entries[page]) {
                            ItemTab.ORDERS -> OrdersTab(
                                item = load.value,
                                orders = orders,
                                meId = me?.id,
                                catalog = catalog,
                                onRetry = viewModel::refresh,
                                onEdit = { composer = OrderComposer(load.value, it) },
                                onDelete = { order ->
                                    scope.launch {
                                        runCatching { viewModel.deleteOrder(order) }
                                            .onSuccess { snackbar.showSnackbar("Order removed") }
                                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                                    }
                                },
                                onClose = { order, quantity ->
                                    scope.launch {
                                        runCatching { container.market.closeOrder(order.id, quantity) }
                                            .onSuccess { snackbar.showSnackbar("Added to your trading history") }
                                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                                    }
                                },
                                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                            )
                            ItemTab.STATISTICS -> StatisticsTab(load.value, statistics, viewModel::load)
                            ItemTab.INFO -> InfoTab(load.value, setItems, catalog, viewModel::select)
                        }
                      }
                    }
                }
            }
        }
    }

    composer?.let { current ->
        OrderEditorSheet(
            item = current.item,
            existing = current.existing,
            catalog = catalog,
            bestSell = (orders as? Load.Ready)?.value.bestPrice(OrderType.SELL, current.item),
            bestBuy = (orders as? Load.Ready)?.value.bestPrice(OrderType.BUY, current.item),
            onDismiss = { composer = null },
            onSaved = { message ->
                composer = null
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
}

private data class OrderComposer(val item: Item, val existing: Order?)

private enum class ItemTab(val label: String) { ORDERS("Orders"), STATISTICS("Statistics"), INFO("Info") }

/**
 * Prices only compare within one variant: a rank 5 arcane costs far more than a rank 0 one. So the
 * summary uses the base variant, which is what most orders are for.
 */
private fun List<Order>?.bestPrice(type: String, item: Item): Int? {
    val active = this?.filter { order ->
        order.type == type &&
            order.user?.status in setOf("ingame", "online") &&
            (item.maxRank == null || order.rank == 0) &&
            (item.subtypes.isEmpty() || order.subtype == item.subtypes.first())
    }.orEmpty()
    return if (type == OrderType.SELL) active.minOfOrNull { it.platinum } else active.maxOfOrNull { it.platinum }
}

@Composable
private fun ItemHeader(item: Item, setItems: List<Item>, catalog: Catalog, orders: Load<List<Order>>, onSelectPart: (Item) -> Unit) {
    val list = (orders as? Load.Ready)?.value
    val loading = orders is Load.Loading
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ItemThumb(catalog.art(item), size = 64.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val variant = listOfNotNull(
                    item.maxRank?.let { "rank 0" },
                    item.subtypes.firstOrNull()?.lowercase(),
                ).joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }
                StatTile("Best sell$variant", Modifier.weight(1f)) {
                    BestPrice(list.bestPrice(OrderType.SELL, item), loading)
                }
                StatTile("Best buy$variant", Modifier.weight(1f)) {
                    BestPrice(list.bestPrice(OrderType.BUY, item), loading)
                }
            }
        }
        if (setItems.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(setItems, key = { it.id }) { part ->
                    val partI18n = part.i18n.localized(catalog.language)
                    val current = part.id == item.id
                    FilterChip(
                        selected = current,
                        onClick = { onSelectPart(part) },
                        label = {
                            Text(
                                partI18n?.name?.removePrefix(setItems.first { it.setRoot }.i18n.localized(catalog.language)?.name?.removeSuffix(" Set")?.plus(" ") ?: "") ?: part.slug,
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            val art = catalog.art(part)
                            AsyncImage(art.badge ?: art.thumb, null, Modifier.size(FilterChipDefaults.IconSize))
                        },
                    )
                }
            }
        }
    }
}

