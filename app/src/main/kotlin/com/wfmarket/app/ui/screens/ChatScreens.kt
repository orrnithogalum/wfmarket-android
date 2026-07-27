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

