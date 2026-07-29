@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.api.model.Chat
import com.wfmarket.api.model.ChatMessage
import com.wfmarket.api.model.assetUrl
import com.wfmarket.app.data.SwipeAction
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ChatsScreen() {
    val container = appContainer()
    val chats by container.chats.chats.collectAsStateWithLifecycle()
    val deleting by container.chats.deleting.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            refreshing = true
            runCatching { container.chats.refreshChats() }
                .onSuccess { error = null }
                .onFailure { error = it.userMessage() }
            refreshing = false
            loaded = true
        }
    }

    LaunchedEffect(signedIn) { if (signedIn) refresh() }

    fun swiped(chat: Chat, action: SwipeAction) {
        when (action) {
            SwipeAction.MARK_READ -> scope.launch { container.chats.markChatRead(chat.id) }
            SwipeAction.DELETE -> {
                container.chats.deleteSoon(chat.id) { container.notices.show(it.userMessage()) }
                scope.launch {
                    // The deletion runs on its own timer, so dismissing an earlier notice doesn't undo it.
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("Conversation deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) container.chats.undoDelete(chat.id)
                }
            }
            SwipeAction.NONE -> Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Messages") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        if (!signedIn) {
            MessageBox(Icons.AutoMirrored.Filled.Chat, "Sign in to trade", "Your conversations with other players show up here.", Modifier.padding(padding)) {
                Button(onClick = navigator::signIn) { Text("Sign in") }
            }
            return@Scaffold
        }
        val shown = chats.filterNot { it.id in deleting }
        // Until the inbox has loaded once, the page's own spinner says so; the pull spinner is for later.
        RefreshBox(isRefreshing = refreshing && loaded, onRefresh = ::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            FadingLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                item { BatteryPrompt(settings.backgroundMessages, settings.batteryPromptDismissed) }
                error?.let { message -> item { ErrorBox(message, onRetry = ::refresh) } }
                if (shown.isEmpty() && loaded && error == null) {
                    item { EmptyBox("No conversations yet", "Tap an order and choose “Send a message” to start one.") }
                }
                items(shown, key = { it.id }) { chat ->
                    SwipeableChatRow(
                        chat, me?.id, settings.swipeRight, settings.swipeLeft,
                        onSwipe = { swiped(chat, it) },
                        modifier = Modifier.animateItem(),
                    ) { navigator.chat(chat.id) }
                }
            }
        }
    }
}

/** Battery optimization is what most often stops background delivery, so it's asked up front. */
@Composable
private fun BatteryPrompt(enabled: Boolean, dismissed: Boolean) {
    val context = LocalContext.current
    val container = appContainer()
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleResume { exempt = power.isIgnoringBatteryOptimizations(context.packageName) }
    if (!enabled || exempt || dismissed) return
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Get messages reliably", style = MaterialTheme.typography.titleSmall)
            Text(
                "Allow unrestricted battery use so Android doesn't delay messages while the app is closed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { requestBatteryExemption(context) }, shapes = ButtonDefaults.shapes()) { Text("Allow") }
                TextButton(onClick = container.settings::dismissBatteryPrompt, shapes = ButtonDefaults.shapes()) { Text("Not now") }
            }
        }
    }
}

fun requestBatteryExemption(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
        )
    }
}

@Composable
fun LifecycleResume(onResume: () -> Unit) {
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose {}
    }
}

/**
 * A conversation that can be swiped either way, each direction doing what Settings says. Marking
 * as read springs back; deleting slides the row away and leaves a moment to undo.
 */
