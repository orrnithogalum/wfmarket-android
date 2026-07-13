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

    /** Sends a chat message and returns the temporary id that [SocketEvent.MessageSent] will echo. */
    fun sendChatMessage(chatId: String, text: String): String? {
        val tempId = newObjectId()
        val sent = sendLegacy("@WS/chats/SEND_MESSAGE", buildJsonObject {
            put("chat_id", chatId)
            put("message", text)
            put("temp_id", tempId)
        })
        return if (sent) tempId else null
    }

    fun markMessageRead(messageId: String) {
        sendLegacy("@WS/chats/MESSAGE_WAS_READ", buildJsonObject { put("message_id", messageId) })
    }

    /** Sets the presence status. [durationSeconds] null keeps it until changed. */
    fun setStatus(status: String, durationSeconds: Int?): Boolean =
        sendRoute("@wfm|cmd/status/set", buildJsonObject {
            put("status", status)
            put("duration", durationSeconds)
        })

    /** Places a bid, or raises the bid [existingBidId]. */
    fun setBid(auctionId: String, value: Int, existingBidId: String? = null): Boolean =
        sendLegacy("@WS/rpc/auctions/bid/SET", buildJsonObject {
            put("auction_id", auctionId)
            put("bid_id", existingBidId ?: newObjectId())
            put("value", value)
        })

    fun removeBid(auctionId: String, bidId: String): Boolean =
        sendLegacy("@WS/rpc/auctions/bid/REMOVE", buildJsonObject {
            put("auction_id", auctionId)
            put("bid_id", bidId)
        })

    fun subscribeAuction(auctionId: String) {
        sendLegacy("@WS/SUBSCRIBE/AUCTION", buildJsonObject { put("auction_id", auctionId) })
    }

    fun unsubscribeAuction(auctionId: String) {
        sendLegacy("@WS/UNSUBSCRIBE/AUCTION", buildJsonObject { put("auction_id", auctionId) })
    }

    private fun sendLegacy(type: String, payload: JsonElement): Boolean =
        chat.send(buildJsonObject { put("type", type); put("payload", payload) }.toString())

    private fun sendRoute(route: String, payload: JsonElement): Boolean =
        presence.send(buildJsonObject {
            put("route", route)
            put("payload", payload)
            put("id", UUID.randomUUID().toString())
        }.toString())

    // ---------------------------------------------------------------- Incoming frames

    private fun onLegacyFrame(text: String) {
        val frame = runCatching { WfmJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = frame["type"]?.jsonPrimitive?.contentOrNull ?: return
        frameObserver?.invoke("chat", type)
        val payload = frame["payload"]
        when (type) {
            "@WS/chats/NEW_MESSAGE" -> decodeMessage(payload)?.let { _events.tryEmit(SocketEvent.NewMessage(it)) }
            "@WS/chats/MESSAGE_SENT" -> {
                val body = payload as? JsonObject ?: return
                // The echoed message may be nested or be the payload itself, depending on the route's version.
                val message = decodeMessage(body["message"] as? JsonObject ?: body) ?: return
                val tempId = (body["temp_id"] ?: (body["message"] as? JsonObject)?.get("temp_id"))
                    ?.jsonPrimitive?.contentOrNull
                _events.tryEmit(SocketEvent.MessageSent(tempId, message))
            }
            "@WS/chats/MESSAGE_WAS_READ" -> (payload as? JsonObject)?.get("message_id")?.jsonPrimitive?.contentOrNull
                ?.let { _events.tryEmit(SocketEvent.MessageRead(it)) }
            "@WS/USER/SET_STATUS" -> (payload as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?.let { _events.tryEmit(SocketEvent.StatusChanged(it, null)) }
            "@WS/notification/NEW" -> {
                val body = payload as? JsonObject ?: return
                _events.tryEmit(SocketEvent.AuctionNotification(
                    title = body.string("title"),
                    body = body.string("body") ?: body.string("message"),
                    auctionId = body.string("url")?.substringAfter("/auction/", "")?.takeIf { it.isNotBlank() }
                        ?: (body["extra_data"] as? JsonObject)?.let { it.string("auction_id") ?: it.string("id") },
                ))
            }
            "@WS/auctions/entry/BID_SET", "@WS/auctions/yourBids/SET" -> emitBid(payload, removed = false)
            "@WS/auctions/entry/BID_REMOVED", "@WS/auctions/yourBids/REMOVED" -> emitBid(payload, removed = true)
            "@WS/rpc/auctions/bid/SET:SUCCESS", "@WS/rpc/auctions/bid/REMOVE:SUCCESS" ->
                _events.tryEmit(SocketEvent.BidResult(true, null))
            "@WS/rpc/auctions/bid/SET:ERROR", "@WS/rpc/auctions/bid/REMOVE:ERROR" ->
                _events.tryEmit(SocketEvent.BidResult(false, payload?.let(::errorText)))
            "@WS/ERROR" -> _events.tryEmit(SocketEvent.ServerError(payload?.let(::errorText)))
        }
    }

    private fun emitBid(payload: JsonElement?, removed: Boolean) {
        val body = payload as? JsonObject ?: return
        val auctionId = (body["auction"] as? JsonObject)?.string("id") ?: body.string("auction") ?: return
        val bid = (body["bid"] as? JsonObject)?.let {
            runCatching { WfmJson.decodeFromJsonElement<Bid>(it) }.getOrNull()
        }
        _events.tryEmit(SocketEvent.BidChanged(auctionId, bid, removed))
    }

    private fun decodeMessage(payload: JsonElement?): ChatMessage? =
        (payload as? JsonObject)?.let { runCatching { WfmJson.decodeFromJsonElement<ChatMessage>(it) }.getOrNull() }


