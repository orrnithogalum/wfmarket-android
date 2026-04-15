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

