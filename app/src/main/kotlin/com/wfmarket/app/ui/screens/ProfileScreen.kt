@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
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
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(private val container: AppContainer, private val slug: String) : ViewModel() {
    private val _user = MutableStateFlow<Load<User>>(Load.Loading)
    val user = _user.asStateFlow()
    private val _orders = MutableStateFlow<Load<List<Order>>>(Load.Loading)
    val orders = _orders.asStateFlow()
    private val _auctions = MutableStateFlow<Load<List<Auction>>>(Load.Loading)
    val auctions = _auctions.asStateFlow()
    private val _history = MutableStateFlow<Load<List<ClosedOrder>>>(Load.Loading)
    val history = _history.asStateFlow()
    private val _reviews = MutableStateFlow<Load<ReviewsPage>>(Load.Loading)
    val reviews = _reviews.asStateFlow()
    private val _achievements = MutableStateFlow<Load<List<Achievement>>>(Load.Loading)
    val achievements = _achievements.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** The public lists leave out whatever is hidden, so your own profile reads the private ones. */
    private val isMe: Boolean get() = container.account.me.value?.slug == slug

    init {
        load()
        // Stays current while waiting behind another screen, such as one closing an auction.
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                when (kind) {
                    DataKind.ORDERS -> launch { fetchOrders() }
                    DataKind.HISTORY -> launch { fetchHistory() }
                    DataKind.AUCTIONS -> launch { fetchAuctions() }
                    DataKind.REVIEWS -> launch {
                        fetchReviews()
                        fetchUser()
                    }
                    DataKind.SCOPE -> load()
                }
            }
        }
    }

    private suspend fun fetchUser() = _user.accept(runCatching { container.market.user(slug) })

    private suspend fun fetchOrders() = _orders.accept(
        runCatching { if (isMe) container.market.myOrders() else container.market.userOrders(slug) },
    )

    private suspend fun fetchAuctions() = _auctions.accept(
        runCatching { if (isMe) container.auctions.mine() else container.auctions.ofUser(slug) },
    )

    private suspend fun fetchHistory() = _history.accept(runCatching { container.market.closedOrders(slug) })

    private suspend fun fetchReviews() = _reviews.accept(runCatching { container.market.reviews(slug) })

    private suspend fun fetchAchievements() = _achievements.accept(runCatching { container.market.achievements(slug) })

    private suspend fun fetchAll(): Boolean = coroutineScope {
        listOf(
            async { fetchUser() },
            async { fetchOrders() },
            async { fetchAuctions() },
            async { fetchHistory() },
            async { fetchReviews() },
            async { fetchAchievements() },
        ).awaitAll().all { it }
    }

    fun load() {
        viewModelScope.launch { fetchAll() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            if (isMe) runCatching { container.account.refresh() }
            if (!fetchAll()) container.refreshFailed()
            _refreshing.value = false
        }
    }
}

/** The signed-in user's own profile, from the bottom bar. */
@Composable
fun MyProfileScreen() {
    val container = appContainer()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(signedIn) {
        if (signedIn && me == null) runCatching { container.account.refresh() }
    }

    val slug = me?.slug?.takeIf { it.isNotBlank() }
    when {
        !signedIn -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
                    actions = { IconButton(onClick = navigator::settings) { Icon(Icons.Filled.Settings, "Settings") } },
                )
            },
            contentWindowInsets = WindowInsets.statusBars,
        ) { padding ->
            MessageBox(
                Icons.Filled.Person,
                "You're browsing without an account",
                "Sign in to post orders, message traders, bid on auctions and see your history.",
                Modifier.padding(padding),
            ) { Button(onClick = navigator::signIn) { Text("Sign in") } }
        }
        slug != null -> ProfileScreen(slug, topLevel = true)
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
                    actions = { IconButton(onClick = navigator::settings) { Icon(Icons.Filled.Settings, "Settings") } },
                )
            },
            contentWindowInsets = WindowInsets.statusBars,
        ) { padding ->
            var failed by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { runCatching { container.account.refresh() }.onFailure { failed = it.userMessage() } }
            if (me != null && slug == null) {
                // A signed-in account with no in-game name yet: it hasn't been verified.
                MessageBox(
                    Icons.Filled.Person,
                    "Verify your account",
                    "Verifying links your in-game name, which your profile needs.",
                    Modifier.padding(padding),
                ) { Button(onClick = navigator::accountSettings) { Text("Verify") } }
            } else if (failed != null) {
                ErrorBox(failed!!, Modifier.padding(padding)) {
                    failed = null
                    scope.launch { runCatching { container.account.refresh() }.onFailure { failed = it.userMessage() } }
                }
            } else {
                LoadingBox(Modifier.padding(padding))
            }
        }
    }
}

