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

    /** The conversation currently on screen. It never raises a notification. */
    @Volatile var activeChatId: String? = null

}
