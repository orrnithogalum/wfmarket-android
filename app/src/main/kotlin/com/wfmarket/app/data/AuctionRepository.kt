package com.wfmarket.app.data

import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmErrorKind
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.model.CreateAuction
import com.wfmarket.api.model.UpdateAuction
import com.wfmarket.api.socket.SocketEvent
import com.wfmarket.api.socket.WfmSockets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Riven, lich and sister auctions. Bids go over the socket; everything else is HTTP. */
class AuctionRepository(private val api: WfmApi, private val sockets: WfmSockets, private val changes: DataChanges) {
    /** Lists can lag behind a close, and a closed auction should never come back on screen. */
    private val closed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * The auction last opened from a list, handed to its page so the page shows at once, while it
     * fetches the latest, and so its picture has somewhere to fly to.
     */
    @Volatile var handoff: Auction? = null