private enum class ProfileTab(val label: String) {
    ORDERS("Orders"), AUCTIONS("Auctions"), HISTORY("History"), REVIEWS("Reviews"), ACHIEVEMENTS("Achievements"), ABOUT("About")
}

@Composable
fun ProfileScreen(slug: String, topLevel: Boolean = false) {
    val container = appContainer()
    val viewModel = containerViewModel(key = "profile:$slug") { ProfileViewModel(it, slug) }
    val userLoad by viewModel.user.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val auctions by viewModel.auctions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val myStatus by container.account.status.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val openUrl = rememberOpenUrl()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { ProfileTab.entries.size }
    var statusSheet by remember { mutableStateOf(false) }
    var editAbout by remember { mutableStateOf(false) }

    val isMe = me?.slug == slug
    // The status notification opens the sheet straight away.
    val sheetRequested by container.statusSheetRequest.collectAsStateWithLifecycle()
    LaunchedEffect(sheetRequested, isMe) {
        if (sheetRequested && isMe) {
            statusSheet = true
            container.statusSheetRequest.value = false
        }
    }
    val fresh = (userLoad as? Load.Ready)?.value
    // Your own record updates immediately; the public one lags until refetched.
    val user = if (isMe) me ?: fresh else fresh

    val header = rememberCollapsingHeaderState()
    var backgroundFailed by remember(user?.background) { mutableStateOf(false) }
    val background = assetUrl(user?.background).takeUnless { backgroundFailed }
    // Over a background the bar is see-through, and fills in as the picture scrolls away under it.
    val fillDistance = with(LocalDensity.current) { BAR_FILL_DISTANCE.toPx() }
    val barFill by remember(background, fillDistance) {
        derivedStateOf { if (background == null) 1f else (-header.offset / fillDistance).coerceIn(0f, 1f) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = barFill)),
                title = {
                    val name: @Composable () -> Unit = { Text(user?.ingameName ?: "Profile", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (background == null) {
                        name()
                    } else {
                        // Over a picture, back and the name share one pill of the page's color, so
                        // both read on any part of it, white included, in either theme. The pill is
                        // drawn slimmer than the row, which keeps back's full touch target.
                        val pill = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * (1 - barFill))
                        Row(
                            Modifier.heightIn(min = 48.dp).drawBehind {
                                val inset = 6.dp.toPx()
                                val height = size.height - 2 * inset
                                drawRoundRect(pill, Offset(0f, inset), Size(size.width, height), CornerRadius(height / 2))
                            }.padding(start = if (topLevel) 14.dp else 0.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!topLevel) IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(20.dp)) }
                            Text(user?.ingameName ?: "Profile", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    // With a background, back is in the title's pill instead.
                    if (!topLevel && background == null) IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { openUrl("https://warframe.market/profile/$slug") }, colors = overPicture(barFill)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on warframe.market")
                    }
                    if (isMe) IconButton(onClick = navigator::settings, colors = overPicture(barFill)) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = if (topLevel) WindowInsets.statusBars else ScaffoldDefaults.contentWindowInsets,
    ) { padding ->
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val backgroundHeight = (maxWidth / BACKGROUND_RATIO).coerceAtMost(MAX_BACKGROUND_HEIGHT)
        if (user != null && background != null) {
            // Drawn from the top of the screen, under the bar, which the page's content is laid out below.
            ProfileBackdrop(background, padding.calculateTopPadding() + backgroundHeight, header) { backgroundFailed = true }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (user == null) {
                LoadContent(userLoad, viewModel::load) {}
                return@Column
            }
            CollapsingHeaderLayout(state = header, header = {
                ProfileHeader(
                    backgroundHeight = backgroundHeight.takeIf { background != null },
                    user = user,
                    isMe = isMe,
                    myStatus = myStatus,
                    onStatus = { statusSheet = true },
                    onMessage = {
                        if (!signedIn) {
                            navigator.signIn()
                        } else {
                            scope.launch {
                                runCatching { container.chats.openChatWith(user.id) }
                                    .onSuccess(navigator::chat)
                                    .onFailure { snackbar.showSnackbar(it.userMessage()) }
                            }
                        }
                    },
                )
            }) {
            PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                ProfileTab.entries.forEachIndexed { index, tab ->
                    Tab(selected = pager.currentPage == index, onClick = { scope.launch { pager.animateScrollToPage(index) } }, text = { Text(tab.label) })
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), key = { it }) { page ->
              RefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                when (ProfileTab.entries[page]) {
                    ProfileTab.ORDERS -> ProfileOrders(orders, catalog, isMe, viewModel, snackbar)
                    ProfileTab.AUCTIONS -> LoadContent(auctions, viewModel::load) { list ->
                        FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                            if (list.isEmpty()) item { EmptyBox("No auctions") }
                            itemsIndexed(list, key = { _, auction -> auction.id }) { index, auction ->
                                AuctionCard(
                                    auction, catalog,
                                    shape = ListItemDefaults.segmentedShapes(index, list.size).shape,
                                    modifier = listItemMotion().padding(bottom = ListItemDefaults.SegmentedGap),
                                    transitionKey = "profile",
                                ) { navigator.auction(auction.id) }
                            }
                        }
                    }
                    ProfileTab.HISTORY -> HistoryTab(history, catalog, isMe, viewModel, snackbar)
                    ProfileTab.REVIEWS -> ReviewsTab(reviews, slug, isMe, signedIn, viewModel, snackbar)
                    ProfileTab.ACHIEVEMENTS -> AchievementsTab(achievements, catalog, viewModel::load)
                    ProfileTab.ABOUT -> AboutTab(user, isMe, (fresh ?: user).canSetBackground, { editAbout = true }, snackbar)
                }
              }
            }
            }
        }
      }
    }

    if (statusSheet) StatusSheet { statusSheet = false }
    if (editAbout) {
        EditAboutDialog(me?.aboutText ?: user?.about.orEmpty(), { editAbout = false }) { text ->
            editAbout = false
            scope.launch {
                runCatching { container.account.updateAbout(text) }
                    .onSuccess { snackbar.showSnackbar("Profile updated") }
                    .onFailure { snackbar.showSnackbar(it.userMessage()) }
            }
        }
    }
}

