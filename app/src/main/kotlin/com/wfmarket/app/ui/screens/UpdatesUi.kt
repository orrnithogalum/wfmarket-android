@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.app.BuildConfig
import com.wfmarket.app.data.AppSettings
import com.wfmarket.app.update.AppUpdater
import com.wfmarket.app.update.Release
import com.wfmarket.app.update.UpdateState
import com.wfmarket.app.update.release
import com.wfmarket.app.ui.components.Motion
import com.wfmarket.app.ui.components.SettingsGroupScope
import com.wfmarket.app.ui.components.appContainer
import com.wfmarket.app.ui.components.relativeTime
import com.wfmarket.app.ui.components.switchRow
import com.wfmarket.app.ui.components.valueRow
import java.time.Instant
import kotlin.math.roundToInt

/** Whether a new version is waiting for the user, so the way to Settings can show a dot. */
@Composable
fun rememberUpdateWaiting(): Boolean {
    val updater = appContainer().updater
    val state by updater.state.collectAsStateWithLifecycle()
    val dismissed by updater.dismissed.collectAsStateWithLifecycle()
    val release = state.release ?: return false
    val waiting = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    return updater.supported && waiting && release.version != dismissed
}

/** The settings gear, with a dot on it while a new version waits. */
@Composable
fun SettingsIcon() {
    if (rememberUpdateWaiting()) {
        BadgedBox(badge = { Badge() }) { Icon(Icons.Filled.Settings, "Settings, update available") }
    } else {
        Icon(Icons.Filled.Settings, "Settings")
    }
}

/**
 * Starts installing the downloaded update. Android needs the user's permission, once, before the
 * app can install anything, so the first time this explains and opens that setting, then carries
 * on with the install when the user comes back with it allowed.
 */
@Composable
fun rememberUpdateInstaller(updater: AppUpdater): () -> Unit {
    val context = LocalContext.current
    var explain by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }
    LifecycleResume {
        if (awaitingPermission) {
            awaitingPermission = false
            if (updater.canInstall()) updater.install()
        }
    }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false },
            icon = { Icon(Icons.Filled.InstallMobile, null) },
            title = { Text("Allow updates to install") },
            text = {
                Text("Android asks you once before Warframe Market can install its own updates. Turn on Allow from this source on the next screen, then come back, and the update carries on.")
            },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    awaitingPermission = true
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } },
        )
    }
    return { if (updater.canInstall()) updater.install() else explain = true }
}

/**
 * A new version at the top of Settings, from finding it to installing it: what it is, a bar while
 * it downloads, what's new in it, and the one button that moves it along.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val release = state.release ?: return
    val colors = MaterialTheme.colorScheme
    val updater = appContainer().updater
    DisposableEffect(updater) {
        updater.cardsShown++
        onDispose { updater.cardsShown-- }
    }
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp), shape = MaterialTheme.shapes.large, color = colors.primaryContainer) {
        Column(Modifier.animateContentSize(Motion.move()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UpdateIcon(state)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Warframe Market ${release.version}", style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                    // The line rises into place when the update moves to its next step. Within a step,
                    // such as the download counting up, it changes in place with figures of one width,
                    // so nothing shifts as they tick.
                    AnimatedContent(
                        state,
                        contentKey = { it::class },
                        transitionSpec = {
                            (slideInVertically(Motion.arrive(250)) { it / 3 } + fadeIn(Motion.fadeIn())).togetherWith(fadeOut(Motion.fadeOut()))
                        },
                        label = "updateLine",
                    ) { step ->
                        Text(cardLine(step), style = MaterialTheme.typography.bodyMedium.withSteadyFigures(), color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
                var percent by remember { mutableIntStateOf(0) }
                if (state is UpdateState.Downloading) percent = (state.fraction * 100).roundToInt()
                AnimatedVisibility(state is UpdateState.Downloading, enter = fadeIn(Motion.fadeIn()), exit = fadeOut(Motion.fadeOut())) {
                    Text("$percent%", style = MaterialTheme.typography.labelLarge.withSteadyFigures(), color = colors.onPrimaryContainer)
                }
            }
            DownloadBar(state)
            if (release.notes.isNotBlank()) ReleaseNotes(release)
            UpdateButtons(state, onDownload, onCancel, onInstall, onLater)
        }
    }
}

/** A round badge whose picture follows the update along: found, downloading, downloaded, installing. */
@Composable
private fun UpdateIcon(state: UpdateState) {
    val colors = MaterialTheme.colorScheme
    val icon: ImageVector = when (state) {
        is UpdateState.Downloading -> Icons.Filled.Download
        is UpdateState.Ready -> Icons.Filled.DownloadDone
        is UpdateState.Installing -> Icons.Filled.InstallMobile
        is UpdateState.Failed -> Icons.Filled.ErrorOutline
        else -> Icons.Filled.SystemUpdate
    }
    // While it downloads, the arrow dips gently, as if pulling the update down.
    val dip = if (state is UpdateState.Downloading) {
        val motion = rememberInfiniteTransition(label = "dip")
        motion.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = Motion.Emphasized), RepeatMode.Reverse), label = "dip").value
    } else {
        0f
    }
    val lift = with(LocalDensity.current) { 2.dp.toPx() }
    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
        AnimatedContent(
            icon,
            transitionSpec = {
                (fadeIn(Motion.fadeIn()) + scaleIn(Motion.arrive(300), initialScale = 0.6f)).togetherWith(fadeOut(Motion.fadeOut()) + scaleOut(Motion.fadeOut(), targetScale = 0.6f))
            },
            label = "updateIcon",
        ) {
            Icon(it, null, Modifier.size(24.dp).graphicsLayer { translationY = dip * lift }, tint = colors.onPrimary)
        }
    }
}

