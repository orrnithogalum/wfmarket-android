@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.api.model.AuctionType
import com.wfmarket.api.model.RivenClass
import com.wfmarket.api.model.assetUrl
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

/** A weapon as the picker shows it. Riven weapons also carry their riven and last week's trades. */
private data class WeaponOption(
    val slug: String,
    val name: String,
    val image: String?,
    val rivenClass: RivenClass? = null,
    val median: Double? = null,
    val popularity: Double = 0.0,
)

private fun WeaponOption.detail(): String? = listOfNotNull(
    rivenClass?.label,
    median?.let { "${String.format(Locale.getDefault(), "%,d", it.roundToLong())}p last week" },
).joinToString(" · ").ifEmpty { null }

/** Names starting with a digit or a symbol share one heading. */
private fun WeaponOption.letter(): String = name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

/**
 * Picks the weapon for a search or a new auction. For rivens, the weapons traded most last week
 * come first as tiles, and the rest can be narrowed to one kind of riven. Everything is in rounded
 * groups by first letter, as the app's other long lists are.
 */
@Composable
fun WeaponPicker(
    type: String,
    catalog: Catalog,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    selected: String? = null,
    onPick: (String) -> Unit,
) {
    val container = appContainer()
    val weeks by container.rivens.weeks.collectAsStateWithLifecycle()
    val classes by container.rivens.classes.collectAsStateWithLifecycle()
    val riven = type == AuctionType.RIVEN
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<RivenClass?>(null) }

    val options = remember(type, catalog, classes, weeks) {
        when (type) {
            AuctionType.RIVEN -> {
                val week = weeks?.latest?.trades.orEmpty().filter { it.compatibility != null }.groupBy { it.compatibility!!.lowercase() }
                catalog.rivenWeapons.map { weapon ->
                    val english = catalog.englishName(weapon).lowercase()
                    val lines = week[english].orEmpty()
                    WeaponOption(
                        slug = weapon.slug,
                        name = catalog.i18n(weapon.i18n)?.name ?: weapon.slug,
                        image = assetUrl(catalog.i18n(weapon.i18n)?.icon),
                        rivenClass = classes[english] ?: RivenClass.of(weapon),
                        // Unrolled is what a riven is worth before anyone gambles on it.
                        median = (lines.firstOrNull { !it.rerolled } ?: lines.firstOrNull())?.median,
                        popularity = lines.sumOf { it.pop },
                    )
                }
            }
            AuctionType.LICH -> catalog.lichWeapons.map { WeaponOption(it.slug, catalog.i18n(it.i18n)?.name ?: it.slug, assetUrl(catalog.i18n(it.i18n)?.icon)) }
            else -> catalog.sisterWeapons.map { WeaponOption(it.slug, catalog.i18n(it.i18n)?.name ?: it.slug, assetUrl(catalog.i18n(it.i18n)?.icon)) }
        }.sortedBy { it.name.lowercase() }
    }
    val kinds = remember(options) { RivenClass.entries.filter { kind -> options.any { it.rivenClass == kind } } }
    val shown = remember(options, query, kind) {
        options.filter { (kind == null || it.rivenClass == kind) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    }
    val groups = remember(shown) { shown.groupBy { it.letter() }.toList() }
    val popular = remember(options) { options.filter { it.popularity > 0 }.sortedByDescending { it.popularity }.take(10) }
    val colors = MaterialTheme.colorScheme

    // A choice is shown taken, the row tinting and its check appearing, before the sheet slides
    // away, so the pick reads as done rather than the sheet just vanishing.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(selected) }
    var closing by remember { mutableStateOf(false) }
    fun close(after: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            delay(CONFIRM_MILLIS)
            sheet.hide()
        }.invokeOnCompletion { after() }
    }
    fun choose(slug: String) {
        chosen = slug
        close { onPick(slug) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Weapon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                onClear?.let { clear -> TextButton(onClick = { chosen = null; close(clear) }) { Text("Any weapon") } }
            }
            // The same filled pill as the home page's search bar.
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search weapons") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHighest,
                    unfocusedContainerColor = colors.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            if (riven && kinds.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                    kinds.forEach { entry ->
                        FilterChip(selected = kind == entry, onClick = { kind = if (kind == entry) null else entry }, label = { Text(entry.label) })
                    }
                }
            }
            FadingLazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (riven && query.isBlank() && kind == null && popular.isNotEmpty()) {
                    item(key = "popular-title") { SectionTitle("Most traded last week") }
                    item(key = "popular") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(popular, key = { it.slug }) { option ->
                                SuggestionTile(
                                    option.image, option.name,
                                    option.median?.let { "${String.format(Locale.getDefault(), "%,d", it.roundToLong())}p" },
                                    onClick = { choose(option.slug) },
                                    chosen = option.slug == chosen,
                                )
                            }
                        }
                    }
                }
                if (shown.isEmpty()) item(key = "empty") { EmptyBox("No weapons match") }
                groups.forEach { (letter, group) ->
                    item(key = "letter-$letter") { SectionTitle(letter, listItemMotion()) }
                    itemsIndexed(group, key = { _, option -> option.slug }) { index, option ->
                        val isChosen = option.slug == chosen
                        val container by animateColorAsState(if (isChosen) colors.secondaryContainer else colors.surfaceContainerHigh, Motion.fadeIn(), label = "chosen")
                        SegmentedListItem(
                            onClick = { choose(option.slug) },
                            shapes = ListItemDefaults.segmentedShapes(index, group.size),
                            colors = ListItemDefaults.segmentedColors(containerColor = container),
                            modifier = listItemMotion().padding(horizontal = 16.dp).padding(bottom = ListItemDefaults.SegmentedGap),
                            verticalAlignment = Alignment.CenterVertically,
                            leadingContent = { WeaponThumb(option.image, height = 44.dp, tile = false) },
                            supportingContent = option.detail()?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            trailingContent = {
                                AnimatedVisibility(isChosen, enter = fadeIn(Motion.fadeIn()) + scaleIn(Motion.arrive(220), initialScale = 0.8f), exit = fadeOut(Motion.fadeOut())) {
                                    Icon(Icons.Filled.Check, "Chosen", tint = colors.primary)
                                }
                            },
                        ) { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

/** How long a choice shows as taken before the sheet goes. */
private const val CONFIRM_MILLIS = 140L
