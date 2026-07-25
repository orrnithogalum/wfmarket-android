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

    private fun failPendingMessages() {
        _messages.update { all ->
            all.mapValues { (_, list) -> list.map { if (it.pending) it.copy(pending = false, failed = true) else it } }
        }
    }

    private fun onIncoming(message: ChatMessage) {
        scope.launch {
            appendMessage(message)
            val known = _chats.value.any { it.id == message.chatId }
            if (!known) runCatching { refreshChats() }
            val onScreen = activeChatId == message.chatId && isAppForeground()
            // The socket also echoes what you send from another device.
            if (message.messageFrom == account.me.value?.id) return@launch
            if (onScreen) {
                sockets.markMessageRead(message.id)
            } else {
                incrementUnread(message)
                val chat = _chats.value.firstOrNull { it.id == message.chatId }
                notifier.showMessage(chat, message, recentFor(message.chatId))
                notifier.markDelivered(message.chatId, message.sendDate)
            }
        }
    }

    private fun onSent(event: SocketEvent.MessageSent) {
        val chatId = event.message.chatId
        _messages.update { all ->
            val list = all[chatId].orEmpty()
            val swapped = list.filterNot { it.pending && it.id == event.tempId }.let { remaining ->
                if (remaining.any { it.id == event.message.id }) remaining else remaining + event.message.copy(localId = event.tempId)
            }
            all + (chatId to swapped)
        }
        _chats.update { chats ->
            chats.map {
                if (it.id == chatId) it.copy(lastUpdate = event.message.sendDate, messages = listOf(event.message)) else it
            }.sortedByDescending { it.lastUpdate }
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _messages.update { all ->
            val list = all[message.chatId].orEmpty()
            if (list.any { it.id == message.id }) all else all + (message.chatId to (list + message))
        }
        _chats.update { chats ->
            chats.map {
                if (it.id == message.chatId) it.copy(lastUpdate = message.sendDate, messages = listOf(message)) else it
            }.sortedByDescending { it.lastUpdate }
        }
    }

    private fun incrementUnread(message: ChatMessage) {
        _chats.update { chats ->
            chats.map { if (it.id == message.chatId) it.copy(unreadCount = it.unreadCount + 1) else it }
        }
        recount()
    }

    private fun recount() {
        _unread.value = _chats.value.sumOf { it.unreadCount }
    }

    private fun recentFor(chatId: String) = _messages.value[chatId].orEmpty().takeLast(6)

    suspend fun refreshChats() {
        if (session.token == null) return
        _chats.value = api.chats().sortedByDescending { it.lastUpdate }
        recount()
    }

    /**
     * Compares the server's inbox with what's already been announced and notifies about anything
     * new. Reads only the chat list, because opening a chat would mark it read.
     */
    suspend fun syncUnread() = syncLock.withLock {
        if (session.token == null) return@withLock
        announce(api.chats().sortedByDescending { it.lastUpdate })
    }

    /** Takes a fresh copy of the inbox and notifies about whatever hasn't been announced yet. */
    fun announce(latest: List<Chat>) {
        _chats.value = latest
        recount()
        val myId = account.me.value?.id
        for (chat in latest) {
            if (chat.unreadCount <= 0) {
                // Read on another device, so there's nothing left to announce.
                notifier.cancelChat(chat.id)
                continue
            }
            if (chat.id == activeChatId && isAppForeground()) continue
            if (!notifier.isNewerThanDelivered(chat.id, chat.lastUpdate)) continue
            val last = chat.messages.lastOrNull()
            if (last != null && last.messageFrom == myId) continue
            notifier.showChatSummary(chat, last)
            notifier.markDelivered(chat.id, chat.lastUpdate)
        }
    }

    /** Conversations made up for trying out the conversation screen. What's sent in them never leaves the phone. */
    private val samples = mutableSetOf<String>()

    /** Shows a conversation that isn't on the server, for trying out the conversation screen. */
    fun showSample(chat: Chat, messages: List<ChatMessage>) {
        samples += chat.id
        _chats.update { chats -> chats.filterNot { it.id == chat.id } + chat }
        _messages.update { it + (chat.id to messages) }
    }

    suspend fun loadMessages(chatId: String) {
        val loaded = api.chatMessages(chatId).sortedBy { it.sendDate }
        _messages.update { all ->
            val known = all[chatId].orEmpty()
            val pending = known.filter { it.pending }
            val localIds = known.mapNotNull { message -> message.localId?.let { message.id to it } }.toMap()
            all + (chatId to loaded.map { message -> localIds[message.id]?.let { message.copy(localId = it) } ?: message } + pending)
        }
        _chats.update { chats -> chats.map { if (it.id == chatId) it.copy(unreadCount = 0) else it } }
        recount()
        notifier.cancelChat(chatId)
    }

    suspend fun openChatWith(userId: String): String {
        val chatId = api.openChat(userId)
        if (_chats.value.none { it.id == chatId }) runCatching { refreshChats() }
        return chatId
    }

    /** Shows the message immediately as pending, then sends it. */
    suspend fun send(chatId: String, text: String) {
        val myId = account.me.value?.id.orEmpty()
        val sample = chatId in samples
        if (!sample) ensureConnected()
        val tempId = if (sample) "sample-${java.util.UUID.randomUUID()}" else sockets.sendChatMessage(chatId, text)
            ?: throw WfmException(WfmErrorKind.NETWORK, listOf("Not connected. Try again in a moment."))
        val optimistic = ChatMessage(
            id = tempId,
            chatId = chatId,
            message = text,
            rawMessage = text,
            messageFrom = myId,
            sendDate = java.time.Instant.now().toString(),
            pending = true,
        )
        _messages.update { all -> all + (chatId to all[chatId].orEmpty() + optimistic) }
        if (sample) {
            // Confirmed a moment later, as the server would.
            scope.launch {
                delay(SAMPLE_ECHO_MS)
                onSent(SocketEvent.MessageSent(tempId, optimistic.copy(id = "$tempId-sent", pending = false)))
            }
            return
        }
        // A socket that's connected but not authorized accepts the frame and never answers.
        scope.launch {
            delay(SEND_TIMEOUT_MS)
            _messages.update { all ->
                val list = all[chatId] ?: return@update all
                if (list.none { it.id == tempId && it.pending }) return@update all
                all + (chatId to list.map { if (it.id == tempId) it.copy(pending = false, failed = true) else it })
            }
        }
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 15_000L

        /** About how long the server takes to confirm a message. */
        const val SAMPLE_ECHO_MS = 400L

        /** A little longer than a short snackbar stays up. */
        const val UNDO_WINDOW_MS = 5_000L
    }

}