@Composable
private fun SwipeableChatRow(
    chat: Chat,
    myId: String?,
    right: SwipeAction,
    left: SwipeAction,
    onSwipe: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    fun usable(action: SwipeAction) = action != SwipeAction.NONE
    // Not saved: a chat brought back by Undo should come back in place, not still swiped away.
    val state = remember(chat.id) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { distance -> distance * 0.35f } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect {
            if (it != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    val dragging = state.dismissDirection != SwipeToDismissBoxValue.Settled
    val corner by animateDpAsState(if (dragging) 28.dp else 0.dp, label = "swipeCorner")
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = usable(right),
        enableDismissFromEndToStart = usable(left),
        onDismiss = { direction ->
            val action = if (direction == SwipeToDismissBoxValue.StartToEnd) right else left
            onSwipe(action)
            if (action != SwipeAction.DELETE) scope.launch { state.reset() }
        },
        backgroundContent = { SwipeBackground(state, right, left) },
    ) {
        ChatRow(chat, myId, Modifier.clip(RoundedCornerShape(corner)), onClick)
    }
}

@Composable
private fun SwipeBackground(state: SwipeToDismissBoxState, right: SwipeAction, left: SwipeAction) {
    val direction = state.dismissDirection
    val action = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> right
        SwipeToDismissBoxValue.EndToStart -> left
        SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
    }
    if (action == SwipeAction.NONE) return
    val armed = state.targetValue != SwipeToDismissBoxValue.Settled
    val colors = MaterialTheme.colorScheme
    val delete = action == SwipeAction.DELETE
    val container by animateColorAsState(
        when {
            !armed -> colors.surfaceContainerHighest
            delete -> colors.errorContainer
            else -> colors.primaryContainer
        },
        label = "swipeColor",
    )
    val content = when {
        !armed -> colors.onSurfaceVariant
        delete -> colors.onErrorContainer
        else -> colors.onPrimaryContainer
    }
    val scale by animateFloatAsState(if (armed) 1.15f else 0.9f, label = "swipeIcon")
    Box(
        Modifier.fillMaxSize().background(container).padding(horizontal = 28.dp),
        contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (delete) Icons.Filled.Delete else Icons.Filled.MarkChatRead, null, Modifier.scale(scale), tint = content)
            Text(action.label, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** When a conversation last moved, as Google Messages puts it: a time today, a day this week, a date after that. */
private fun conversationTime(iso: String?): String {
    val millis = epochMillis(iso) ?: return ""
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val pattern = when {
        day == today -> "HH:mm"
        ChronoUnit.DAYS.between(day, today) < 7 -> "EEE"
        day.year == today.year -> "d MMM"
        else -> "d MMM yyyy"
    }
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone).format(Instant.ofEpochMilli(millis))
}

@Composable
private fun ChatRow(chat: Chat, myId: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val partner = chat.chatWith.firstOrNull { it.id != myId } ?: chat.chatWith.firstOrNull()
    val last = chat.messages.lastOrNull()
    val unread = chat.unreadCount > 0
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().background(colors.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        UserAvatar(assetUrl(partner?.avatar), chat.chatName, size = 52.dp, status = partner?.status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    chat.chatName.ifBlank { partner?.ingameName.orEmpty() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    conversationTime(chat.lastUpdate),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) colors.primary else colors.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    last?.let { (if (it.messageFrom == myId) "You: " else "") + it.text }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) Badge(containerColor = colors.primary, contentColor = colors.onPrimary) { Text(chat.unreadCount.toString()) }
            }
        }
    }
}

// ---------------------------------------------------------------- Conversation

/** A conversation laid out as Google Messages does: days apart, and runs of messages grouped. */
private sealed interface ChatItem {
    val key: String

    data class Day(val label: String) : ChatItem {
        override val key get() = "day:$label"
    }

    /** The time, between messages of one day sent long apart, as Google Messages marks them. */
    data class Time(val label: String, val before: String) : ChatItem {
        override val key get() = "time:$before"
    }

    data class Bubble(val message: ChatMessage, val mine: Boolean, val first: Boolean, val last: Boolean) : ChatItem {
        override val key get() = message.key
    }
}

/** The same bubble from the moment it's sent, before and after the server gives it its own id. */
private val ChatMessage.key get() = localId ?: id

