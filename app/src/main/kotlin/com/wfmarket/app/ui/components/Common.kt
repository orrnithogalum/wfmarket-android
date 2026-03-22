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

