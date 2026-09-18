package com.wfmarket.app.data

import android.content.Context
import android.util.Log
import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmErrorKind
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.socket.WfmSockets
import com.wfmarket.app.notifications.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A saved search that notifies about auctions it hasn't seen before. */
@Serializable
data class AuctionAlert(
    val id: String = UUID.randomUUID().toString(),
    val search: AuctionSearch,
    /** Named from the catalogue when saved, so a background check never has to load it. */
    val title: String,
    val enabled: Boolean = true,
    /** Auctions already seen, newest first. The first check fills this in without notifying. */
    val seen: List<String> = emptyList(),
    val checked: Boolean = false,
    /** When the last check ran. Only auctions created since then can be news. */
    val checkedAt: Long = 0,
)

/** An auction someone else is running, watched for new bids and for its end. */
@Serializable
data class FollowedAuction(
    val id: String,
    val title: String,
    val topBid: Int? = null,
)

@Serializable
data class AlertsState(
    val alerts: List<AuctionAlert> = emptyList(),
    val followed: List<FollowedAuction> = emptyList(),
) {
    val hasWork: Boolean get() = alerts.any { it.enabled } || followed.isNotEmpty()
}

/**
 * Saved auction searches and followed auctions. The site has no alerts of its own, so these are
 * checked by polling, with the app closed too, and followed auctions also update live over the
 * socket while it's connected.
 */
class AuctionAlerts(
    context: Context,
    private val api: WfmApi,
    private val sockets: WfmSockets,
    private val notifier: Notifier,
    private val myId: () -> String?,
    private val isAppForeground: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "auction_alerts.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()
    private val checkLock = Mutex()

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AlertsState> = _state.asStateFlow()

    /** The auction on screen, whose changes are already in front of the user. */
    @Volatile var activeAuctionId: String? = null

    private fun read(): AlertsState = runCatching { json.decodeFromString(AlertsState.serializer(), file.readText()) }
        .getOrElse { if (file.exists()) Log.w(TAG, "Couldn't read alerts", it); AlertsState() }

    private suspend fun update(change: (AlertsState) -> AlertsState) = writeLock.withLock {
        val next = change(_state.value)
        _state.value = next
        withContext(Dispatchers.IO) {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(AlertsState.serializer(), next))
            temp.renameTo(file)
        }
    }

    fun alertFor(search: AuctionSearch): AuctionAlert? = _state.value.alerts.firstOrNull { it.search == search }

