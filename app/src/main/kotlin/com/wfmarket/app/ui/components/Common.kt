@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.Platform
import com.wfmarket.api.model.UserStatus
import com.wfmarket.app.data.ItemArt
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How far a [CollapsingHeaderLayout]'s header has scrolled away, for things that move with it. */
@Stable
class CollapsingHeaderState {
    /** In pixels: 0 with the header fully shown, down to minus its height. */
    var offset by mutableFloatStateOf(0f)
        internal set
    var headerHeight by mutableIntStateOf(0)
        internal set
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/**
 * A header that scrolls away with the list below it and returns as soon as the user scrolls back.
 * Handling this before the list does keeps it working alongside pull-to-refresh.
 */
@Composable
fun CollapsingHeaderLayout(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: CollapsingHeaderState = rememberCollapsingHeaderState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val next = (state.offset + available.y).coerceIn(-state.headerHeight.toFloat(), 0f)
                val consumed = next - state.offset
                state.offset = next
                return Offset(0f, consumed)
            }
        }
    }
    Column(modifier.nestedScroll(connection)) {
        Box(
            Modifier.clipToBounds().layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                state.headerHeight = placeable.height
                val shift = state.offset.roundToInt()
                layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) { placeable.place(0, shift) }
            },
        ) { header() }
        content()
    }
}

/** The outcome of a load, as observed by a screen. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/**
 * Takes the outcome of a fetch and reports whether it worked. Fresh data replaces old, but a failed
 * refresh keeps what's on screen, because old data is better than an error over data that was fine.
 */
fun <T> MutableStateFlow<Load<T>>.accept(result: Result<T>): Boolean {
    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
    result.onSuccess { value = Load.Ready(it) }
        .onFailure { failure -> if (value !is Load.Ready) value = Load.Failed(failure.userMessage()) }
    return result.isSuccess
}

fun Throwable.userMessage(): String = (this as? WfmException)?.summary ?: message ?: "Something went wrong."

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/** Pull-to-refresh with the expressive indicator, whose shape morphs while it works. */
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter))
        },
        content = content,
    )
}

/**
 * Gives content that doesn't scroll, like a spinner or an error, a page that does. Without one,
 * pull-to-refresh has nothing to pull on.
 */
@Composable
fun PullablePage(content: @Composable BoxScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.TopCenter, content = content) }
    }
}

/**
 * Fades scrolling content out at its edges, so it doesn't get sliced by whatever sits next to it,
 * like a search bar or the navigation bar. An edge only fades while there's more to scroll that way,
 * so content at rest stays fully visible.
 */
@Composable
fun Modifier.fadingEdges(state: ScrollableState, reversed: Boolean = false): Modifier {
    // A list laid out from the bottom scrolls backward downward.
    val top by animateFloatAsState(if (if (reversed) state.canScrollForward else state.canScrollBackward) 1f else 0f, label = "topFade")
    val bottom by animateFloatAsState(if (if (reversed) state.canScrollBackward else state.canScrollForward) 1f else 0f, label = "bottomFade")
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val height = FADE_HEIGHT.toPx().coerceAtMost(size.height / 2)
            if (top > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - top), 1f to Color.Black, startY = 0f, endY = height),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom > 0f) {
                val start = size.height - height
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 1f to Color.Black.copy(alpha = 1f - bottom), startY = start, endY = size.height),
                    topLeft = Offset(0f, start),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

private val FADE_HEIGHT = 24.dp

/** A vertically scrolling list with [fadingEdges], and a way back to the top once it's long. */
@Composable
fun FadingLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    scrollToTop: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    // Min constraints pass through as they did to the list alone, so it sizes the same as before.
    Box(modifier, propagateMinConstraints = true) {
        LazyColumn(
            Modifier.fadingEdges(state, reverseLayout),
            state,
            contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (scrollToTop) Box(Modifier.matchParentSize()) { ScrollToTopButton(state) }
    }
}

private const val SCROLL_TO_TOP_AFTER = 8

/**
 * Shows once the list is well down and the user starts heading back up, which is when it's wanted,
 * and stays out of the way while they read on down. It can be turned off in Settings.
 */
