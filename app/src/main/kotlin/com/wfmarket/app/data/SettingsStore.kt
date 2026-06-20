package com.wfmarket.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wfmarket.api.WfmContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ThemeMode { SYSTEM, LIGHT, DARK }


/**
 * The status last chosen, here or on another device, and when its hold runs out. The site drops
 * you to offline whenever the connection closes and doesn't restore your choice when it reopens,
 * so the app keeps this to put it back.
 */
data class HeldStatus(val status: String, val until: Long?) {
    /** Online and in game are the statuses worth staying connected for. Invisible looks like offline. */
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        (status == "online" || status == "ingame") && (until == null || now < until)
}

data class AppSettings(
    val platform: String = "pc",
    val crossplay: Boolean = true,
    val language: String = "en",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val backgroundMessages: Boolean = true,
    val instantMessages: Boolean = false,
    val notifyAuctions: Boolean = true,
    val statusDurationOption: Int = 0,
    val batteryPromptDismissed: Boolean = false,
    val keepStatusInBackground: Boolean = true,
    val heldStatus: HeldStatus? = null,
)

private val Context.dataStore by preferencesDataStore("settings")

/** User preferences. Read synchronously by the API layer, so the first load blocks once at startup. */
class SettingsStore(private val context: Context, private val scope: CoroutineScope) {
    private object Keys {
        val platform = stringPreferencesKey("platform")
        val crossplay = booleanPreferencesKey("crossplay")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val amoled = booleanPreferencesKey("amoled")
        val backgroundMessages = booleanPreferencesKey("background_messages")
        val instantMessages = booleanPreferencesKey("instant_messages")
        val notifyAuctions = booleanPreferencesKey("notify_auctions")
        val statusDurationOption = intPreferencesKey("status_duration_option")
        val batteryPromptDismissed = booleanPreferencesKey("battery_prompt_dismissed")
        val keepStatusInBackground = booleanPreferencesKey("keep_status_in_background")
        val heldStatus = stringPreferencesKey("held_status")
        val heldStatusUntil = longPreferencesKey("held_status_until")
    }

    private fun Preferences.toSettings() = AppSettings(
        platform = this[Keys.platform] ?: "pc",
        crossplay = this[Keys.crossplay] ?: true,
        language = this[Keys.language] ?: defaultLanguage(),
        theme = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.dynamicColor] ?: true,
        amoled = this[Keys.amoled] ?: false,
        backgroundMessages = this[Keys.backgroundMessages] ?: true,
        instantMessages = this[Keys.instantMessages] ?: false,
        notifyAuctions = this[Keys.notifyAuctions] ?: true,
        statusDurationOption = this[Keys.statusDurationOption] ?: 0,
        batteryPromptDismissed = this[Keys.batteryPromptDismissed] ?: false,
        keepStatusInBackground = this[Keys.keepStatusInBackground] ?: true,
        heldStatus = this[Keys.heldStatus]?.let { HeldStatus(it, this[Keys.heldStatusUntil]) },
    )


    private fun defaultLanguage(): String {
        val locale = context.resources.configuration.locales[0]
        val tag = when {
            locale.language == "zh" && (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) -> "zh-hant"
            locale.language == "zh" -> "zh-hans"
            else -> locale.language
        }
        return com.wfmarket.api.model.Language.entries.firstOrNull { it.wire == tag }?.wire ?: "en"
    }

    val flow: StateFlow<AppSettings> = context.dataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { context.dataStore.data.first().toSettings() })

    val current: AppSettings get() = flow.value


