@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import com.wfmarket.app.ui.theme.LocalSemanticColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuctionDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _auction = MutableStateFlow<Load<Auction>>(Load.Loading)
    val auction = _auction.asStateFlow()
    private val _bids = MutableStateFlow<List<Bid>>(emptyList())
    val bids = _bids.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        container.auctions.handedOff(id)?.let { _auction.value = Load.Ready(it) }
        load()
        container.auctions.watch(id)
        // Someone else bidding should show up without a manual refresh.
        viewModelScope.launch {
            container.auctions.bidChanges.collect { change -> if (change.auctionId == id) load() }
        }
        viewModelScope.launch {
            container.changes.flow.collect { kind -> if (kind == DataKind.AUCTIONS || kind == DataKind.SCOPE) load() }
        }
    }

    private suspend fun fetch(): Boolean {
        val updated = _auction.accept(runCatching { container.auctions.auction(id) })
        runCatching { container.auctions.bids(id) }.onSuccess { _bids.value = it.sortedByDescending { bid -> bid.value } }
        return updated
    }

    fun load() {
        viewModelScope.launch { fetch() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            if (!fetch()) container.refreshFailed()
            _refreshing.value = false
        }
    }

    override fun onCleared() {
        container.auctions.unwatch(id)
    }
}

@Composable
fun AuctionDetailScreen(id: String) {
    val container = appContainer()
    val viewModel = containerViewModel(key = "auction:$id") { AuctionDetailViewModel(it, id) }
    val auctionLoad by viewModel.auction.collectAsStateWithLifecycle()
    val bids by viewModel.bids.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val openUrl = rememberOpenUrl()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmClose by remember { mutableStateOf<Auction?>(null) }
    val alerts by container.alerts.state.collectAsStateWithLifecycle()
    val following = alerts.followed.any { it.id == id }

    DisposableEffect(id) {
        container.alerts.activeAuctionId = id
        onDispose { if (container.alerts.activeAuctionId == id) container.alerts.activeAuctionId = null }
    }

    fun run(success: String? = null, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { success?.let { snackbar.showSnackbar(it) } }
                .onFailure { snackbar.showSnackbar(it.userMessage()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auction") },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    val auction = (auctionLoad as? Load.Ready)?.value
                    // Your own auction's bids already reach you; following is for someone else's.
                    if (auction != null && !auction.closed && auction.owner.id != me?.id) {
                        IconToggleButton(
                            checked = following,
                            onCheckedChange = { follow ->
                                scope.launch {
                                    if (follow) {
                                        val title = catalog.weaponName(auction.item.type, auction.item.weaponUrlName) +
                                            (auction.item.name?.let { " ${it.humanize()}" } ?: "")
                                        container.alerts.follow(auction, title)
                                        snackbar.showSnackbar("Following. You'll be notified about new bids and when it ends.")
                                    } else {
                                        container.alerts.unfollow(auction.id)
                                        snackbar.showSnackbar("No longer following")
                                    }
                                }
                            },
                            shapes = IconButtonDefaults.toggleableShapes(),
                        ) {
                            Icon(
                                if (following) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                                if (following) "Stop following" else "Follow this auction",
                            )
                        }
                    }
                    IconButton(onClick = { openUrl("https://warframe.market/auction/$id") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on warframe.market")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            LoadContent(auctionLoad, viewModel::load) { auction ->
                val isOwner = me?.id == auction.owner.id
                val myBid = bids.firstOrNull { it.user.id == me?.id }
                FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Column {
                            AuctionHeader(auction, catalog)
                            if (auction.item.type == AuctionType.RIVEN) RivenTradeCard(auction.item.weaponUrlName, catalog, Modifier.fillMaxWidth(), gap = 12.dp)
                        }
                    }
                    item { OwnerCard(auction, signedIn, isOwner, catalog, context, snackbar) }
                    if (auction.noteText.isNotBlank()) {
                        item {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(auction.noteText, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (isOwner) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { navigator.editAuction(auction.id) }, enabled = !auction.closed) {
                                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Edit")
                                }
                                OutlinedButton(onClick = {
                                    run(if (auction.visible) "Hidden" else "Visible") { container.auctions.setVisible(auction.id, !auction.visible) }
                                }) { Text(if (auction.visible) "Hide" else "Show") }
                                if (!auction.closed) {
                                    OutlinedButton(onClick = { confirmClose = auction }) { Text(if (auction.winner?.id?.isNotBlank() == true) "Complete" else "Close") }
                                }
                                if (auction.winner?.id?.isNotBlank() == true) {
                                    OutlinedButton(onClick = { run { container.auctions.clearWinner(auction.id) } }) { Text("Clear winner") }
                                }
                            }
                        }
                    } else if (!auction.closed && !auction.isDirectSell) {
                        item {
                            BidForm(auction, myBid, signedIn,
                                onSignIn = navigator::signIn,
                                onBid = { value -> run("Bid placed") { container.auctions.placeBid(auction.id, value, myBid?.id) } },
                                onRemove = { myBid?.let { bid -> run("Bid removed") { container.auctions.removeBid(auction.id, bid.id) } } },
                            )
                        }
                    }
                    if (bids.isNotEmpty()) {
                        item { Text("Bids · ${bids.size}", style = MaterialTheme.typography.titleMedium) }
                        item(key = "bids") {
                          Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                           bids.forEachIndexed { index, bid ->
                            SegmentedListItem(
                                onClick = { bid.user.slug?.let(navigator::user) },
                                shapes = ListItemDefaults.segmentedShapes(index, bids.size),
                                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                verticalAlignment = Alignment.CenterVertically,
                                leadingContent = { UserAvatar(assetUrl(bid.user.avatar), bid.user.ingameName, status = bid.user.status) },
                                supportingContent = { Text("+${bid.user.reputation} · ${relativeTime(bid.updated)}") },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isOwner && !auction.closed) {
                                            TextButton(onClick = { run("Winner chosen") { container.auctions.markWinner(auction.id, bid.user.id) } }) { Text("Choose") }
                                        }
                                        Platinum(bid.value)
                                    }
                                },
                            ) { Text(bid.user.ingameName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                           }
                          }
                        }
                    }
                }
            }
        }
    }

    confirmClose?.let { auction ->
        val complete = auction.winner?.id?.isNotBlank() == true
        AlertDialog(
            onDismissRequest = { confirmClose = null },
            title = { Text(if (complete) "Complete this auction?" else "Close this auction?") },
            text = { Text("It's removed from the site and your profile. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = null
                    scope.launch {
                        // There's nothing left to show here, so go back to wherever this was opened from.
                        runCatching { container.auctions.close(auction.id) }
                            .onSuccess {
                                container.notices.show(if (complete) "Auction completed" else "Auction closed")
                                navigator.back()
                            }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }) { Text(if (complete) "Complete" else "Close auction") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun AuctionHeader(auction: Auction, catalog: Catalog) {
    val item = auction.item
    val colors = MaterialTheme.colorScheme
    // Opened from a card, the card's picture grows into this one. From a link, it's simply here.
    val shared = LocalOpenedFrom.current.key?.takeIf { it.endsWith(":${auction.id}") }
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ItemThumb(
                catalog.weaponImage(item.type, item.weaponUrlName),
                Modifier.sharedAcrossScreens(shared?.let { "image:$it" }).fillMaxWidth(),
                size = 150.dp,
                width = Dp.Unspecified,
            )
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(catalog.weaponName(item.type, item.weaponUrlName), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOfNotNull(item.name?.replaceFirstChar { it.uppercase() }, item.type.humanize()).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                // The prices as big figures with small labels, as the profile shows reputation.
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    val figure = MaterialTheme.typography.headlineSmall
                    if (auction.isDirectSell) {
                        Figure("buyout") { Platinum(auction.buyoutPrice ?: auction.startingPrice, style = figure) }
                    } else {
                        Figure(if (auction.topBid != null) "top bid" else "starting bid") { Platinum(auction.topBid ?: auction.startingPrice, style = figure) }
                        auction.buyoutPrice?.let { Figure("buyout") { Platinum(it, style = figure) } }
                        if (auction.topBid != null) Figure("started at") { Platinum(auction.startingPrice, style = figure) }
                    }
                }
                if (item.type == AuctionType.RIVEN && item.attributes.isNotEmpty()) {
                    val rolls = rememberRivenRolls(item, catalog)
                    RivenStats(item.attributes, catalog, Modifier.fillMaxWidth(), rolls = rolls, detailed = true)
                    rolls?.let { rivenRollsNote(it, item.modRank) }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                AuctionDetailsLine(item, catalog)
                Text(
                    listOfNotNull(
                        auction.minimalReputation.takeIf { it > 0 }?.let { "Bidders need +$it reputation" },
                        if (auction.visible) null else "Hidden",
                        if (auction.closed) "Closed" else null,
                        auction.created?.let { "Listed ${relativeTime(it)}" },
                        auction.updated?.takeIf { it != auction.created }?.let { "updated ${relativeTime(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** The in-game whisper that asks the seller for this auction. */
fun auctionWhisper(auction: Auction, catalog: Catalog): String {
    val who = auction.owner.ingameName.orEmpty().let { if (it.contains(' ')) "\"$it\"" else it }
    val what = catalog.weaponName(auction.item.type, auction.item.weaponUrlName) + (auction.item.name?.let { " ${it.humanize()}" } ?: "")
    return "/w $who Hi! I want to buy: \"$what\" (warframe.market/auction/${auction.id})"
}

@Composable
private fun OwnerCard(auction: Auction, signedIn: Boolean, isOwner: Boolean, catalog: Catalog, context: android.content.Context, snackbar: SnackbarHostState) {
    val container = appContainer()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val owner = auction.owner
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { owner.slug?.let(navigator::user) }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UserAvatar(assetUrl(owner.avatar), owner.ingameName, size = 48.dp, status = owner.status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(owner.ingameName.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusLabel(owner.status)
                    PlatformTag(auction.platform, auction.crossplay)
                }
            }
            Figure("reputation", alignment = Alignment.End) {
                Text("+${owner.reputation}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!isOwner) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, auctionWhisper(auction, catalog))
                        scope.launch { snackbar.showSnackbar("Whisper copied") }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Whisper")
                }
                Button(
                    onClick = {
                        if (!signedIn) {
                            navigator.signIn()
                        } else {
                            scope.launch {
                                runCatching { container.chats.openChatWith(owner.id) }
                                    .onSuccess(navigator::chat)
                                    .onFailure { snackbar.showSnackbar(it.userMessage()) }
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Message") }
            }
        }
    }
    }
}