@Composable
private fun BoxScope.ScrollToTopButton(state: LazyListState) {
    val settings by appContainer().settings.flow.collectAsStateWithLifecycle()
    if (!settings.scrollToTopButton) return
    var headingUp by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        var last = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { now ->
            val (index, offset) = now
            val (lastIndex, lastOffset) = last
            // Small jitters, like a list settling, say nothing about where the user is going.
            if (index != lastIndex || kotlin.math.abs(offset - lastOffset) > 24) {
                headingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                last = now
            }
        }
    }
    val scope = rememberCoroutineScope()
    val far by remember { derivedStateOf { state.firstVisibleItemIndex >= SCROLL_TO_TOP_AFTER } }
    AnimatedVisibility(
        visible = far && headingUp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.8f) + slideInVertically { -it / 2 },
        exit = fadeOut() + scaleOut(targetScale = 0.8f) + slideOutVertically { -it / 2 },
    ) {
        Surface(
            onClick = {
                headingUp = false
                scope.launch {
                    // A long way up is mostly a jump, so it doesn't scroll for seconds.
                    if (state.firstVisibleItemIndex > SCROLL_TO_TOP_AFTER * 3) state.scrollToItem(SCROLL_TO_TOP_AFTER)
                    state.animateScrollToItem(0)
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.ArrowUpward, null, Modifier.size(18.dp))
                Text("Back to top", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** `verticalScroll` with [fadingEdges]. */
@Composable
fun Modifier.fadingVerticalScroll(): Modifier {
    val state = rememberScrollState()
    return fadingEdges(state).verticalScroll(state)
}

/** A placeholder for content that's on its way, in the shape it will have. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(modifier.graphicsLayer { alpha = pulse }.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

private enum class ImageState { LOADING, READY, FAILED }

private const val PLACEHOLDER_DELAY_MILLIS = 150L

/**
 * A remote image that shows it's loading, shows a quiet icon if it can't be fetched, and tries again
 * when the network returns. Large images get a spinner, and small ones a pulsing tile.
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    spinner: Boolean = false,
) {
    var state by remember(url) { mutableStateOf(ImageState.LOADING) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    // Images already in memory arrive within a frame or two, and shouldn't flash a placeholder.
    var slow by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        delay(PLACEHOLDER_DELAY_MILLIS)
        slow = true
    }
    if (state == ImageState.FAILED) {
        val connectivity = appContainer().connectivity
        LaunchedEffect(connectivity) {
            connectivity.reconnected.collect {
                state = ImageState.LOADING
                attempt++
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        key(attempt) {
            AsyncImage(
                url,
                contentDescription,
                Modifier.matchParentSize(),
                contentScale = contentScale,
                onLoading = { state = ImageState.LOADING },
                onSuccess = { state = ImageState.READY },
                onError = { state = ImageState.FAILED },
            )
        }
        when (state) {
            ImageState.READY -> Unit
            ImageState.LOADING -> when {
                !slow -> Unit
                spinner -> LoadingIndicator(Modifier.size(40.dp))
                else -> SkeletonBox(Modifier.matchParentSize(), RoundedCornerShape(8.dp))
            }
            ImageState.FAILED -> Icon(
                Icons.Outlined.BrokenImage,
                null,
                Modifier.size(if (spinner) 32.dp else 20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/** An icon on one of Material's expressive shapes, as empty and error states use. */
@Composable
fun ShapedIcon(icon: ImageVector, shape: RoundedPolygon, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Box(
        modifier.size(size).clip(shape.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.45f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun MessageBox(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShapedIcon(icon, shape, Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val connectivity = appContainer().connectivity
    val online by connectivity.online.collectAsStateWithLifecycle()
    val retry by rememberUpdatedState(onRetry)
    // Whatever failed is worth another try as soon as the network is back.
    LaunchedEffect(connectivity) { connectivity.reconnected.collect { retry?.invoke() } }
    val button: @Composable () -> Unit = { onRetry?.let { FilledTonalButton(onClick = it) { Text("Try again") } } }
    if (online) {
        MessageBox(Icons.Outlined.CloudOff, "Couldn't load this", message, modifier, MaterialShapes.SoftBurst, button)
    } else {
        val body = if (onRetry != null) "This will load by itself when you're back online." else "Connect to the internet to load this."
        MessageBox(Icons.Outlined.WifiOff, "You're offline", body, modifier, MaterialShapes.Clover4Leaf, button)
    }
}

@Composable
fun EmptyBox(title: String, body: String? = null, modifier: Modifier = Modifier) {
    MessageBox(Icons.Outlined.Inbox, title, body, modifier, MaterialShapes.Cookie9Sided)
}

@Composable
fun <T> LoadContent(load: Load<T>, onRetry: (() -> Unit)? = null, content: @Composable (T) -> Unit) {
    val kind = when (load) {
        Load.Loading -> 0
        is Load.Failed -> 1
        is Load.Ready -> 2
    }
    // Keyed on the kind of state, so fresh data replaces old data without a fade.
    Crossfade(kind, label = "load") { shown ->
        when (shown) {
            0 -> PullablePage { LoadingBox() }
            1 -> (load as? Load.Failed)?.let { failed -> PullablePage { ErrorBox(failed.message, onRetry = onRetry) } }
            else -> (load as? Load.Ready)?.let { content(it.value) }
        }
    }
}

/** A platinum amount with the app's platinum glyph. */
@Composable
fun Platinum(
    value: Number,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val text = if (value.toDouble() % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value.toDouble())
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text, style = style, fontWeight = FontWeight.SemiBold)
        Icon(
            PlatinumIcon,
            contentDescription = "platinum",
            modifier = Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { style.fontSize.toDp() * 1.05f }),
            tint = LocalSemanticColors.current.platinum,
        )
    }
}

@Composable
fun statusColor(status: String?): Color {
    val semantic = LocalSemanticColors.current
    return when (status) {
        "ingame" -> semantic.ingame
        "online" -> semantic.online
        else -> semantic.offline
    }
}

@Composable
fun UserAvatar(url: String?, name: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, status: String? = null, shape: Shape = CircleShape) {
    Box(modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name?.take(1)?.uppercase().orEmpty(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            )
            if (url != null) {
                AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (status != null) {
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(statusColor(status))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/**
 * A status dot for a chip's icon slot. Chips size and space their icons as 18dp, so the dot is
 * centered in that much room, and sits level and balanced with the label like any other icon.
 */
@Composable
fun StatusDotIcon(status: String?) {
    Box(Modifier.size(FilterChipDefaults.IconSize), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(status)))
    }
}

val StatusFilterOptions = listOf(UserStatus.INGAME, UserStatus.ONLINE, UserStatus.OFFLINE)

/**
 * Narrows a list of traders by presence, one chip per status. None chosen means everyone.
 * Offline covers invisible players, who look offline to everyone else.
 */
@Composable
fun StatusFilterChips(selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StatusFilterOptions) { option ->
            val on = option.wire in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - option.wire else selected + option.wire) },
                label = { Text(option.label) },
                leadingIcon = { StatusDotIcon(option.wire) },
            )
        }
    }
}

/** Status is never conveyed by color alone, so the label travels with the dot. */
@Composable
fun StatusLabel(status: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status)))
        Text(
            UserStatus.of(status).label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SmallTag(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(6.dp)).background(container).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
    )
}