/**
 * The download's progress, as Material's wavy bar: the wave travels while bytes arrive and lies
 * flat once the bar is full. Its room is kept from the start, so the card never changes size: the
 * bar fades in when the download starts, and stays full when it's done.
 */
@Composable
private fun DownloadBar(state: UpdateState) {
    var last by remember { mutableFloatStateOf(0f) }
    if (state is UpdateState.Downloading) last = state.fraction
    if (state is UpdateState.Ready || state is UpdateState.Installing) last = 1f
    val shown by animateFloatAsState(last, tween(220, easing = LinearEasing), label = "download")
    val visible = state is UpdateState.Downloading || state is UpdateState.Ready || state is UpdateState.Installing
    val alpha by animateFloatAsState(if (visible) 1f else 0f, if (visible) Motion.fadeIn(300) else Motion.fadeOut(), label = "barAlpha")
    val colors = MaterialTheme.colorScheme
    val modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }
    if (state is UpdateState.Installing) {
        LinearWavyProgressIndicator(modifier, color = colors.primary, trackColor = colors.primary.copy(alpha = 0.2f))
    } else {
        LinearWavyProgressIndicator(progress = { shown }, modifier = modifier, color = colors.primary, trackColor = colors.primary.copy(alpha = 0.2f))
    }
}

/** What's new: a few lines, and the rest a tap away. */
@Composable
private fun ReleaseNotes(release: Release) {
    val colors = MaterialTheme.colorScheme
    val text = remember(release.notes) { releaseNotesText(release.notes) }
    var expanded by remember { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("What's new", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onPrimaryContainer,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 32.dp)) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

@Composable
private fun UpdateButtons(state: UpdateState, onDownload: () -> Unit, onCancel: () -> Unit, onInstall: () -> Unit, onLater: () -> Unit) {
    // A button's height even while installing, when there's nothing to press, so the card keeps its size.
    Row(
        Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MinHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is UpdateState.Available -> {
                TextButton(onClick = onLater) { Text("Later") }
                Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Download") }
            }
            // Outlined rather than plain text, so it sits on the card's 16dp edge as Install does after it.
            is UpdateState.Downloading -> OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            is UpdateState.Ready -> {
                TextButton(onClick = onLater) { Text("Later") }
                Button(onClick = onInstall, shapes = ButtonDefaults.shapes()) { Text("Install") }
            }
            is UpdateState.Failed -> {
                TextButton(onClick = onLater) { Text("Later") }
                Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) { Text("Try again") }
            }
            else -> Unit
        }
    }
}

