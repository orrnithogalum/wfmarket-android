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

    private val byWeek: List<Map<Pair<String?, Boolean>, RivenTrade>> by lazy {
        weeks.map { week -> week.trades.associateBy { it.weekKey to it.rerolled } }
    }

    /** A weapon's weeks, newest first, by its English name, which is how Warframe names it, or a [weekKey]. */
    fun history(name: String): List<RivenWeek> = weeks.indices.reversed().mapNotNull { index ->
        val lines = byWeek[index]
        val key = name.lowercase()
        val unrolled = lines[key to false]
        val rolled = lines[key to true]
        if (unrolled == null && rolled == null) null else RivenWeek(weeks[index].publishedMillis, unrolled, rolled)
    }

    /** The same line in the week before, to show which way it moved. */
    fun before(trade: RivenTrade): RivenTrade? = if (byWeek.size < 2) null else byWeek[byWeek.size - 2][trade.weekKey to trade.rerolled]
}

/** What identifies a line from week to week. Veiled rivens have no weapon, so their kind stands in for one. */
val RivenTrade.weekKey: String get() = compatibility?.lowercase() ?: "veiled ${itemType.lowercase()}"

private const val REFRESH_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
private const val WEEKS_KEPT = 26
private const val SAME_WEEK_MILLIS = 3 * 24 * 60 * 60 * 1000L

/**
 * Warframe's weekly riven trade figures. Each publication covers one week only, so every new week is
 * kept on disk, and a history builds up from the day the app is installed.
 *
 * The figures also say which riven each weapon takes, more reliably than the site's catalogue does,
 * so grading uses them when they're loaded.
 */
class RivenRepository(
    context: Context,
    private val api: WfmApi,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "riven_trades")
    private val mutex = Mutex()

    @Volatile private var lastFetchMillis = 0L