/** [backgroundHeight] is how much of the profile's background shows below the bar, or null without one. */
@Composable
private fun ProfileHeader(backgroundHeight: Dp?, user: User, isMe: Boolean, myStatus: String, onStatus: () -> Unit, onMessage: () -> Unit) {
    val status = if (isMe) myStatus else user.status
    Box {
        // The background itself is behind the whole screen. The header leaves it room, and the
        // avatar sits over the foot of it, where it fades into the page.
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = backgroundHeight?.let { it * 0.5f } ?: 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UserAvatar(assetUrl(user.avatar), user.ingameName, size = 80.dp, status = status, shape = MaterialShapes.Cookie12Sided.toShape())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatusLabel(status)
                    if (status == "offline" && !isMe) {
                        user.lastSeen?.let {
                            Text("Last seen ${relativeTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PlatformTag(user.platform, user.crossplay)
                        SmallTag(Language.of(user.locale).label)
                        user.masteryRank?.takeIf { it > 0 }?.let { SmallTag("MR $it") }
                        user.tier?.takeIf { it != "none" }?.let { SmallTag(it.humanize()) }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("+${user.reputation}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("reputation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isMe) {
                FilledTonalButton(onClick = onStatus, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Status: ${UserStatus.of(status).label}") }
            } else {
                Button(onClick = onMessage, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Message")
                }
            }
            if (user.banned) {
                Text("Banned${user.banMessage?.let { ": $it" } ?: ""}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (user.warned) {
                Text("Warned${user.warnMessage?.let { ": $it" } ?: ""}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The site asks for backgrounds at 1920×820. */
private const val BACKGROUND_RATIO = 1920f / 820f
private val MAX_BACKGROUND_HEIGHT = 220.dp

/** A bar button's backing over a picture: a disc of the page's color, gone once the bar is filled in. */
@Composable
private fun overPicture(barFill: Float): IconButtonColors =
    IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * (1 - barFill)))

/** How far the header scrolls before the bar over a background is filled in. */
private val BAR_FILL_DISTANCE = 96.dp

/** How much slower than the header the picture moves as it scrolls away. */
private const val PARALLAX = 0.35f

/**
 * A profile's background, edge to edge from the top of the screen, behind the bar. It scrolls away
 * with the header, a little slower than the header's content, so it reads as being behind the page
 * rather than on it. A veil of the page's own color at the top keeps the bar readable over any
 * picture, and the foot fades into the page, which suits every theme, AMOLED black included.
 */
@Composable
private fun ProfileBackdrop(url: String, height: Dp, header: CollapsingHeaderState, onError: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().height(height).graphicsLayer { translationY = header.offset }.clipToBounds()) {
        AsyncImage(
            url, null,
            Modifier.fillMaxSize().graphicsLayer { translationY = -header.offset * PARALLAX },
            contentScale = ContentScale.Crop,
            onError = { onError() },
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to surface.copy(alpha = 0.7f),
                    0.4f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.78f to surface.copy(alpha = 0.75f),
                    1f to surface,
                ),
            ),
        )
    }
}

/** Status as on the website: three states, optionally held for a fixed time. */
@Composable
fun StatusSheet(onDismiss: () -> Unit) {
    val container = appContainer()
    val status by container.account.status.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val state by container.sockets.state.collectAsStateWithLifecycle()
    val until by container.account.statusUntil.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your status", style = MaterialTheme.typography.titleLarge)
            val options = listOf(UserStatus.INGAME, UserStatus.ONLINE, UserStatus.INVISIBLE)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { index, option ->
                    SegmentedListItem(
                        selected = status == option.wire,
                        onClick = { failed = !container.account.setStatus(option.wire, settings.statusDurationOption) },
                        shapes = ListItemDefaults.segmentedShapes(index, options.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        leadingContent = { Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(option.wire))) },
                        trailingContent = { RadioButton(selected = status == option.wire, onClick = null) },
                        supportingContent = {
                            Text(
                                when (option) {
                                    UserStatus.INGAME -> "Ready to trade now"
                                    UserStatus.ONLINE -> "Around, but not in the game"
                                    else -> "Shown as offline"
                                },
                            )
                        },
                    ) { Text(option.label) }
                }
            }
            Text("Keep status for", style = MaterialTheme.typography.labelLarge)
            val labels = listOf("∞", "30m", "1h", "2h", "4h")
            ChoiceButtonGroup(labels.indices.toList(), settings.statusDurationOption, { labels[it] }, { index ->
                container.settings.setStatusDurationOption(index)
                if (status != "invisible" && status != "offline") {
                    failed = !container.account.setStatus(status, index)
                }
            }, Modifier.fillMaxWidth(), fill = true)
            until?.let { Text("Expires ${formatDate(it, "HH:mm")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (status == "online" || status == "ingame") {
                Text(
                    if (settings.keepStatusInBackground) "Stays on when you close the app. Change this in Settings." else "You'll show as offline once you close the app. Change this in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed || !state.statusAuthorized) {
                Text(
                    if (state.statusConnected) "Connecting to your account…" else "Connecting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileOrders(orders: Load<List<Order>>, catalog: Catalog, isMe: Boolean, viewModel: ProfileViewModel, snackbar: SnackbarHostState) {
    val container = appContainer()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(OrderType.SELL) }
    var acting by remember { mutableStateOf<Order?>(null) }
    var editing by remember { mutableStateOf<Order?>(null) }

    fun act(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { snackbar.showSnackbar(message) }
                .onFailure { snackbar.showSnackbar(it.userMessage()) }
        }
    }

    LoadContent(orders, viewModel::load) { list ->
        val shown = list.filter { it.type == type }.sortedByDescending { it.updatedAt }
        val allVisible = shown.all { it.visible }
        FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButtonGroup(
                        listOf(OrderType.SELL, OrderType.BUY), type,
                        { value -> "${if (value == OrderType.SELL) "Selling" else "Buying"} · ${list.count { it.type == value }}" },
                        { type = it }, Modifier.fillMaxWidth(), fill = true,
                    )
                    val total = shown.sumOf { it.platinum * it.quantity }
                    if (shown.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Platinum(total, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (isMe) {
                                TextButton(onClick = {
                                    act(if (allVisible) "All hidden" else "All visible") {
                                        container.market.setAllVisible(!allVisible, type)
                                    }
                                }) { Text(if (allVisible) "Hide all" else "Show all") }
                            }
                        }
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyBox("No orders") }
            // One grouped list, like the home page's orders: round outer corners, tight inner ones.
            itemsIndexed(shown, key = { _, order -> order.id }) { index, order ->
                val item = catalog.itemsById[order.itemId]
                SegmentedListItem(
                    onClick = { if (isMe) acting = order else item?.let { navigator.item(it.slug) } },
                    shapes = ListItemDefaults.segmentedShapes(index, shown.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = listItemMotion().padding(horizontal = 12.dp).padding(bottom = ListItemDefaults.SegmentedGap),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { if (item != null) ItemThumb(catalog.art(item)) else ItemThumb(null as String?) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(relativeTime(order.updatedAt), style = MaterialTheme.typography.labelSmall)
                            OrderDetails(order)
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Platinum(order.platinum)
                            Text("x${order.quantity}", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                ) { Text(item?.let(catalog::itemName) ?: "…", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }

    acting?.let { order ->
        val item = catalog.itemsById[order.itemId]
        var quantity by remember(order.id) { mutableIntStateOf(1) }
        ModalBottomSheet(onDismissRequest = { acting = null }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(item?.let(catalog::itemName).orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (order.quantity > 1) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Traded: $quantity", Modifier.weight(1f))
                        Slider(
                            value = quantity.toFloat(),
                            onValueChange = { quantity = it.toInt() },
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
                    modifier = Modifier.clickable {
                        acting = null
                        act("Added to your trading history") { container.market.closeOrder(order.id, quantity) }
                    },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Edit") },
                    leadingContent = { Icon(Icons.Filled.Edit, null) },
                    modifier = Modifier.clickable { acting = null; editing = order },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text(if (order.visible) "Hide" else "Show") },
                    leadingContent = { Icon(if (order.visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) },
                    modifier = Modifier.clickable {
                        acting = null
                        act(if (order.visible) "Order hidden" else "Order visible") {
                            container.market.updateOrder(order.id, UpdateOrder(visible = !order.visible))
                        }
                    },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("Remove") },
                    leadingContent = { Icon(Icons.Filled.Delete, null) },
                    modifier = Modifier.clickable {
                        acting = null
                        act("Order removed") { container.market.deleteOrder(order.id) }
                    },
                )
                ListItem(
                    colors = sheetListItemColors(),
                    headlineContent = { Text("View item") },
                    leadingContent = { Icon(Icons.Filled.Image, null) },
                    modifier = Modifier.clickable {
                        acting = null
                        item?.let { navigator.item(it.slug) }
                    },
                )
            }
        }
    }

    editing?.let { order ->
        catalog.itemsById[order.itemId]?.let { item ->
            OrderEditorSheet(
                item = item,
                existing = order,
                catalog = catalog,
                bestSell = null,
                bestBuy = null,
                onDismiss = { editing = null },
                onSaved = { message ->
                    editing = null
                    scope.launch { snackbar.showSnackbar(message) }
                },
            )
        }
    }
}

@Composable
private fun HistoryTab(history: Load<List<ClosedOrder>>, catalog: Catalog, isMe: Boolean, viewModel: ProfileViewModel, snackbar: SnackbarHostState) {
    val container = appContainer()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ClosedOrder?>(null) }

    removing?.let { closed ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from your history?") },
            text = { Text("This trade won't count towards your totals any more. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { container.market.removeClosedOrder(closed.id) }
                            .onSuccess { snackbar.showSnackbar("Removed from history") }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }

    LoadContent(history, viewModel::load) { list ->
        val sold = list.filter { it.orderType == OrderType.SELL }
        val bought = list.filter { it.orderType == OrderType.BUY }
        val rows = list.filter { filter == null || it.orderType == filter }.sortedByDescending { it.closedDate }
        FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Platinum received", Modifier.weight(1f)) { Platinum(sold.sumOf { it.platinum * it.quantity }) }
                        StatTile("Platinum spent", Modifier.weight(1f)) { Platinum(bought.sumOf { it.platinum * it.quantity }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Items sold", sold.sumOf { it.quantity }.toString(), Modifier.weight(1f))
                        StatTile("Items bought", bought.sumOf { it.quantity }.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "All", OrderType.SELL to "Sold", OrderType.BUY to "Bought").forEach { (value, label) ->
                            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                        }
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyBox("No trades recorded") }
            itemsIndexed(rows, key = { _, closed -> closed.id }) { index, closed ->
                val item = catalog.itemsById[closed.item.id]
                SegmentedListItem(
                    onClick = { item?.let { navigator.item(it.slug) } },
                    shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = listItemMotion().padding(horizontal = 12.dp).padding(bottom = ListItemDefaults.SegmentedGap),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { if (item != null) ItemThumb(catalog.art(item)) else ItemThumb(assetUrl(closed.item.thumb)) },
                    supportingContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            SmallTag(
                                if (closed.orderType == OrderType.SELL) "Sold" else "Bought",
                                container = (if (closed.orderType == OrderType.SELL) semantic.positive else semantic.seriesBlue).copy(alpha = 0.18f),
                                content = MaterialTheme.colorScheme.onSurface,
                            )
                            closed.modRank?.let { SmallTag("Rank $it") }
                            closed.subtype?.let { SmallTag(it.humanize()) }
                            Text(formatDate(closed.closedDate), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Platinum(closed.platinum)
                                Text("x${closed.quantity}", style = MaterialTheme.typography.labelMedium)
                            }
                            if (isMe) IconButton(onClick = { removing = closed }) { Icon(Icons.Filled.Delete, "Remove from history") }
                        }
                    },
                ) {
                    Text(item?.let(catalog::itemName) ?: closed.item.urlName.humanize(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ReviewsTab(reviews: Load<ReviewsPage>, slug: String, isMe: Boolean, signedIn: Boolean, viewModel: ProfileViewModel, snackbar: SnackbarHostState) {
    val container = appContainer()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var compose by remember { mutableStateOf(false) }

    LoadContent(reviews, viewModel::load) { page ->
        FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isMe && signedIn) {
                item {
                    val own = page.ownReview
                    if (own != null) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Your review", style = MaterialTheme.typography.labelLarge)
                                    Text(own.text.ifBlank { "No comment" }, style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { compose = true }) { Text("Edit") }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { container.market.deleteReview(slug, own.id) }
                                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    } else {
                        FilledTonalButton(onClick = { compose = true }, modifier = Modifier.fillMaxWidth()) { Text("Write a review") }
                    }
                }
            }
            page.totalReviewCount?.let { count -> item { Text("$count reviews", style = MaterialTheme.typography.labelLarge) } }
            if (page.reviews.isEmpty()) item { EmptyBox("No reviews yet") }
            items(page.reviews, key = { it.id }) { review ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().clickable { review.userFrom.slug?.let(navigator::user) },
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UserAvatar(assetUrl(review.userFrom.avatar), review.userFrom.ingameName, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(review.userFrom.ingameName.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                SmallTag(if (review.reviewType == 1) "+1" else "Report")
                                Text(relativeTime(review.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (review.text.isNotBlank()) Text(review.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (compose) {
            var text by remember { mutableStateOf(page.ownReview?.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = { compose = false },
                title = { Text(if (page.ownReview == null) "Write a review" else "Edit your review") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A positive review adds +1 to this player's reputation.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(text, { text = it }, label = { Text("Comment (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        compose = false
                        scope.launch {
                            runCatching {
                                val own = page.ownReview
                                if (own == null) container.market.createReview(slug, text, 1) else container.market.updateReview(slug, own.id, text)
                            }.onSuccess { snackbar.showSnackbar("Review saved") }
                                .onFailure { snackbar.showSnackbar(it.userMessage()) }
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { compose = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun AchievementsTab(achievements: Load<List<Achievement>>, catalog: Catalog, onRetry: () -> Unit) {
    LoadContent(achievements, onRetry) { list ->
        val definitions = remember(catalog) { catalog.achievements.associateBy { it.id } }
        FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) item { EmptyBox("No achievements yet") }
            items(list, key = { it.id.ifBlank { it.slug } }) { achievement ->
                val definition = definitions[achievement.id] ?: achievement
                val i18n = catalog.i18n(definition.i18n.ifEmpty { achievement.i18n })
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemThumb(assetUrl(i18n?.thumb ?: i18n?.icon))
                        Column(Modifier.weight(1f)) {
                            Text(i18n?.name ?: achievement.slug.humanize(), style = MaterialTheme.typography.titleSmall)
                            i18n?.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            achievement.state?.completedAt?.let {
                                Text("Earned ${formatDate(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        definition.reputationBonus?.takeIf { it > 0 }?.let { SmallTag("+$it rep") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutTab(user: User, isMe: Boolean, canSetBackground: Boolean, onEdit: () -> Unit, snackbar: SnackbarHostState) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun upload(uri: Uri?, background: Boolean) {
        uri ?: return
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                require(bytes.size <= 10_000_000) { "Images must be under 10 MB." }
                val name = "upload.${mime.substringAfter('/')}"
                if (background) container.account.uploadBackground(bytes, name, mime) else container.account.uploadAvatar(bytes, name, mime)
            }.onSuccess { snackbar.showSnackbar("Image updated") }
                .onFailure { snackbar.showSnackbar(it.userMessage()) }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { upload(it, background = false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { upload(it, background = true) }
    val text: AnnotatedString = remember(user.about) { AnnotatedString.fromHtml(user.about.orEmpty()) }

    Column(Modifier.fillMaxSize().fadingVerticalScroll().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (text.isBlank()) {
            Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        user.createdAt?.let { Text("Member since ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (isMe) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit about") }
                OutlinedButton(onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Change avatar") }
                if (canSetBackground) {
                    OutlinedButton(onClick = { backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Change background") }
                }
            }
            if (canSetBackground) {
                Text("Backgrounds look best at 1920×820.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A perk of the Silver supporter tier and up. It's the site's own rule, and the server refuses everyone else. */
private val User.canSetBackground: Boolean
    get() = role in setOf("moderator", "admin") || tier in setOf("silver", "gold", "platinum", "diamond")

@Composable
private fun EditAboutDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About you") },
        text = {
            OutlinedTextField(
                text,
                { if (it.length <= 300) text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                supportingText = { Text("${text.length}/300 · Markdown is supported") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
