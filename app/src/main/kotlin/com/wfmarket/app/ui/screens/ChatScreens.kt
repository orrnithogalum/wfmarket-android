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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
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
            MessageBox(Icons.AutoMirrored.Filled.Chat, "Sign in to trade", body = "Your conversations with other players show up here.", modifier = Modifier.padding(padding)) {
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
                    item { EmptyBox("No conversations yet", body = "Tap an order and choose “Send a message” to start one.") }
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

/**
 * Which items came in while the newest message was in view. Those rise in at the bottom, and the
 * list stays on the newest rather than on what was there, so the rest slide up to make room.
 */
private class Arrivals {
    private var shown: List<ChatItem>? = null
    private var known: Set<String> = emptySet()
    private val rising = mutableSetOf<String>()

    /** Called during composition, so the list is measured with the new items and its new place together. */
    fun update(items: List<ChatItem>, list: LazyListState) {
        if (items === shown) return
        shown = items
        val keys = items.mapTo(HashSet()) { it.key }
        val before = known
        known = keys
        // What's there when the conversation opens is simply there.
        if (before.isEmpty()) return
        val atNewest = Snapshot.withoutReadObservation { list.firstVisibleItemIndex == 0 }
        val added = keys - before
        if (!atNewest || added.isEmpty()) return
        rising += added
        list.requestScrollToItem(0)
    }

    /** Whether the item with [key] should rise in, which it does only the first time it's shown. */
    fun take(key: String): Boolean = rising.remove(key)
}

/** How long a new message takes to rise in, the same as the moves it sets off above it. */
private fun <T> riseSpec() = Motion.arrive<T>(350)

/**
 * Grows this from nothing at the bottom of the conversation as it rises into place, so the
 * messages above slide up to make room in step with it, as one movement.
 */
@Composable
private fun Modifier.riseIn(rising: Boolean): Modifier {
    val progress = remember { Animatable(if (rising) 0f else 1f) }
    LaunchedEffect(progress) { progress.animateTo(1f, riseSpec()) }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * progress.value).roundToInt()) { placeable.place(0, 0) }
    }.graphicsLayer { alpha = progress.value }
}

/**
 * Messages from one sender read as one run until this long passes between them, as in Google
 * Messages, where a run carries on across minutes and a long pause starts a new one under the time.
 */
private const val GROUP_GAP_MILLIS = 60 * 60_000L

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE d MMMM" else "d MMMM yyyy", Locale.getDefault()).format(day)
}

private fun chatItems(messages: List<ChatMessage>, myId: String?): List<ChatItem> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val times = messages.map { epochMillis(it.sendDate) ?: System.currentTimeMillis() }
    val days = times.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    fun joined(a: Int, b: Int) = messages[a].messageFrom == messages[b].messageFrom && days[a] == days[b] && times[b] - times[a] < GROUP_GAP_MILLIS
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return buildList {
        messages.forEachIndexed { index, message ->
            when {
                index == 0 || days[index] != days[index - 1] -> add(ChatItem.Day(dayLabel(days[index], today)))
                times[index] - times[index - 1] >= GROUP_GAP_MILLIS ->
                    add(ChatItem.Time(Instant.ofEpochMilli(times[index]).atZone(zone).format(clock), message.key))
            }
            add(
                ChatItem.Bubble(
                    message,
                    mine = message.messageFrom == myId,
                    first = index == 0 || !joined(index - 1, index),
                    last = index == messages.lastIndex || !joined(index, index + 1),
                ),
            )
        }
    }
}

