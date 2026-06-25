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
    ORDERS("Orders"), AUCTIONS("Auctions"), HISTORY("History"), REVIEWS("Reviews"), ABOUT("About")
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

