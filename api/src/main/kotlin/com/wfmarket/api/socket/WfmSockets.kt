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

