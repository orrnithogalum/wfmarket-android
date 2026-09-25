@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Collects a [SettingsGroup]'s rows, so each can be drawn with the shape for its place. */
class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun row(content: @Composable (shapes: ListItemShapes) -> Unit) {
        rows += content
    }
}

/**
 * A run of settings drawn as one rounded card split into rows, with round outer corners and
 * tight inner ones, as Google Messages and the system settings draw theirs. Screens using it sit on
 * [settingsBackground], so the rows stand out from it.
 */
@Composable
fun SettingsGroup(title: String? = null, modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index, rows.size)) }
        }
    }
}

/** The page behind [SettingsGroup]s: a step up from the rows' own surface. */
val settingsBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer

fun SettingsGroupScope.switchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit,
) = row { shapes ->
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        // The switch already says it's on. A tinted, reshaped row on top of that reads as a selection.
        shapes = shapes.copy(selectedShape = shapes.shape),
        colors = ListItemDefaults.segmentedColors(
            selectedContainerColor = colors.surface,
            selectedContentColor = colors.onSurface,
            selectedLeadingContentColor = colors.onSurfaceVariant,
            selectedTrailingContentColor = colors.onSurfaceVariant,
            selectedSupportingContentColor = colors.onSurfaceVariant,
        ),
        enabled = enabled,
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = icon?.let { { Icon(it, null) } },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

/** A row that opens something, or with no [onClick], one that only informs. */
fun SettingsGroupScope.valueRow(
    title: String,
    value: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) = row { shapes ->
    val leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, null) } }
    val supporting: (@Composable () -> Unit)? = value?.let { { Text(it) } }
    val headline: @Composable () -> Unit = { if (destructive) Text(title, color = MaterialTheme.colorScheme.error) else Text(title) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}
