package com.wfmarket.app.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DataKind {
    ORDERS,
    HISTORY,
    AUCTIONS,
    REVIEWS,

    /** Platform, crossplay, language or the signed-in account changed, so everything shown is suspect. */
    SCOPE,
}

/**
 * Says when data changed, so every screen showing it can reload, including ones waiting on the back
 * stack. Repositories report it after each successful change, so no screen has to know who else cares.
 */
class DataChanges {
    private val _flow = MutableSharedFlow<DataKind>(extraBufferCapacity = 16)
    val flow: SharedFlow<DataKind> = _flow.asSharedFlow()

    fun mark(kind: DataKind) {
        _flow.tryEmit(kind)
    }
}

