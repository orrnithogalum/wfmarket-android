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

