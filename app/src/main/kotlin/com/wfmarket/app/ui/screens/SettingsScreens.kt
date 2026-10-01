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
import com.wfmarket.app.data.SwipeAction
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

/** One choice per direction, as Google Messages offers it. */
@Composable
private fun SwipeActionsDialog(right: SwipeAction, left: SwipeAction, onRight: (SwipeAction) -> Unit, onLeft: (SwipeAction) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swipe actions") },
        text = {
            Column(Modifier.fadingVerticalScroll(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("Swipe right" to (right to onRight), "Swipe left" to (left to onLeft)).forEach { (title, choice) ->
                    val (current, onPick) = choice
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                        SwipeAction.entries.forEach { ChoiceRow(it.label, it == current) { onPick(it) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun SwipeAction.summary(direction: String) = "$label: swipe $direction"

@Composable
fun SettingsScreen() {
    val container = appContainer()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
    val me by container.account.me.collectAsStateWithLifecycle()
    val alerts by container.alerts.state.collectAsStateWithLifecycle()
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
    var swipeDialog by remember { mutableStateOf(false) }

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
            if (signedIn) {
                me?.let { user ->
                    SettingsGroup {
                        valueRow(
                            user.ingameName.ifBlank { "Unverified account" },
                            if (user.verification) "Verified · Password, email, linked accounts" else "Not verified · Verify to trade",
                            trailing = { UserAvatar(assetUrl(user.avatar), user.ingameName, size = 40.dp) },
                            onClick = navigator::accountSettings,
                        )
                    }
                }
            }

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

            SettingsGroup("Status") {
                switchRow(
                    "Stay online when the app is closed",
                    if (batteryExempt) "Keeps Online or In game showing until the time you chose runs out."
                    else "Keeps Online or In game showing until the time you chose runs out. Without unrestricted battery use, Android shows a silent notification while it does.",
                    settings.keepStatusInBackground,
                ) { container.settings.setKeepStatusInBackground(it) }
            }

            SettingsGroup("Messages") {
                switchRow(
                    "Messages while the app is closed",
                    "Checks for new messages about every 5 minutes",
                    settings.backgroundMessages,
                ) { container.settings.setBackgroundMessages(it) }
                if (settings.backgroundMessages) {
                    switchRow(
                        "Instant delivery",
                        if (batteryExempt) "Messages and auction bids arrive right away" else "Messages and auction bids arrive right away. Without unrestricted battery use, Android shows a silent notification while the app is closed.",
                        settings.instantMessages,
                    ) { container.settings.setInstantMessages(it) }
                }
                valueRow(
                    "Swipe actions",
                    "${settings.swipeRight.summary("right")}\n${settings.swipeLeft.summary("left")}",
                ) { swipeDialog = true }
                valueRow("Unrestricted battery use", if (batteryExempt) "Allowed" else "Recommended, or Android may delay messages") {
                    requestBatteryExemption(context)
                }
            }

            SettingsGroup("Auctions") {
                val count = alerts.alerts.size + alerts.followed.size
                valueRow(
                    "Alerts and followed auctions",
                    if (count == 0) "Get notified about new matches for a search, or new bids on an auction" else "${alerts.alerts.size} saved ${if (alerts.alerts.size == 1) "search" else "searches"} · ${alerts.followed.size} followed",
                    onClick = navigator::alerts,
                )
                switchRow(
                    "Updates on your auctions",
                    "Bids and changes on auctions you run, while the app is connected",
                    settings.notifyAuctions,
                ) { container.settings.setNotifyAuctions(it) }
                switchRow(
                    "Riven grades",
                    "A letter from S to F beside each riven stat, for how well it rolled",
                    settings.rivenGrades,
                ) { container.settings.setRivenGrades(it) }
            }

            SettingsGroup("Appearance") {
                valueRow("Theme", settings.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { themeDialog = true }
                switchRow("Dynamic color", "Match your wallpaper", settings.dynamicColor) { container.settings.setDynamicColor(it) }
                if (settings.theme != ThemeMode.LIGHT) {
                    switchRow("AMOLED black", "Pure black backgrounds in dark theme", settings.amoled) { container.settings.setAmoled(it) }
                }
                switchRow(
                    "Back to top button",
                    "Shows on long lists when you start scrolling back up",
                    settings.scrollToTopButton,
                ) { container.settings.setScrollToTopButton(it) }
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
    if (swipeDialog) {
        SwipeActionsDialog(
            settings.swipeRight, settings.swipeLeft,
            onRight = container.settings::setSwipeRight,
            onLeft = container.settings::setSwipeLeft,
            onDismiss = { swipeDialog = false },
        )
    }
}

/** A form on its own card, to sit among [SettingsGroup]s. */
@Composable
private fun FormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Hide password" else "Show password")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private val LinkableServices = listOf(
    "steam" to "Steam",
    "xbox" to "Xbox",
    "playstation" to "PlayStation",
    "discord" to "Discord",
    "github" to "GitHub",
    "patreon" to "Patreon",
)

@Composable
fun AccountSettingsScreen() {
    val container = appContainer()
    val me by container.account.me.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openUrl = rememberOpenUrl()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var currentForPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var currentForEmail by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    var confirmDeletion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { container.account.refresh() } }

    var refreshing by remember { mutableStateOf(false) }

    val background = settingsBackground
    Scaffold(topBar = { BackBar("Account", background) }, snackbarHost = { SnackbarHost(snackbar) }, containerColor = background) { padding ->
      RefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                runCatching { container.account.refresh() }.onFailure { container.refreshFailed() }
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
      ) {
        Column(
            Modifier.fillMaxSize().imePadding().fadingVerticalScroll(),
        ) {
            val user = me
            SettingsGroup("Verification") {
                if (user?.verification == true) {
                    valueRow("Verified as ${user.ingameName}", "Changed your in-game name? Follow the same steps again to update it.")
                } else {
                    valueRow("Not verified", "Verify to post orders and send messages.")
                }
            }
            user?.checkCode?.let { code ->
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(code, Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                            IconButton(onClick = {
                                copyToClipboard(context, code)
                                scope.launch { snackbar.showSnackbar("Code copied") }
                            }) { Icon(Icons.Filled.ContentCopy, "Copy code") }
                        }
                    }
                    Text(
                        "Send this code in a private forum message to the bot KycKyc on forums.warframe.com. It checks its inbox every 2 minutes, and your account takes the name you have on the forum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    OutlinedButton(onClick = { openUrl("https://warframe.market/settings") }, shapes = ButtonDefaults.shapes()) { Text("Full instructions on the site") }
                }
            }

            FormCard("Change password") {
                PasswordField("Current password", currentForPassword, { currentForPassword = it })
                PasswordField("New password", newPassword, { newPassword = it })
                PasswordField("Confirm new password", confirmPassword, { confirmPassword = it })
                passwordError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = currentForPassword.isNotEmpty() && newPassword.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        if (newPassword != confirmPassword) {
                            passwordError = "The new passwords don't match."
                            return@Button
                        }
                        passwordError = null
                        scope.launch {
                            runCatching { container.account.changePassword(currentForPassword, newPassword, confirmPassword) }
                                .onSuccess {
                                    currentForPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    snackbar.showSnackbar("Password changed")
                                }
                                .onFailure { passwordError = it.userMessage() }
                        }
                    },
                ) { Text("Change password") }
            }

            FormCard("Change email") {
                OutlinedTextField(
                    newEmail,
                    { newEmail = it },
                    label = { Text("New email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField("Current password", currentForEmail, { currentForEmail = it })
                emailError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = currentForEmail.isNotEmpty() && newEmail.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        emailError = null
                        scope.launch {
                            runCatching { container.account.changeEmail(currentForEmail, newEmail.trim()) }
                                .onSuccess {
                                    currentForEmail = ""
                                    newEmail = ""
                                    snackbar.showSnackbar("Check your inbox to confirm the new email")
                                }
                                .onFailure { emailError = it.userMessage() }
                        }
                    },
                ) { Text("Change email") }
            }

            SettingsGroup("Linked accounts") {
                LinkableServices.forEach { (service, label) ->
                    val linked = user?.linkedAccounts?.let { accounts ->
                        (accounts[service] ?: accounts["${service}_profile"])?.let { (it as? JsonPrimitive)?.booleanOrNull }
                    } == true
                    valueRow(
                        label,
                        if (linked) "Linked" else "Not linked",
                        trailing = {
                            if (linked) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { container.account.unlinkAccount(service) }
                                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                                    }
                                }) { Text("Remove") }
                            } else {
                                TextButton(onClick = { openUrl("https://warframe.market/settings/account") }) { Text("Link on site") }
                            }
                        },
                    )
                }
            }

            user?.ignoreList?.takeIf { it.isNotEmpty() }?.let { ignored ->
                SettingsGroup("Ignored players · ${ignored.size}") {
                    ignored.forEach { id ->
                        valueRow(
                            id,
                            trailing = {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { container.chats.unignore(id) }.onFailure { snackbar.showSnackbar(it.userMessage()) }
                                    }
                                }) { Text("Unignore") }
                            },
                        )
                    }
                }
            }

            SettingsGroup("Danger zone") {
                if (user?.deleteInProgress == true) {
                    valueRow(
                        "Account scheduled for deletion",
                        user.deleteAt?.let { "On ${formatDate(it)}" },
                        trailing = {
                            TextButton(onClick = {
                                scope.launch { runCatching { container.account.cancelDeletion() }.onFailure { snackbar.showSnackbar(it.userMessage()) } }
                            }) { Text("Cancel") }
                        },
                    )
                } else {
                    valueRow("Delete account", "Schedules deletion of your account", destructive = true) { confirmDeletion = true }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
      }
    }

    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text("Delete your account?") },
            text = { Text("Your account, orders and history will be removed once the waiting period ends. You can cancel until then.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletion = false
                    scope.launch {
                        runCatching { container.account.requestDeletion() }
                            .onSuccess { snackbar.showSnackbar("Deletion scheduled") }
                            .onFailure { snackbar.showSnackbar(it.userMessage()) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text("Keep account") } },
        )
    }
}

@Composable
fun SignInScreen() {
    val container = appContainer()
    val navigator = LocalNavigator.current
    val openUrl = rememberOpenUrl()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (email.isBlank() || password.isEmpty() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { container.account.signIn(email, password) }
                .onSuccess { navigator.back() }
                .onFailure {
                    busy = false
                    error = it.userMessage()
                }
        }
    }

    Scaffold(topBar = { BackBar("Sign in") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().fadingVerticalScroll().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your warframe.market account", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                email,
                { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField("Password", password, { password = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::submit, enabled = !busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
            }
            Text(
                "Your password goes only to warframe.market and isn't stored on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Creating an account and resetting a password need a reCAPTCHA check, so they open on the website. If you log in with Steam, Xbox or Discord, add an email and password under Settings › Account on the website first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openUrl("https://warframe.market/auth/register") }, modifier = Modifier.weight(1f)) { Text("Create account") }
                OutlinedButton(onClick = { openUrl("https://warframe.market/auth/restore") }, modifier = Modifier.weight(1f)) { Text("Forgot password") }
            }
        }
    }
}
