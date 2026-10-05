package com.wfmarket.app.data

import android.content.Context
import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmJson
import com.wfmarket.api.model.RivenClass
import com.wfmarket.api.model.RivenTrade
import com.wfmarket.api.model.RivenTrades
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One weapon's riven trades in a week: never rolled, and rolled. Either can be missing. */
data class RivenWeek(val publishedMillis: Long, val unrolled: RivenTrade?, val rolled: RivenTrade?)

/** The weeks of riven trades kept for one platform, oldest first. */
data class RivenWeeks(val platform: String, val weeks: List<RivenTrades>) {
    val latest: RivenTrades? get() = weeks.lastOrNull()
    val previous: RivenTrades? get() = weeks.getOrNull(weeks.size - 2)


