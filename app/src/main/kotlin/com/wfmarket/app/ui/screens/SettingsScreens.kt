@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wfmarket.app.ui.screens

import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.api.model.Language
import com.wfmarket.api.model.Platform
import com.wfmarket.api.model.assetUrl
import com.wfmarket.app.data.ThemeMode
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Composable
private fun BackBar(title: String, container: Color = MaterialTheme.colorScheme.surface) {
    val navigator = LocalNavigator.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container),
    )
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fadingVerticalScroll()) {
                options.forEach { option -> ChoiceRow(label(option), option == selected) { onSelect(option) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    // The minimum touch height, with nothing added: the dialog's own padding does the spacing.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var batteryExempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleResume { batteryExempt = power.isIgnoringBatteryOptimizations(context.packageName) }

    var platformDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }

    fun updatePreference(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
    }

    var refreshing by remember { mutableStateOf(false) }
    fun refreshAccount() {
        scope.launch {
            refreshing = true
            runCatching { container.account.refresh() }.onFailure { container.refreshFailed() }
            refreshing = false
        }
    }

    val background = settingsBackground
    Scaffold(
        topBar = { BackBar("Settings", background) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = background,
    ) { padding ->
      RefreshBox(isRefreshing = refreshing, onRefresh = ::refreshAccount, modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().fadingVerticalScroll()) {
            SettingsGroup("Market") {
                valueRow("Platform", Platform.of(settings.platform).label) { platformDialog = true }
                switchRow(
                    "Crossplay",
                    if (settings.platform == "switch") "Not available on Switch" else "See and trade with players on other platforms",
                    settings.crossplay && settings.platform != "switch",
                    enabled = settings.platform != "switch",
                ) { updatePreference { container.account.updatePreferences(crossplay = it) } }
                valueRow("Language", Language.of(settings.language).label) { languageDialog = true }
            }

            SettingsGroup("Messages") {
                switchRow(
                    "Messages while the app is closed",
                    "Checks for new messages about every 5 minutes",
                    settings.backgroundMessages,
                ) { container.settings.setBackgroundMessages(it) }
            }

            SettingsGroup("Appearance") {
                valueRow("Theme", settings.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { themeDialog = true }
                switchRow("Dynamic color", "Match your wallpaper", settings.dynamicColor) { container.settings.setDynamicColor(it) }
            }

            SettingsGroup("Notifications") {
                valueRow("Notification settings", "Sounds and categories") {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }

            SettingsGroup("Account") {
                if (signedIn) {
                    valueRow("Sign out") {
                        scope.launch {
                            container.account.signOut()
                            container.notices.show("Signed out")
                            navigator.back()
                        }
                    }
                } else {
                    valueRow("Sign in", "Post orders, message traders and bid on auctions", onClick = navigator::signIn)
                }
            }

            SettingsGroup("About") {
                valueRow("Frequently asked questions") { openUrl("https://warframe.market/faq") }
                valueRow("Terms of service") { openUrl("https://warframe.market/tos") }
                valueRow("Privacy") { openUrl("https://warframe.market/privacy") }
                valueRow("WF Market 1.0", "An unofficial client for warframe.market. Not affiliated with the site or Digital Extremes.")
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
      }
    }

    if (platformDialog) {
        ChoiceDialog(
            "Platform", Platform.entries.toList(), Platform.of(settings.platform), { it.label },
            onDismiss = { platformDialog = false },
            onSelect = {
                platformDialog = false
                updatePreference { container.account.updatePreferences(platform = it.wire) }
            },
        )
    }
    if (languageDialog) {
        ChoiceDialog(
            "Language", Language.entries.toList(), Language.of(settings.language), { it.label },
            onDismiss = { languageDialog = false },
            onSelect = {
                languageDialog = false
                updatePreference { container.account.updatePreferences(locale = it.wire) }
            },
        )
    }
    if (themeDialog) {
        ChoiceDialog(
            "Theme", ThemeMode.entries.toList(), settings.theme, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onDismiss = { themeDialog = false },
            onSelect = {
                themeDialog = false
                container.settings.setTheme(it)
            },
        )
    }
}

