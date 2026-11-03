@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import com.wfmarket.app.ui.components.Motion
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wfmarket.app.ui.components.withFontScaleUpTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.app.Shortcuts
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.AuctionRequest
import com.wfmarket.app.ui.components.AppNavigator
import com.wfmarket.app.ui.components.LocalNavAnimatedScope
import com.wfmarket.app.ui.components.LocalNavigator
import com.wfmarket.app.ui.components.LocalOpenedFrom
import com.wfmarket.app.ui.components.OpenedFrom
import com.wfmarket.app.ui.components.LocalSharedTransitionScope
import com.wfmarket.app.ui.components.appContainer
import com.wfmarket.app.ui.screens.*
import kotlinx.coroutines.launch

private enum class TopLevel(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    MARKET("market", "Market", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    AUCTIONS("auctions", "Auctions", Icons.Filled.Gavel, Icons.Outlined.Gavel),
    MESSAGES("messages", "Messages", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
    DUCATS("ducanator", "Ducanator", Icons.Filled.Toll, Icons.Outlined.Toll),
    PROFILE("me", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
}

private val NavBackStackEntry.isTab: Boolean get() = TopLevel.entries.any { it.route == destination.route }

private val NavigationBarHeight = 64.dp
private val NavigationRailWidth = 80.dp
private val OfflineBannerMinHeight = 28.dp

// Material's motion patterns. In both, the old screen is gone before the new one fades in, so two
// screens' worth of text are never on top of each other. Switching tabs and going back fade through.
// Opening a screen also moves forward a little.
private const val OUT_MILLIS = 90
private const val IN_MILLIS = 210
private const val SLIDE_MILLIS = 300
private const val SLIDE_FRACTION = 12

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(IN_MILLIS, OUT_MILLIS, LinearOutSlowInEasing)) +
        scaleIn(tween(IN_MILLIS, OUT_MILLIS, LinearOutSlowInEasing), initialScale = 0.96f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(OUT_MILLIS, easing = FastOutLinearInEasing))

/**
 * Screens opened from a button in a bar's corner, which settle in from that corner: a small zoom,
 * over the screen underneath, which stays until they're in. Settings is opened this way whether
 * or not anyone's signed in.
 */
private val NavBackStackEntry.grows: Boolean get() = destination.route == "settings"

private val TopEnd = TransformOrigin(1f, 0f)

private fun growIn(): EnterTransition =
    scaleIn(tween(GROW_MILLIS, easing = Motion.EmphasizedDecelerate), initialScale = 0.94f, transformOrigin = TopEnd) +
        fadeIn(tween(180, easing = LinearOutSlowInEasing))

private fun shrinkOut(): ExitTransition =
    scaleOut(tween(200, easing = FastOutLinearInEasing), targetScale = 0.94f, transformOrigin = TopEnd) +
        fadeOut(tween(160, easing = FastOutLinearInEasing))

private fun heldOut(): ExitTransition = fadeOut(tween(150, delayMillis = GROW_MILLIS - 150, easing = LinearEasing))

private fun heldIn(): EnterTransition = fadeIn(tween(150, easing = LinearOutSlowInEasing))

private const val GROW_MILLIS = 300

private fun forwardIn(): EnterTransition =
    slideInHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { it / SLIDE_FRACTION } +
        fadeIn(tween(IN_MILLIS, OUT_MILLIS, LinearOutSlowInEasing))

private fun forwardOut(): ExitTransition =
    slideOutHorizontally(tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) { -it / SLIDE_FRACTION } + fadeThroughOut()

private class Navigator(private val controller: NavHostController, private val container: AppContainer) : AppNavigator {
    override fun back() { controller.popBackStack() }
    override fun item(slug: String) = controller.navigate("item/$slug")
    override fun auction(id: String) = controller.navigate("auction/$id")
    override fun replaceWithAuction(id: String) {
        val current = controller.currentDestination?.id ?: return auction(id)
        controller.navigate("auction/$id") { popUpTo(current) { inclusive = true } }
    }
    override fun editAuction(id: String?) = controller.navigate(if (id == null) "auction_new" else "auction_edit/$id")
    override fun chat(id: String) = controller.navigate("chat/$id")
    override fun user(slug: String) = controller.navigate("user/$slug")
    override fun settings() = controller.navigate("settings")
    override fun accountSettings() = controller.navigate("account")
    override fun signIn() = controller.navigate("signin")
    override fun alerts() = controller.navigate("alerts") { launchSingleTop = true }

    override fun auctionSearch(search: AuctionSearch?) {
        container.auctionRequest.value = AuctionRequest(search, openFilters = search == null)
        // From a page opened over the Auctions tab, such as alerts or an auction, going back to it
        // reads as returning there. Elsewhere the tab opens as usual. Either way, the tab then
        // slides over to Search and, with no search given, raises its filters.
        if (controller.currentDestination?.route != TopLevel.AUCTIONS.route && controller.popBackStack(TopLevel.AUCTIONS.route, inclusive = false)) return
        openTab(TopLevel.AUCTIONS.route)
    }

    fun openTab(route: String) {
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

private val KnownRoots = setOf("items", "auction", "auctions", "profile", "im", "tools", "settings")

/** What a link asks for: a screen, and sometimes something to do there. */
private sealed interface LinkTarget {
    data class Screen(val route: String) : LinkTarget
    data class Auctions(val search: AuctionSearch?) : LinkTarget
    data class Status(val status: String?) : LinkTarget
    data object InstallUpdate : LinkTarget
}

/** Maps a warframe.market URL, or one of the app's own links, onto a screen. Used by notifications, shortcuts and links. */
private fun targetFor(uri: Uri): LinkTarget? {
    if (uri.scheme == Shortcuts.SCHEME) {
        return when (uri.host) {
            "auctions" -> if (uri.pathSegments.firstOrNull() == "new") LinkTarget.Screen("auction_new") else LinkTarget.Auctions(null)
            "status" -> LinkTarget.Status(uri.pathSegments.firstOrNull())
            "settings" -> LinkTarget.Screen("settings")
            "update" -> LinkTarget.InstallUpdate
            else -> null
        }
    }
    // Localised URLs carry a language prefix, as in /fr/items/...
    val segments = uri.pathSegments.dropWhile { it !in KnownRoots }
    val route = when (segments.firstOrNull()) {
        "items" -> segments.getOrNull(1)?.let { "item/$it" }
        "auction" -> segments.getOrNull(1)?.let { "auction/$it" }
        "auctions" -> if (segments.getOrNull(1) == "search") {
            val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
            return LinkTarget.Auctions(AuctionSearch.fromQuery(params).takeIf { it.canSearch })
        } else {
            "auctions"
        }
        "profile" -> segments.getOrNull(1)?.let { "user/$it" }
        "im" -> if (segments.size >= 3) "chat/${segments[2]}" else "messages"
        "tools" -> "ducanator"
        "settings" -> "settings"
        else -> null
    }
    return route?.let(LinkTarget::Screen)
}

/**
 * A screen, with its way in and out handed to what's on it, so shared elements can move with it.
 * [argument] names the route's one argument, if it has one, which [content] is given.
 */
private fun NavGraphBuilder.screen(route: String, argument: String? = null, content: @Composable (String?) -> Unit) {
    val arguments = listOfNotNull(argument?.let { navArgument(it) { type = NavType.StringType } })
    composable(route, arguments) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            content(argument?.let { entry.arguments?.getString(it) })
        }
    }
}

@Composable
fun AppRoot(deepLink: Uri?, onDeepLinkHandled: () -> Unit) {
    val container = appContainer()
    val controller = rememberNavController()
    val navigator = remember(controller) { Navigator(controller, container) }
    val scope = rememberCoroutineScope()
    val backStack by controller.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val unread by container.chats.unread.collectAsStateWithLifecycle()
    val updateWaiting = rememberUpdateWaiting()
    val onTopLevel = TopLevel.entries.any { top -> destination?.hierarchy?.any { it.route == top.route } == true }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(deepLink, destination != null) {
        val uri = deepLink ?: return@LaunchedEffect
        if (destination == null) return@LaunchedEffect
        when (val target = targetFor(uri)) {
            is LinkTarget.Screen -> controller.navigate(target.route) { launchSingleTop = true }
            is LinkTarget.Auctions -> navigator.auctionSearch(target.search)
            is LinkTarget.Status -> {
                val status = target.status
                when {
                    !container.account.isSignedIn.value -> navigator.signIn()
                    status == null -> {
                        navigator.openTab(TopLevel.PROFILE.route)
                        container.statusSheetRequest.value = true
                    }
                    // A shortcut that sets a status needs no screen of its own, just the confirmation.
                    else -> scope.launch {
                        val label = com.wfmarket.api.model.UserStatus.of(status).label
                        container.notices.show(
                            if (container.account.setStatusWhenConnected(status)) "Status: $label" else "Couldn't set your status. Try again in a moment.",
                        )
                    }
                }
            }
            LinkTarget.InstallUpdate -> container.updater.requestInstall()
            null -> Unit
        }
        onDeepLinkHandled()
    }

    // Installing starts from Settings, which can ask for the permission to install first.
    val installRequested by container.updater.installRequest.collectAsStateWithLifecycle()
    LaunchedEffect(installRequested, destination != null) {
        if (installRequested && destination != null && destination.route != "settings") navigator.settings()
    }

    // While a page is open, the bar underneath still shows the tab that page came from.
    val currentTab = TopLevel.entries.firstOrNull { it.route == destination?.route }
    var lastTab by rememberSaveable { mutableStateOf(TopLevel.MARKET) }
    LaunchedEffect(currentTab) { currentTab?.let { lastTab = it } }
    val shownTab = currentTab ?: lastTab

    val notices = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        container.notices.flow.collect { notice ->
            if (notices.showSnackbar(notice.text, notice.action) == SnackbarResult.ActionPerformed) notice.onAction?.invoke()
        }
    }

    val online by container.connectivity.online.collectAsStateWithLifecycle()
    // Room for its label at any font size: one line with a little space around it.
    val bannerHeight = with(LocalDensity.current) { maxOf(OfflineBannerMinHeight, MaterialTheme.typography.labelMedium.lineHeight.toDp() + 12.dp) }
    val offlineBanner by animateDpAsState(if (online) 0.dp else bannerHeight, tween(250), label = "offlineBanner")

    // Tab screens leave room for the bar instead of being resized by it. If the bar took its space
    // from the layout, every screen would jump as it comes and goes during a transition.
    val tabPadding = if (wide) {
        PaddingValues(start = NavigationRailWidth)
    } else {
        PaddingValues(bottom = NavigationBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    }
    val tab: @Composable (@Composable () -> Unit) -> Unit = { screen ->
        Box(Modifier.fillMaxSize().padding(tabPadding).consumeWindowInsets(tabPadding)) { screen() }
    }

    // Lives as long as the app's screens, so a card's picture can fly back to it after the page closes.
    val openedFrom = remember { OpenedFrom() }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalOpenedFrom provides openedFrom) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxSize().padding(top = offlineBanner)) {
                // The bar stays under every page instead of animating in and out. A page covers it,
                // and a back gesture uncovers it along with the tab it belongs to.
                val barSemantics = if (onTopLevel) Modifier else Modifier.clearAndSetSemantics {}
                if (wide) {
                    NavigationRail(Modifier.align(Alignment.CenterStart).statusBarsPadding().then(barSemantics)) {
                        TopLevel.entries.forEach { top ->
                            NavigationRailItem(
                                selected = top == shownTab,
                                onClick = { navigator.openTab(top.route) },
                                icon = { TabIcon(top, top == shownTab, unread, updateWaiting) },
                                label = { TabLabel(top.label) },
                            )
                        }
                    }
                } else {
                    // Material 3 Expressive's flexible bar: shorter, with the pill behind the chosen icon.
                    ShortNavigationBar(Modifier.align(Alignment.BottomCenter).then(barSemantics)) {
                        TopLevel.entries.forEach { top ->
                            ShortNavigationBarItem(
                                selected = top == shownTab,
                                onClick = { navigator.openTab(top.route) },
                                icon = { TabIcon(top, top == shownTab, unread, updateWaiting) },
                                label = { TabLabel(top.label) },
                            )
                        }
                    }
                }

                // Shared elements, like an auction's picture, fly between screens across this.
                SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    controller,
                    startDestination = TopLevel.MARKET.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        when {
                            targetState.grows -> growIn()
                            initialState.isTab && targetState.isTab -> fadeThroughIn()
                            else -> forwardIn()
                        }
                    },
                    exitTransition = {
                        when {
                            targetState.grows -> heldOut()
                            initialState.isTab && targetState.isTab -> fadeThroughOut()
                            else -> forwardOut()
                        }
                    },
                    popEnterTransition = { if (initialState.grows) heldIn() else fadeThroughIn() },
                    popExitTransition = { if (initialState.grows) shrinkOut() else fadeThroughOut() },
                ) {
                    screen("market") { tab { MarketScreen() } }
                    screen("auctions") { tab { AuctionsScreen() } }
                    screen("messages") { tab { ChatsScreen() } }
                    screen("ducanator") { tab { DucanatorScreen() } }
                    screen("me") { tab { MyProfileScreen() } }
                    screen("item/{slug}", "slug") { ItemScreen(it.orEmpty()) }
                    screen("auction/{id}", "id") { AuctionDetailScreen(it.orEmpty()) }
                    screen("auction_new") { AuctionEditorScreen(null) }
                    screen("auction_edit/{id}", "id") { AuctionEditorScreen(it) }
                    screen("chat/{id}", "id") { ChatScreen(it.orEmpty()) }
                    screen("user/{slug}", "slug") { ProfileScreen(it.orEmpty()) }
                    screen("settings") { SettingsScreen() }
                    screen("account") { AccountSettingsScreen() }
                    screen("signin") { SignInScreen() }
                    screen("alerts") { AlertsScreen() }
                }
                }
                }

                // Back plays when it's released instead of following the finger, and looks the same
                // from anywhere: a tab fades to the first tab, and a page to the screen it came from.
                BackHandler(enabled = if (onTopLevel) currentTab != TopLevel.MARKET else controller.previousBackStackEntry != null) {
                    if (onTopLevel) navigator.openTab(TopLevel.MARKET.route) else navigator.back()
                }

                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                SnackbarHost(
                    notices,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + if (!wide && onTopLevel) NavigationBarHeight else 0.dp),
                )
            }
            if (offlineBanner > 0.dp) OfflineBanner(offlineBanner, bannerHeight)
        }
    }
}

