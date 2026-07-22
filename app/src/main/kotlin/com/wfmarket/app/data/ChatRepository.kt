package com.wfmarket.app.data

import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmErrorKind
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.Chat
import com.wfmarket.api.model.ChatMessage
import com.wfmarket.api.socket.SocketEvent
import com.wfmarket.api.socket.WfmSockets
import com.wfmarket.app.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The inbox. Live messages arrive over the socket and are mirrored here. Polling [syncUnread] is the
 * safety net that catches anything sent while the socket was down, so it also drives notifications
 * from the background worker.
 */
class ChatRepository(
    private val api: WfmApi,
    private val sockets: WfmSockets,
    private val session: SessionStore,
    private val account: AccountRepository,
    private val notifier: Notifier,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Chats swiped away whose deletion can still be undone. Screens leave them out. */
    private val _deleting = MutableStateFlow<Set<String>>(emptySet())
    val deleting: StateFlow<Set<String>> = _deleting.asStateFlow()
    private val deleteJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** The conversation currently on screen. It never raises a notification. */
    @Volatile var activeChatId: String? = null

    var isAppForeground: () -> Boolean = { false }

    private val syncLock = Mutex()

    fun start() {
        scope.launch { sockets.events.collect(::onEvent) }
        scope.launch {
            account.isSignedIn.collect { signedIn ->
                if (signedIn) {
                    runCatching { refreshChats() }
                } else {
                    _chats.value = emptyList()
                    _messages.value = emptyMap()
                    _unread.value = 0
                    notifier.cancelAllChats()
                }
            }
        }
        scope.launch {
            account.me.collect { me -> if (me != null && _chats.value.isEmpty()) _unread.value = me.unreadMessages }
        }
        // Foreground/background poll. Cheap, and covers a socket that's silently not authorized.
        scope.launch {
            while (true) {
                delay(if (isAppForeground()) 45_000 else 90_000)
                if (session.token != null) runCatching { syncUnread() }
            }
        }
    }

    private fun onEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.NewMessage -> onIncoming(event.message)
            is SocketEvent.MessageSent -> onSent(event)
            is SocketEvent.AuctionNotification -> if (settings.current.notifyAuctions && !isAppForeground()) {
                notifier.showAuction(event.title, event.body, event.auctionId)
            }
            is SocketEvent.AccountNotice -> notifier.showAccountNotice(event.kind, event.message)
            // The socket reports rejections without saying which command they answer. Sends are
            // the only command in flight on this path, so whatever's pending is what failed.
            is SocketEvent.ServerError -> failPendingMessages()
            is SocketEvent.ChatStreamReady -> if (session.token != null) scope.launch { runCatching { syncUnread() } }
            else -> Unit
        }
    }

}
