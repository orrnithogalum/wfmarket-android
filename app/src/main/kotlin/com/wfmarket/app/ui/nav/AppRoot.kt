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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    PROFILE("me", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
}

private val NavBackStackEntry.isTab: Boolean get() = TopLevel.entries.any { it.route == destination.route }

private val NavigationBarHeight = 80.dp
private val NavigationRailWidth = 80.dp
private val OfflineBannerHeight = 28.dp

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
}

/** Maps a warframe.market URL, or one of the app's own links, onto a screen. Used by notifications, shortcuts and links. */
private fun targetFor(uri: Uri): LinkTarget? {
    if (uri.scheme == Shortcuts.SCHEME) {
        return when (uri.host) {
            "auctions" -> if (uri.pathSegments.firstOrNull() == "new") LinkTarget.Screen("auction_new") else LinkTarget.Auctions(null)
            "status" -> LinkTarget.Status(uri.pathSegments.firstOrNull())
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

