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

    suspend fun addAlert(search: AuctionSearch, title: String) {
        if (alertFor(search) != null) return
        val alert = AuctionAlert(search = search, title = title)
        update { it.copy(alerts = listOf(alert) + it.alerts) }
        // What's listed now is the baseline. Taking it at once means only later listings notify.
        scope.launch { checkLock.withLock { checkAlert(alert) } }
    }

    suspend fun removeAlert(id: String) = update { state -> state.copy(alerts = state.alerts.filterNot { it.id == id }) }

    /** Puts back an alert that was just removed, with what it had already seen. */
    suspend fun restoreAlert(alert: AuctionAlert, index: Int) = update { state ->
        if (state.alerts.any { it.id == alert.id }) state
        else state.copy(alerts = state.alerts.toMutableList().apply { add(index.coerceIn(0, size), alert) })
    }

    suspend fun setAlertEnabled(id: String, enabled: Boolean) = update { state ->
        // Paused alerts start over, so they don't announce everything listed while they were off.
        state.copy(alerts = state.alerts.map { if (it.id == id) it.copy(enabled = enabled, checked = it.checked && enabled) else it })
    }

    fun isFollowed(id: String): Boolean = _state.value.followed.any { it.id == id }

    suspend fun follow(auction: Auction, title: String) {
        if (isFollowed(auction.id)) return
        update { it.copy(followed = listOf(FollowedAuction(auction.id, title, auction.topBid)) + it.followed) }
        sockets.subscribeAuction(auction.id)
    }

    suspend fun unfollow(id: String) {
        update { state -> state.copy(followed = state.followed.filterNot { it.id == id }) }
        if (activeAuctionId != id) sockets.unsubscribeAuction(id)
    }

    /** Followed auctions only send bids to a connection that asked for them. */
    fun resubscribe() {
        _state.value.followed.forEach { sockets.subscribeAuction(it.id) }
    }

    /** Runs every saved search and looks at every followed auction, notifying about what's new. */
    suspend fun check() = checkLock.withLock {
        for (alert in _state.value.alerts.filter { it.enabled }) {
            checkAlert(alert)
            delay(REQUEST_GAP_MS)
        }
        for (followed in _state.value.followed) {
            refreshFollowed(followed.id)
            delay(REQUEST_GAP_MS)
        }
    }

    private suspend fun checkAlert(alert: AuctionAlert) {
        val results = try {
            api.searchAuctions(alert.search)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't run an alert's search", e)
            return
        }
        val me = myId()
        val matching = results.filter { !it.closed && it.visible && it.owner.id != me && alert.search.matches(it) }
        // An alert added while this ran may have replaced the list, so this one is looked up again.
        val current = _state.value.alerts.firstOrNull { it.id == alert.id } ?: return
        val seen = current.seen.toSet()
        // Unseen isn't enough: switching platform or crossplay brings in a whole other set of old
        // listings, and the 100-result window shifts as cheaper ones sell.
        val since = current.checkedAt - CLOCK_SLACK_MS
        // With a price range, a listing repriced into it is news too, so its last change counts.
        fun changedSince(auction: Auction): Boolean =
            (createdMillis(auction) ?: 0L) >= since || (current.search.hasPriceRange && (updatedMillis(auction) ?: 0L) >= since)
        val fresh = if (current.checked) matching.filter { it.id !in seen && changedSince(it) } else emptyList()
        val now = System.currentTimeMillis()
        update { state ->
            state.copy(alerts = state.alerts.map {
                if (it.id == alert.id) it.copy(seen = (matching.map(Auction::id) + it.seen).distinct().take(MAX_SEEN), checked = true, checkedAt = now) else it
            })
        }
        if (fresh.isNotEmpty()) notifier.showAlertMatches(current, fresh)
    }

    /** Looks at one followed auction now, such as when the socket says it got a bid. */
    suspend fun refreshFollowed(id: String) {
        val followed = _state.value.followed.firstOrNull { it.id == id } ?: return
        val auction = try {
            api.auction(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: WfmException) {
            // The site answers a removed auction with a form error on the id.
            if (e.kind == WfmErrorKind.NOT_FOUND || "auction" in e.fieldErrors) {
                notifier.showFollowedEnded(followed, removed = true)
                unfollow(id)
            }
            return
        } catch (e: Exception) {
            return
        }
        if (auction.closed) {
            notifier.showFollowedEnded(followed, removed = false)
            unfollow(id)
            return
        }
        val top = auction.topBid ?: return
        if (top == followed.topBid) return
        update { state -> state.copy(followed = state.followed.map { if (it.id == id) it.copy(topBid = top) else it }) }
        // A bid that was taken back isn't news, and neither is one the user placed.
        if (top < (followed.topBid ?: 0)) return
        if (activeAuctionId == id && isAppForeground()) return
        val me = myId()
        val leader = if (me == null) null else runCatching { api.auctionBids(id).maxByOrNull { it.value }?.user?.id }.getOrNull()
        if (leader != null && leader == me) return
        notifier.showFollowedBid(followed, top)
    }

    private fun createdMillis(auction: Auction): Long? = millis(auction.created)

    private fun updatedMillis(auction: Auction): Long? = millis(auction.updated)