@Composable
fun ChatScreen(chatId: String) {
    val container = appContainer()
    val chats by container.chats.chats.collectAsStateWithLifecycle()
    val allMessages by container.chats.messages.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chat = chats.firstOrNull { it.id == chatId }
    val partner = chat?.chatWith?.firstOrNull { it.id != me?.id } ?: chat?.chatWith?.firstOrNull()
    val partnerName = chat?.chatName?.ifBlank { null } ?: partner?.ingameName.orEmpty()
    val messages = allMessages[chatId].orEmpty()
    // As what's sent is stamped, so a made-up sample conversation still reads right signed out.
    val myId = me?.id.orEmpty()
    val items = remember(messages, myId) { chatItems(messages, myId) }
    var draft by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmIgnore by remember { mutableStateOf(false) }
    // Laid out from the bottom, newest first, as Google Messages does: it stays on the newest
    // message as the keyboard opens, and a message arriving there pushes the rest up.
    val listState = rememberLazyListState()
    val arrivals = remember { Arrivals() }
    arrivals.update(items, listState)
    // How far the times are dragged into view, from none to all; letting go puts them away.
    val timesShown = remember { Animatable(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    DisposableEffect(chatId) {
        container.chats.activeChatId = chatId
        onDispose { container.chats.activeChatId = null }
    }
    LaunchedEffect(chatId) {
        runCatching { container.chats.loadMessages(chatId) }
            .onSuccess { error = null }
            .onFailure { error = it.userMessage() }
        loading = false
    }

    // Away from the newest message, what arrives doesn't move the list, so reading back through old
    // ones isn't interrupted; it's counted on the button instead. Sending takes you back down.
    var unseen by remember { mutableIntStateOf(0) }
    var handledMessages by remember { mutableIntStateOf(0) }
    // A message or two below the screen doesn't count as having scrolled away.
    val awayFromLatest by remember { derivedStateOf { listState.firstVisibleItemIndex >= 3 } }
    LaunchedEffect(awayFromLatest) { if (!awayFromLatest) unseen = 0 }
    LaunchedEffect(messages.size) {
        val added = messages.drop(handledMessages)
        val first = handledMessages == 0
        handledMessages = messages.size
        if (first || added.isEmpty() || listState.firstVisibleItemIndex == 0) return@LaunchedEffect
        if (added.any { it.messageFrom == myId }) listState.animateScrollToItem(0) else unseen += added.size
    }

    fun send(text: String = draft.trim()) {
        if (text.isEmpty()) return
        draft = ""
        // Started at once, so the message leaves the field and lands in the conversation in the same frame.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { container.chats.send(chatId, text) }.onFailure {
                draft = text
                snackbar.showSnackbar(it.userMessage())
            }
        }
    }

    fun retry(message: ChatMessage) {
        scope.launch {
            runCatching { container.chats.retry(chatId, message) }.onFailure {
                draft = message.text
                snackbar.showSnackbar(it.userMessage())
            }
        }
    }

    fun copy(text: String, what: String) {
        copyToClipboard(context, text)
        // Android 13 and later confirm copies on their own.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showSnackbar("$what copied") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { partner?.slug?.let(navigator::user) }.padding(end = 8.dp),
                    ) {
                        UserAvatar(assetUrl(partner?.avatar), partnerName, size = 40.dp, status = partner?.status)
                        Column {
                            Text(partnerName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            partner?.status?.let { StatusLabel(it) }
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(
                            text = { Text("View profile") },
                            leadingIcon = { Icon(Icons.Filled.Person, null) },
                            onClick = { menuOpen = false; partner?.slug?.let(navigator::user) },
                        )
                        if (partnerName.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Copy /w $partnerName") },
                                leadingIcon = { Icon(Icons.Filled.SportsEsports, null) },
                                onClick = { menuOpen = false; copy("/w $partnerName ", "Whisper") },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete conversation") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Ignore user") },
                            leadingIcon = { Icon(Icons.Filled.Block, null) },
                            onClick = { menuOpen = false; confirmIgnore = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Composer(draft, { draft = it }, onSend = { send() })
        },
    ) { padding ->
        RefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    runCatching { container.chats.loadMessages(chatId) }
                        .onSuccess { error = null }
                        .onFailure { failure ->
                            if (messages.isEmpty()) error = failure.userMessage() else container.refreshFailed()
                        }
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                loading && messages.isEmpty() -> PullablePage { LoadingBox() }
                error != null && messages.isEmpty() -> PullablePage { ErrorBox(error!!) }
                messages.isEmpty() -> PullablePage { EmptyBox("Say hello", body = "Messages are between you and ${partnerName.ifBlank { "this player" }}.") }
                else -> {
                    FadingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().pointerInput(rtl) {
                            val reveal = TimesWidth.toPx()
                            val toward = if (rtl) 1f else -1f
                            fun settle() {
                                scope.launch { timesShown.animateTo(0f, Motion.move(300)) }
                            }
                            detectHorizontalDragGestures(onDragEnd = ::settle, onDragCancel = ::settle) { change, dragAmount ->
                                change.consume()
                                scope.launch { timesShown.snapTo((timesShown.value + toward * dragAmount / reveal).coerceIn(0f, 1f)) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        reverseLayout = true,
                        scrollToTop = false,
                    ) {
                        items(items.asReversed(), key = { it.key }, contentType = { it::class }) { item ->
                            val rising = remember { arrivals.take(item.key) }
                            // Nothing slides to a new place on its own: what's above a message rising in
                            // moves with it as it grows, and anything else following would only lag behind.
                            val motion = Modifier
                                .animateItem(fadeInSpec = if (rising) null else Motion.fadeIn(), placementSpec = null, fadeOutSpec = Motion.fadeOut())
                                .riseIn(rising)
                            when (item) {
                                is ChatItem.Day -> DayHeader(item.label, motion)
                                is ChatItem.Time -> DayHeader(item.label, motion)
                                is ChatItem.Bubble -> MessageBubble(
                                    item,
                                    partnerAvatar = assetUrl(partner?.avatar),
                                    partnerName = partnerName,
                                    onRetry = { retry(item.message) },
                                    onDiscard = { container.chats.discard(chatId, item.message.id) },
                                    onCopy = ::copy,
                                    timesShown = { timesShown.value },
                                    modifier = motion,
                                )
                            }
                        }
                    }
                    JumpToLatest(
                        visible = awayFromLatest,
                        unseen = unseen,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { scope.launch { listState.animateScrollToItem(0) } }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this conversation?") },
            text = { Text("It's removed from your inbox.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        runCatching { container.chats.deleteChat(chatId) }
                            .onSuccess { navigator.back() }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmIgnore) {
        AlertDialog(
            onDismissRequest = { confirmIgnore = false },
            title = { Text("Ignore $partnerName?") },
            text = { Text("You won't receive messages from this player. You can undo this in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmIgnore = false
                    val userId = partner?.id ?: return@TextButton
                    scope.launch {
                        runCatching { container.chats.ignore(userId, chatId) }
                            .onSuccess { navigator.back() }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }) { Text("Ignore") }
            },
            dismissButton = { TextButton(onClick = { confirmIgnore = false }) { Text("Cancel") } },
        )
    }
}

/** The message field: a filled pill with a round send button beside it. */
@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val canSend = draft.isNotBlank()
    // Counts sends, so each one flies the icon off.
    var sends by remember { mutableIntStateOf(0) }
    fun send() {
        if (!canSend) return
        sends++
        onSend()
    }
    Row(
        Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            placeholder = { Text("Message") },
            shape = RoundedCornerShape(28.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 6,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        SendButton(canSend, sends, ::send)
    }
}

/** Its own scope, so the colours easing don't redraw the message field beside it every frame. */
@Composable
private fun SendButton(canSend: Boolean, sends: Int, onClick: () -> Unit) {
    // The button eases to rest as the field clears, rather than going grey the moment it's tapped.
    val resting = IconButtonDefaults.filledIconButtonColors()
    val container by animateColorAsState(if (canSend) resting.containerColor else resting.disabledContainerColor, Motion.fadeIn(300), label = "sendContainer")
    val content by animateColorAsState(if (canSend) resting.contentColor else resting.disabledContentColor, Motion.fadeIn(300), label = "sendContent")
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    // Pressing squares the button off a little, the expressive way of answering a touch.
    FilledIconButton(
        onClick = onClick,
        enabled = canSend,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        modifier = Modifier.size(56.dp),
    ) {
        // The plane flies off the way it points, and a fresh one fades in behind it.
        AnimatedContent(
            sends,
            transitionSpec = {
                fadeIn(tween(220, delayMillis = 120, easing = Motion.EmphasizedDecelerate)) togetherWith
                    (slideOut(Motion.move(250)) { IntOffset(forward * it.width * 3 / 4, -it.height / 4) } + fadeOut(tween(180)))
            },
            label = "sendIcon",
        ) { sent -> key(sent) { Icon(Icons.AutoMirrored.Filled.Send, "Send") } }
    }
}

@Composable
private fun DayHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun JumpToLatest(visible: Boolean, unseen: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        BadgedBox(badge = { if (unseen > 0) Badge { Text(unseen.toString()) } }) {
            SmallFloatingActionButton(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) { Icon(Icons.Filled.KeyboardArrowDown, if (unseen > 0) "$unseen new messages" else "Jump to the latest message") }
        }
    }
}

private val BubbleRound = 22.dp
private val BubbleJoined = 6.dp
private val AvatarSize = 32.dp

/** How far your messages slide over to show the times. */
private val TimesWidth = 48.dp

@Composable
private fun MessageBubble(
    item: ChatItem.Bubble,
    partnerAvatar: String?,
    partnerName: String,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onCopy: (text: String, what: String) -> Unit,
    timesShown: () -> Float,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val mine = item.mine
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var showTime by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Runs of messages keep their round corners on the outside and join up on the sender's side,
    // so a message sent under the last one tucks that one's corner in as it arrives.
    val top by animateDpAsState(if (item.first) BubbleRound else BubbleJoined, riseSpec(), label = "bubbleTop")
    val bottom by animateDpAsState(if (item.last) BubbleRound else BubbleJoined, riseSpec(), label = "bubbleBottom")
    val shape = if (mine) {
        RoundedCornerShape(topStart = BubbleRound, bottomStart = BubbleRound, topEnd = top, bottomEnd = bottom)
    } else {
        RoundedCornerShape(topEnd = BubbleRound, bottomEnd = BubbleRound, topStart = top, bottomStart = bottom)
    }
    val maxWidth = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width * 0.78f).toDp() }
    // Dragging the conversation sideways slides your messages over, and every message's time in at the edge.
    val reveal = with(LocalDensity.current) { TimesWidth.toPx() }
    val toward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    val slide = Modifier.graphicsLayer { if (mine) translationX = toward * reveal * timesShown() }
    Column(
        modifier.fillMaxWidth().padding(top = if (item.first) 10.dp else 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.align(if (mine) Alignment.CenterEnd else Alignment.CenterStart).then(slide),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!mine) {
                    if (item.last) UserAvatar(partnerAvatar, partnerName, size = AvatarSize) else Spacer(Modifier.width(AvatarSize))
                }
                Box {
                    Text(
                        message.text,
                        color = if (mine) colors.onPrimaryContainer else colors.onSurface,
                        // The size of the app's other body text, so messages don't stand out from it.
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = maxWidth)
                            .clip(shape)
                            .background(if (mine) colors.primaryContainer else colors.surfaceContainerHigh)
                            .combinedClickable(
                                onClick = { if (message.failed) onRetry() else showTime = !showTime },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menu = true
                                },
                                onLongClickLabel = "Message options",
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; onCopy(message.text, "Message") },
                        )
                        if (partnerName.isNotBlank()) {
                            // Your own words go out again in game; theirs get a reply started.
                            DropdownMenuItem(
                                text = { Text(if (mine) "Copy as /w whisper" else "Copy /w $partnerName") },
                                leadingIcon = { Icon(Icons.Filled.SportsEsports, null) },
                                onClick = {
                                    menu = false
                                    onCopy(if (mine) "/w $partnerName ${message.text}" else "/w $partnerName ", "Whisper")
                                },
                            )
                        }
                        if (message.failed) {
                            DropdownMenuItem(
                                text = { Text("Try again") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { menu = false; onRetry() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = { menu = false; onDiscard() },
                            )
                        }
                    }
                }
            }
            // Tapping the bubble gives its time too, so this one is only for the eye.
            Text(
                formatDate(message.sendDate, "HH:mm"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd).clearAndSetSemantics {}.graphicsLayer {
                    val shown = timesShown()
                    alpha = shown
                    translationX = -toward * reveal * (1 - shown)
                },
            )
        }
        val status = when {
            message.pending -> "Sending…"
            message.failed -> "Not sent · Tap to try again"
            showTime -> formatDate(message.sendDate, "EEE d MMM, HH:mm")
            item.last -> formatDate(message.sendDate, "HH:mm")
            else -> null
        }
        StatusLine(status, message.failed, mine, slide)
    }
}

/**
 * The line under a message. "Sending…" gives way to the time with a crossfade, and the time folds
 * away when another message joins the run, in step with that one rising in below.
 */
@Composable
private fun StatusLine(text: String?, failed: Boolean, mine: Boolean, modifier: Modifier = Modifier) {
    // Keeps the last words while the line folds away.
    val last = remember { mutableStateOf(text?.let { it to failed }) }.apply { if (text != null) value = text to failed }
    val open = remember { Animatable(if (text != null) 1f else 0f) }
    LaunchedEffect(text != null) { open.animateTo(if (text != null) 1f else 0f, riseSpec()) }
    val shown = last.value ?: return
    AnimatedContent(
        shown,
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * open.value).roundToInt()) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = open.value },
        transitionSpec = { (fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut())) using null },
        contentAlignment = if (mine) Alignment.TopEnd else Alignment.TopStart,
        label = "messageStatus",
    ) { (words, error) ->
        Text(
            words,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (mine) 0.dp else AvatarSize + 12.dp, end = 4.dp, top = 3.dp),
        )
    }
}
