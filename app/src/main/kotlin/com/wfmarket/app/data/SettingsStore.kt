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

