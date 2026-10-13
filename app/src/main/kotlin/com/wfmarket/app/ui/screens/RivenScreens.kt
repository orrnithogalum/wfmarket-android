@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.api.model.*
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.RivenWeeks
import com.wfmarket.app.data.weekKey
import com.wfmarket.app.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Grades

/** A listed riven's grades, unless they're turned off in Settings. */
@Composable
fun rememberRivenRolls(item: AuctionItem, catalog: Catalog): RivenRolls? {
    val container = appContainer()
    val settings by container.settings.flow.collectAsStateWithLifecycle()
    val classes by container.rivens.classes.collectAsStateWithLifecycle()
    if (!settings.rivenGrades) return null
    return remember(item, catalog, classes) { catalog.rivenRolls(item, classes) }
}

private val WidestGrades = listOf("A+", "B+", "C+")

/** How far apart the bars start filling, and how long after its bar starts a grade appears. */
private const val BAR_STEP_MILLIS = 50L
private const val GRADE_DELAY_MILLIS = 200L

private fun RivenGrade.spoken(): String = label.replace("+", " plus").replace("-", " minus")

/**
 * A stat's grade as a letter. Every grade takes the room of the widest, with the letters at its
 * start, so a list of stats lines up at any text size. A value no roll can reach shows a question
 * mark, and a stat that can't be graded leaves the space empty.
 */
@Composable
fun RivenGradeLabel(roll: StatRoll?, modifier: Modifier = Modifier, revealIndex: Int? = null) {
    val grade = roll?.grade
    val text = when {
        roll?.outOfRange == true -> "?"
        grade != null -> grade.label
        else -> ""
    }
    val spoken = when {
        roll?.outOfRange == true -> "can't be graded"
        grade != null -> "grade ${grade.spoken()}"
        else -> ""
    }
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    // Beside a bar, the letter fades in as the bar nears its value, so the two read as one reading.
    val reveal = remember { Animatable(if (revealIndex == null) 1f else 0f) }
    if (revealIndex != null) {
        LaunchedEffect(Unit) {
            delay(BAR_STEP_MILLIS * revealIndex + GRADE_DELAY_MILLIS)
            reveal.animateTo(1f, Motion.fadeIn())
        }
    }
    Box(
        modifier.semantics(mergeDescendants = true) { if (spoken.isNotEmpty()) contentDescription = spoken }.graphicsLayer { alpha = reveal.value },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Unseen, these size the box to the widest grades.
        WidestGrades.forEach { Text(it, Modifier.alpha(0f).clearAndSetSemantics {}, style = style, maxLines = 1) }
        Text(text, Modifier.clearAndSetSemantics {}, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * Where a stat's roll landed between the worst and best it could be, with those ends under it.
 * The bar fills in as it appears, so it reads as a measurement rather than a slider.
 */
@Composable
fun RivenRollBar(roll: StatRoll, worst: String, best: String, modifier: Modifier = Modifier, index: Int = 0) {
    // The bars fill one shortly after another, so a riven's rolls read top to bottom.
    val fill = remember { Animatable(0f) }
    LaunchedEffect(roll.position) {
        if (fill.value == 0f) delay(BAR_STEP_MILLIS * index)
        fill.animateTo(roll.position.toFloat(), Motion.arrive(500))
    }
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinearProgressIndicator(progress = { fill.value.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row {
            Text("Worst $worst", style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
            Text("Best $best", style = labelStyle, color = labelColor)
        }
    }
}

/** What a riven's grading had to assume, in a line, or null when it assumed nothing. */
fun rivenRollsNote(rolls: RivenRolls, listedRank: Int?): String? = buildList {
    rolls.nowFactor?.let { factor ->
        val percent = ((factor - 1) * 100).roundToInt()
        val change = if (percent >= 0) "$percent% higher" else "${-percent}% lower"
        add("Listed before Riven Disposition was retired, so in game these stats are now about $change.")
    }
    if (rolls.rank != (listedRank ?: 0)) add("These look like maxed stats, so they're graded at rank ${rolls.rank}.")
}.joinToString(" ").ifEmpty { null }

// ---------------------------------------------------------------- Trade figures

private fun Catalog.tradeWeapon(trade: RivenTrade): RivenWeapon? = trade.compatibility?.let { rivenWeaponsByEnglishName[it.lowercase()] }

private fun Catalog.tradeTitle(trade: RivenTrade): String {
    val name = trade.compatibility ?: return "Veiled ${trade.rivenClass?.label?.lowercase() ?: ""} riven".replace("  ", " ")
    return tradeWeapon(trade)?.let { weaponName(AuctionType.RIVEN, it.slug) } ?: name
}

