package com.wfmarket.api.socket

import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmContext
import com.wfmarket.api.WfmJson
import com.wfmarket.api.model.Bid
import com.wfmarket.api.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

/** What the realtime connections report, translated from both sockets' wire formats. */
sealed interface SocketEvent {
    data class NewMessage(val message: ChatMessage) : SocketEvent
    data class MessageSent(val tempId: String?, val message: ChatMessage) : SocketEvent
    data class MessageRead(val messageId: String) : SocketEvent
    data class StatusChanged(val status: String, val until: String?) : SocketEvent
    data class OnlineCount(val connections: Int, val authorizedUsers: Int) : SocketEvent
    data class AuctionNotification(val title: String?, val body: String?, val auctionId: String?) : SocketEvent
    data class BidChanged(val auctionId: String, val bid: Bid?, val removed: Boolean) : SocketEvent
    data class BidResult(val success: Boolean, val error: String?) : SocketEvent
    data class AccountNotice(val kind: String, val message: String?) : SocketEvent
    /** The server rejected the last command, such as a message to a chat you aren't in. */
    data class ServerError(val message: String?) : SocketEvent
    data object ChatStreamReady : SocketEvent
}

data class SocketState(
    /** The legacy socket carrying chat and auctions. */
    val chatConnected: Boolean = false,
    /** The newer socket carrying presence. */
    val statusConnected: Boolean = false,
    val statusAuthorized: Boolean = false,
)

/**
 * warframe.market runs two websockets and the site uses both, so this client does too:
 *
 * - `wss://warframe.market/socket` (legacy): chat, auctions. Authenticated by the JWT cookie.
 *   Frames look like `{"type": "@WS/...", "payload": ...}`.
 * - `wss://ws.warframe.market/socket`, subprotocol `wfm`: presence and account events.
 *   Authenticated by a sign-in command. Frames look like `{"route": "@wfm|...", "payload": ...}`.
 */
class WfmSockets(
    private val context: WfmContext,
    private val http: OkHttpClient,
    private val userAgent: String = WfmApi.DEFAULT_USER_AGENT,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 128)
    private val _state = MutableStateFlow(SocketState())

    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()
    val state: StateFlow<SocketState> = _state.asStateFlow()

    /** Receives the type of every incoming frame, never its content. For diagnostics. */
    var frameObserver: ((source: String, type: String) -> Unit)? = null

    private val chat = ReconnectingSocket(
        name = "chat",
        requestFactory = {
            val token = context.token
            Request.Builder()
                .url("https://warframe.market/socket?platform=${context.platform}")
                .header("User-Agent", userAgent)
                .apply {
                    if (token != null) {
                        header("Cookie", "JWT=$token")
                        header("Authorization", "JWT $token")
                    }
                }
                .build()
        },
        onOpened = { _state.update { it.copy(chatConnected = true) }; _events.tryEmit(SocketEvent.ChatStreamReady) },
        onClosed = { _state.update { it.copy(chatConnected = false) } },
        onFrame = ::onLegacyFrame,
    )

    private val presence = ReconnectingSocket(
        name = "presence",
        requestFactory = {
            Request.Builder()
                .url("https://ws.warframe.market/socket")
                .header("User-Agent", userAgent)
                .header("Sec-WebSocket-Protocol", "wfm")
                .build()
        },
        onOpened = {
            _state.update { it.copy(statusConnected = true, statusAuthorized = false) }
            context.token?.let { token ->
                sendRoute("@wfm|cmd/auth/signIn", buildJsonObject { put("token", token) })
            }
        },
        onClosed = { _state.update { it.copy(statusConnected = false, statusAuthorized = false) } },
        onFrame = ::onPresenceFrame,
    )

    /** Idempotent; each socket keeps itself alive until [disconnect]. */
    fun connect() {
        chat.start(http, scope)
        presence.start(http, scope)
    }

    fun disconnect() {
        chat.stop()
        presence.stop()
    }

    /** Re-establish both connections, e.g. after signing in or switching platform. */
    fun reconnect() {
        disconnect()
        connect()
    }

    // ---------------------------------------------------------------- Commands

    // ---------------------------------------------------------------- Incoming frames