/**
 * Sits over the top of the screen and pushes the app down by its own height. Screens keep their
 * status bar padding, which ends up behind this, so nothing has to know the banner exists.
 */
@Composable
private fun OfflineBanner(height: Dp, fullHeight: Dp) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Surface(
        Modifier.fillMaxWidth().height(statusBar + height).graphicsLayer { alpha = height / fullHeight }
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.height(fullHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.WifiOff, null, Modifier.size(16.dp))
                Text("You're offline", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/**
 * A tab's name, which grows with the system font only so far, because the bar can't grow with it.
 * Past that, a name too long for its share of the width shrinks to fit rather than run into the next.
 */
@Composable
private fun TabLabel(label: String) {
    val style = LocalTextStyle.current.withFontScaleUpTo(1.3f)
    // A little room either side, so neighbouring names never touch.
    Text(label, Modifier.padding(horizontal = 2.dp), style = style, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp))
}

@Composable
private fun TabIcon(top: TopLevel, selected: Boolean, unread: Int, updateWaiting: Boolean) {
    val icon = if (selected) top.selected else top.unselected
    if (top == TopLevel.MESSAGES && unread > 0) {
        BadgedBox(badge = { Badge { Text(unread.toString()) } }) { Icon(icon, top.label) }
    } else if (top == TopLevel.PROFILE && updateWaiting) {
        // Settings, where the update is, opens from the profile.
        BadgedBox(badge = { Badge() }) { Icon(icon, "${top.label}, update available") }
    } else {
        Icon(icon, top.label)
    }
}
