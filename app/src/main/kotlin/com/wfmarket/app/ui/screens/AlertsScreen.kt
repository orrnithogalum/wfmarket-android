@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.launch

/** Saved auction searches and followed auctions, in the settings style. */
@Composable
fun AlertsScreen() {
    val container = appContainer()
    val state by container.alerts.state.collectAsStateWithLifecycle()
    val catalog by container.catalog.catalog.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResume { notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val background = settingsBackground

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts") },
                navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = background,
    ) { padding ->
        if (state.alerts.isEmpty() && state.followed.isEmpty()) {
            MessageBox(
                Icons.Outlined.NotificationsNone,
                "No alerts yet",
                body = "Search auctions and tap “Notify me” to hear about new matches. Tap the bell on someone's auction to follow its bids.",
                modifier = Modifier.padding(padding),
                shape = MaterialShapes.Sunny,
            ) {
                FilledTonalButton(onClick = { navigator.auctionSearch(null) }, shapes = ButtonDefaults.shapes()) { Text("Search auctions") }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).fadingVerticalScroll()) {
            if (!notificationsOn) {
                SettingsGroup {
                    valueRow("Notifications are off", "Alerts can't reach you until they're allowed. Tap to turn them on.", icon = Icons.Filled.NotificationsOff) {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
            }
            SettingsGroup(title = "Saved searches") {
                state.alerts.forEachIndexed { index, alert ->
                    row { shapes ->
                        SegmentedListItem(
                            onClick = { navigator.auctionSearch(alert.search) },
                            shapes = shapes,
                            verticalAlignment = Alignment.CenterVertically,
                            supportingContent = { Text(alertSummary(alert.search, catalog)) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = alert.enabled,
                                        onCheckedChange = { on -> scope.launch { container.alerts.setAlertEnabled(alert.id, on) } },
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            container.alerts.removeAlert(alert.id)
                                            val result = snackbar.showSnackbar("Alert deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                            if (result == SnackbarResult.ActionPerformed) container.alerts.restoreAlert(alert, index)
                                        }
                                    }) { Icon(Icons.Filled.Delete, "Delete alert") }
                                }
                            },
                        ) { Text(alert.title) }
                    }
                }
                valueRow("Add a search", icon = Icons.Filled.Add) { navigator.auctionSearch(null) }
            }
            SettingsGroup(title = "Followed auctions") {
                state.followed.forEach { followed ->
                    valueRow(
                        followed.title,
                        followed.topBid?.let { "Top bid ${it}p" } ?: "No bids yet",
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    container.alerts.unfollow(followed.id)
                                    snackbar.showSnackbar("No longer following ${followed.title}")
                                }
                            }) { Icon(Icons.Filled.NotificationsOff, "Stop following") }
                        },
                    ) { navigator.auction(followed.id) }
                }
            }
            Text(
                "Searches are checked about every 5 minutes, also while the app is closed. Followed auctions also update the moment a bid comes in while the app is connected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp).navigationBarsPadding(),
            )
        }
    }
}