/** Digits of one width, so a number that keeps changing doesn't nudge what's beside it. */
private fun TextStyle.withSteadyFigures() = copy(fontFeatureSettings = "tnum")

private fun cardLine(state: UpdateState): String = when (state) {
    is UpdateState.Available -> "New version · ${megabytes(state.release.apkSize)}"
    is UpdateState.Downloading -> "Downloading · ${megabytes(state.bytes, withUnit = false)} of ${megabytes(state.total)}"
    is UpdateState.Ready -> "Downloaded and ready to install"
    is UpdateState.Installing -> "Installing. The app will close."
    is UpdateState.Failed -> state.message
    else -> ""
}

private fun megabytes(bytes: Long, withUnit: Boolean = true): String =
    "%.1f".format(bytes / 1_000_000.0) + if (withUnit) " MB" else ""

/** The Updates group's line under the version: where things stand. */
private fun versionLine(state: UpdateState): String = when (state) {
    is UpdateState.UpToDate -> state.checkedAt?.let { "Up to date · Checked ${relativeTime(Instant.ofEpochMilli(it).toString())}" } ?: "Tap to check for updates"
    UpdateState.Checking -> "Checking for updates…"
    is UpdateState.Available -> "Version ${state.release.version} is available"
    is UpdateState.Downloading -> "Downloading version ${state.release.version}"
    is UpdateState.Ready -> "Version ${state.release.version} is ready to install"
    is UpdateState.Installing -> "Installing version ${state.release.version}"
    is UpdateState.Failed -> state.message
}

/** The Updates group in Settings: the version and its status, the two switches, and every release. */
fun SettingsGroupScope.updateRows(updater: AppUpdater, state: UpdateState, settings: AppSettings, onCheck: () -> Unit, onSetCheck: (Boolean) -> Unit, onSetDownload: (Boolean) -> Unit, openUrl: (String) -> Unit) {
    if (!updater.supported) {
        valueRow("Version ${BuildConfig.VERSION_NAME}", "Debug builds don't update themselves. Install a release from GitHub to get updates.")
        return
    }
    valueRow(
        "Version ${BuildConfig.VERSION_NAME}",
        versionLine(state),
        trailing = { CheckIndicator(state == UpdateState.Checking) },
        onClick = onCheck,
    )
    switchRow("Check for updates", "Looks for a new version on GitHub about twice a day", settings.checkUpdates, onChange = onSetCheck)
    switchRow(
        "Download updates automatically",
        "New versions download in the background, ready to install",
        settings.autoDownloadUpdates && settings.checkUpdates,
        enabled = settings.checkUpdates,
        onChange = onSetDownload,
    )
    valueRow("Release notes", "Every version and what changed in it, on GitHub") { openUrl(updater.releasesPage) }
}

/** A refresh arrow that turns while a check runs. */
@Composable
private fun CheckIndicator(checking: Boolean) {
    val turn = if (checking) {
        val motion = rememberInfiniteTransition(label = "checking")
        motion.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn").value
    } else {
        0f
    }
    // Stopping eases back to upright rather than freezing at an angle.
    val angle by animateFloatAsState(if (checking) turn else 0f, if (checking) tween(0) else Motion.move(300), label = "angle")
    Icon(Icons.Filled.Refresh, null, Modifier.graphicsLayer { rotationZ = angle }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A release's notes, which are Markdown on GitHub, as styled text: headings in bold, list items
 * with bullets, and links as their words. GitHub's "Full Changelog" link is left out.
 */
fun releaseNotesText(markdown: String): AnnotatedString = buildAnnotatedString {
    var first = true
    for (raw in markdown.lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("**Full Changelog**")) continue
        if (!first) append('\n')
        first = false
        when {
            line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inlineText(line.trimStart('#').trim())) }
            line.startsWith("- ") || line.startsWith("* ") -> append("•  " + inlineText(line.drop(2)))
            else -> append(inlineText(line))
        }
    }
}

private val MarkdownLink = Regex("""\[([^\]]+)]\([^)]+\)""")

private fun inlineText(text: String) = text.replace(MarkdownLink, "$1").replace("**", "").replace("`", "")
