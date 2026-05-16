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

@Composable
private fun BestPrice(price: Int?, loading: Boolean) {
    when {
        loading -> SkeletonBox(Modifier.size(56.dp, 24.dp), RoundedCornerShape(6.dp))
        price != null -> Platinum(price)
        else -> Text("–", style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------- Orders

/** Whether a trader's presence passes the chips. None chosen means everyone. */
private fun Set<String>.admits(status: String?) = isEmpty() || sellerStatus(status) in this

/** The whisper the site copies to the clipboard for pasting into in-game chat. */
fun whisper(order: Order, catalog: Catalog, item: Item): String {
    val name = item.i18n["en"]?.name ?: catalog.itemName(item)
    val details = buildList {
        order.rank?.let { add("(rank $it)") }
        order.charges?.let { add("(charges $it)") }
        order.subtype?.let { add("(${it.humanize().lowercase()})") }
        if (order.cyanStars != null || order.amberStars != null) {
            add("(${order.cyanStars ?: 0} cyan, ${order.amberStars ?: 0} amber)")
        }
    }.joinToString(" ")
    val action = if (order.type == OrderType.SELL) "buy" else "sell"
    val per = if (order.perTrade > 1) "x${order.perTrade} " else ""
    val who = order.user?.ingameName.orEmpty().let { if (it.contains(' ')) "\"$it\"" else it }
    return "/w $who Hi! I want to $action: $per\"$name${if (details.isEmpty()) "" else " $details"}\" for ${order.platinum} platinum. (warframe.market)"
}

@Composable
private fun OrdersTab(
    item: Item,
    orders: Load<List<Order>>,
    meId: String?,
    catalog: Catalog,
    onRetry: () -> Unit,
    onEdit: (Order) -> Unit,
    onDelete: (Order) -> Unit,
    onClose: (Order, Int) -> Unit,
    onMessage: (String) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(OrderType.SELL) }
    // Players in game can trade now, so they're who the list starts with.
    var statuses by rememberSaveable { mutableStateOf(setOf("ingame")) }
    var rank by rememberSaveable { mutableStateOf<Int?>(null) }
    var subtype by rememberSaveable { mutableStateOf<String?>(null) }
    var charges by rememberSaveable { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf<Order?>(null) }

    val all = (orders as? Load.Ready)?.value.orEmpty()
    val filtered = remember(all, type, statuses, rank, subtype, charges) {
        all.asSequence()
            .filter { it.type == type }
            .filter { statuses.admits(it.user?.status) }
            .filter { rank == null || it.rank == rank }
            .filter { subtype == null || it.subtype == subtype }
            .filter { charges == null || it.charges == charges }
            .sortedWith(
                if (type == OrderType.SELL) compareBy<Order> { it.platinum }.thenByDescending { it.user?.reputation ?: 0 }
                else compareByDescending<Order> { it.platinum }.thenByDescending { it.user?.reputation ?: 0 },
            )
            .toList()
    }

    // A different part of a set is a different list, so it starts from the top.
    val listState = rememberLazyListState()
    LaunchedEffect(item.id) { listState.scrollToItem(0) }

    Box {
        FadingLazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButtonGroup(
                        listOf(OrderType.SELL, OrderType.BUY), type,
                        { value ->
                            val count = all.count { it.type == value && statuses.admits(it.user?.status) }
                            "${if (value == OrderType.SELL) "Sellers" else "Buyers"} · $count"
                        },
                        { type = it }, Modifier.fillMaxWidth(), fill = true,
                    )
                    StatusFilterChips(statuses, { statuses = it })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.maxRank?.let { max ->
                            DropdownChip(
                                "Rank", rank, listOf(null, 0, max), { it?.toString() ?: "Any" }, { rank = it }, isDefault = rank == null,
                            )
                        }
                        if (item.subtypes.isNotEmpty()) {
                            DropdownChip(
                                "Type", subtype, listOf<String?>(null) + item.subtypes, { it?.humanize() ?: "Any" }, { subtype = it }, isDefault = subtype == null,
                            )
                        }
                        item.maxCharges?.let { max ->
                            DropdownChip(
                                "Charges", charges, listOf<Int?>(null) + (0..max).toList(), { it?.toString() ?: "Any" }, { charges = it }, isDefault = charges == null,
                            )
                        }
                    }
                }
            }
            when (orders) {
                Load.Loading -> item { LoadingBox(Modifier.height(240.dp)) }
                is Load.Failed -> item { ErrorBox(orders.message, onRetry = onRetry) }
                is Load.Ready -> {
                    if (filtered.isEmpty()) {
                        item {
                            EmptyBox(
                                "No matching orders",
                                if (statuses.isNotEmpty() && statuses.size < StatusFilterOptions.size) "Try including more statuses." else null,
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { order ->
                        OrderRow(order, isMine = order.user?.id == meId) { selected = order }
                    }
                }
            }
        }
    }

    selected?.let { order ->
        OrderActionsSheet(
            order = order,
            item = item,
            catalog = catalog,
            isMine = order.user?.id == meId,
            onDismiss = { selected = null },
            onEdit = { selected = null; onEdit(order) },
            onDelete = { selected = null; onDelete(order) },
            onClose = { quantity -> selected = null; onClose(order, quantity) },
            onMessage = onMessage,
        )
    }
}

@Composable
private fun OrderRow(order: Order, isMine: Boolean, onClick: () -> Unit) {
    val user = order.user
    val navigator = LocalNavigator.current
    ClickableRow(onClick) {
        // The avatar goes straight to the profile; the row opens the order's actions.
        UserAvatar(
            assetUrl(user?.avatar), user?.ingameName, status = user?.status,
            modifier = Modifier.clip(CircleShape).clickable(enabled = user?.slug != null, onClickLabel = "Open profile") { user?.slug?.let(navigator::user) },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    user?.ingameName.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isMine) SmallTag("You", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusLabel(user?.status)
                Text("+${user?.reputation ?: 0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                PlatformTag(user?.platform, user?.crossplay == true)
            }
            OrderDetails(order)
        }
        Column(horizontalAlignment = Alignment.End) {
            Platinum(order.platinum)
            Text("x${order.quantity}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The per-order attributes: rank, subtype, stars and so on. Draws nothing when there are none. */
@Composable
fun OrderDetails(order: Order) {
    val tags = buildList {
        order.rank?.let { add("Rank $it") }
        order.charges?.let { add("$it charges") }
        order.subtype?.let { add(it.humanize()) }
        order.amberStars?.let { add("$it amber") }
        order.cyanStars?.let { add("$it cyan") }
        if (order.perTrade > 1) add("x${order.perTrade} per trade")
        if (!order.visible) add("Hidden")
    }
    if (tags.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { tags.take(4).forEach { SmallTag(it) } }
    }
}

@Composable
private fun OrderActionsSheet(
    order: Order,
    item: Item,
    catalog: Catalog,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: (Int) -> Unit,
    onMessage: (String) -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    var closeQuantity by remember { mutableIntStateOf(1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(assetUrl(order.user?.avatar), order.user?.ingameName, size = 56.dp, status = order.user?.status)
                Column(Modifier.weight(1f)) {
                    Text(order.user?.ingameName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Reputation +${order.user?.reputation ?: 0} · ${relativeTime(order.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OrderDetails(order)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Platinum(order.platinum, style = MaterialTheme.typography.titleLarge)
                    Text("x${order.quantity}", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider()
            if (isMine) {
                if (order.quantity > 1) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantity traded: $closeQuantity", Modifier.weight(1f))
                        Slider(
                            value = closeQuantity.toFloat(),
                            onValueChange = { closeQuantity = it.toInt() },
                            valueRange = 1f..order.quantity.toFloat(),
                            steps = (order.quantity - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text(if (order.type == OrderType.SELL) "Mark as sold" else "Mark as bought") },
                    supportingContent = { Text("Adds it to your trading history") },
                    leadingContent = { Icon(Icons.Filled.Sell, null) },
                    modifier = Modifier.clickable { onClose(closeQuantity) },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Edit order") },
                    leadingContent = { Icon(Icons.Filled.Edit, null) },
                    modifier = Modifier.clickable(onClick = onEdit),
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Remove order") },
                    leadingContent = { Icon(Icons.Filled.Delete, null) },
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            } else {
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Copy whisper") },
                    supportingContent = { Text("Paste it into in-game chat", maxLines = 1) },
                    leadingContent = { Icon(Icons.Filled.ContentCopy, null) },
                    modifier = Modifier.clickable {
                        copyToClipboard(context, whisper(order, catalog, item))
                        onMessage("Whisper copied")
                        onDismiss()
                    },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Send a message") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    modifier = Modifier.clickable {
                        val userId = order.user?.id ?: return@clickable
                        if (!signedIn) {
                            onDismiss()
                            navigator.signIn()
                            return@clickable
                        }
                        scope.launch {
                            runCatching { container.chats.openChatWith(userId) }
                                .onSuccess {
                                    onDismiss()
                                    navigator.chat(it)
                                }
                                .onFailure { failure -> onMessage(failure.userMessage()) }
                        }
                    },
                )
            }
            ListItem(
                colors = sheetListItemColors(),
                headlineContent = { Text("View profile") },
                leadingContent = { Icon(Icons.Filled.Person, null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    order.user?.slug?.let(navigator::user)
                },
            )
        }
    }
}

/** Creates or edits an order. Only the attributes this item actually has are offered. */
@Composable
fun OrderEditorSheet(
    item: Item,
    existing: Order?,
    catalog: Catalog,
    bestSell: Int?,
    bestBuy: Int?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: OrderType.SELL) }
    var platinum by remember { mutableStateOf(existing?.platinum?.toString() ?: bestSell?.toString().orEmpty()) }
    var quantity by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var perTrade by remember { mutableStateOf(existing?.perTrade?.toString() ?: "1") }
    var rank by remember { mutableStateOf(existing?.rank ?: 0) }
    var charges by remember { mutableStateOf(existing?.charges ?: item.maxCharges ?: 0) }
    var amber by remember { mutableStateOf(existing?.amberStars ?: 0) }
    var cyan by remember { mutableStateOf(existing?.cyanStars ?: 0) }
    var subtype by remember { mutableStateOf(existing?.subtype ?: item.subtypes.firstOrNull()) }
    var visible by remember { mutableStateOf(existing?.visible ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().fadingVerticalScroll().imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${if (existing == null) "New order" else "Edit order"}: ${catalog.itemName(item)}",
                style = MaterialTheme.typography.titleLarge,
            )
            if (existing == null) {
                ChoiceButtonGroup(
                    listOf(OrderType.SELL, OrderType.BUY), type, { if (it == OrderType.SELL) "I'm selling" else "I'm buying" },
                    { value ->
                        type = value
                        if (value == OrderType.SELL) bestSell?.let { platinum = it.toString() } else bestBuy?.let { platinum = it.toString() }
                    },
                    Modifier.fillMaxWidth(), fill = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Platinum", platinum, { platinum = it }, Modifier.weight(1f))
                NumberField("Quantity", quantity, { quantity = it }, Modifier.weight(1f))
                if (item.bulkTradable) NumberField("Per trade", perTrade, { perTrade = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bestSell?.let { value -> AssistChip(onClick = { platinum = value.toString() }, label = { Text("Best sell $value") }) }
                bestBuy?.let { value -> AssistChip(onClick = { platinum = value.toString() }, label = { Text("Best buy $value") }) }
            }
            item.tradingTax?.let {
                Text("Trading tax: $it credits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.maxRank?.let { max -> StepSlider("Rank", rank, max) { rank = it } }
            item.maxCharges?.let { max -> StepSlider("Charges", charges, max) { charges = it } }
            item.maxAmberStars?.let { max -> StepSlider("Amber stars", amber, max) { amber = it } }
            item.maxCyanStars?.let { max -> StepSlider("Cyan stars", cyan, max) { cyan = it } }
            if (item.subtypes.isNotEmpty()) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.subtypes.forEach { value ->
                        FilterChip(selected = subtype == value, onClick = { subtype = value }, label = { Text(value.humanize()) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Visible to others", style = MaterialTheme.typography.bodyLarge)
                    Text("Hidden orders stay on your profile only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = visible, onCheckedChange = { visible = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val plat = platinum.toIntOrNull()
                    val qty = quantity.toIntOrNull()
                    if (plat == null || plat <= 0) { error = "Enter a price of at least 1 platinum."; return@Button }
                    if (qty == null || qty <= 0) { error = "Enter a quantity of at least 1."; return@Button }
                    val per = if (item.bulkTradable) perTrade.toIntOrNull()?.coerceAtLeast(1) ?: 1 else null
                    busy = true
                    error = null
                    scope.launch {
                        runCatching {
                            if (existing == null) {
                                container.market.createOrder(
                                    CreateOrder(
                                        itemId = item.id,
                                        type = type,
                                        platinum = plat,
                                        quantity = qty,
                                        perTrade = per,
                                        visible = visible,
                                        rank = item.maxRank?.let { rank },
                                        charges = item.maxCharges?.let { charges },
                                        subtype = subtype,
                                        amberStars = item.maxAmberStars?.let { amber },
                                        cyanStars = item.maxCyanStars?.let { cyan },
                                    ),
                                )
                            } else {
                                container.market.updateOrder(
                                    existing.id,
                                    UpdateOrder(
                                        platinum = plat,
                                        quantity = qty,
                                        perTrade = per,
                                        visible = visible,
                                        rank = item.maxRank?.let { rank },
                                        charges = item.maxCharges?.let { charges },
                                        subtype = subtype,
                                        amberStars = item.maxAmberStars?.let { amber },
                                        cyanStars = item.maxCyanStars?.let { cyan },
                                    ),
                                )
                            }
                        }.onSuccess {
                            onSaved(if (existing == null) "Order placed" else "Order updated")
                        }.onFailure {
                            busy = false
                            error = it.userMessage()
                        }
                    }
                },
            ) { Text(if (existing == null) "Place order" else "Save") }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all(Char::isDigit) && new.length <= 7) onChange(new) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

