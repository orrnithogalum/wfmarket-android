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

    private val _weeks = MutableStateFlow<RivenWeeks?>(null)
    /** The current platform's weeks, or null before any are loaded. */
    val weeks: StateFlow<RivenWeeks?> = _weeks.asStateFlow()

    private val _classes = MutableStateFlow<Map<String, RivenClass>>(emptyMap())
    /** Which riven each weapon takes, by lowercased English name. */
    val classes: StateFlow<Map<String, RivenClass>> = _classes.asStateFlow()

    @Volatile private var lastFetchMillis = 0L

    fun start() {
        scope.launch {
            settings.flow.map { it.platform }.distinctUntilChanged().collect { platform ->
                lastFetchMillis = 0L
                load(platform)
                runCatching { refresh() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
    }

    /** Warframe publishes on Mondays, so a few checks a day are plenty. */
    fun refreshIfOld() {
        if (System.currentTimeMillis() - lastFetchMillis > REFRESH_INTERVAL_MILLIS) scope.launch { runCatching { refresh() } }
    }

    /** Fetches the latest week. Returns false when there's none for this platform. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val platform = settings.current.platform
        val week = api.rivenTrades(platform) ?: return@withContext false
        lastFetchMillis = System.currentTimeMillis()
        // An empty file is an outage, not a week without trades, and would hide the last good week.
        if (week.trades.isEmpty()) return@withContext true
        mutex.withLock {
            val known = _weeks.value?.takeIf { it.platform == platform }?.weeks ?: readAll(platform)
            val updated = if (known.any { it.publishedMillis == week.publishedMillis }) {
                known
            } else {
                // A file put out again within days, say to fix it, is the same week, not a new one.
                // Kept twice, it would be compared with itself.
                val sameWeek = known.filter { abs(it.publishedMillis - week.publishedMillis) < SAME_WEEK_MILLIS }
                sameWeek.forEach { delete(it) }
                write(week)
                (known - sameWeek.toSet() + week).sortedBy { it.publishedMillis }.takeLast(WEEKS_KEPT)
            }
            if (settings.current.platform == platform) publish(RivenWeeks(platform, updated))
        }
        true
    }

