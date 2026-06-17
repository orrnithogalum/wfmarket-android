package com.wfmarket.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Collects a [SettingsGroup]'s rows. */
class SettingsGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** A titled run of settings, separated from the next by a divider. */
@Composable
fun SettingsGroup(title: String? = null, modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val rows = SettingsGroupScope().apply(content).rows
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        rows.forEach { it() }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