@Composable
fun PlatformTag(platform: String?, crossplay: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallTag(Platform.of(platform).label)
        if (crossplay) SmallTag("Cross")
    }
}

/**
 * An image on a softly lit tile. The light behind the subject is what keeps dark and thin items,
 * like most melee weapons, from disappearing into a dark surface.
 */
@Composable
fun ItemThumb(url: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, width: Dp = size, badge: String? = null, tile: Boolean = true) {
    val glow = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    // On a card the tile would be a box on a box, so there the light alone sits behind the image.
    val backdrop = if (tile) Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier
    Box(
        modifier.size(width, size)
            .then(backdrop)
            .background(Brush.radialGradient(listOf(glow, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) RemoteImage(url, Modifier.fillMaxSize().padding(3.dp))
        // Parts of a set all share one picture, so the part's own icon tells them apart.
        if (badge != null) {
            AsyncImage(
                badge,
                null,
                Modifier.align(Alignment.BottomEnd).padding(2.dp).size(size * 0.42f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A catalogue item's thumbnail, using the sharper full image when the tile is large. */
@Composable
fun ItemThumb(art: ItemArt, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    ItemThumb(if (size > 56.dp) art.full ?: art.thumb else art.thumb ?: art.full, modifier, size, badge = art.badge)
}

/** Weapons are long and thin, so they get a landscape tile and the full-size image. */
@Composable
fun WeaponThumb(url: String?, modifier: Modifier = Modifier, height: Dp = 56.dp, tile: Boolean = true) {
    ItemThumb(url, modifier, size = height, width = height * 1.6f, tile = tile)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** A key number with a small label under it, the way the profile shows reputation. */
@Composable
fun Figure(label: String, modifier: Modifier = Modifier, alignment: Alignment.Horizontal = Alignment.Start, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = alignment) {
        content()
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A tile in a row of suggestions, such as the weapons traded most last week: the picture across the
 * top on its own soft light, the name, and a figure under it.
 */
@Composable
fun SuggestionTile(
    image: String?,
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chosen: Boolean = false,
    badge: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (chosen) colors.secondaryContainer else colors.surfaceContainerHigh, tween(220), label = "chosen")
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = container, modifier = modifier.width(136.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemThumb(image, Modifier.fillMaxWidth(), size = 52.dp, width = Dp.Unspecified, badge = badge, tile = false)
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A label/value pair on a tinted tile. Its 12dp corners nest in a large card with 12dp padding. */
@Composable
fun StatTile(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            content()
        }
    }
}

