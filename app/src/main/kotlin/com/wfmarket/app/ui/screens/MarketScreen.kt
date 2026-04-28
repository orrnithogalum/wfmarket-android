@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.Item
import com.wfmarket.api.model.Order
import com.wfmarket.api.model.PrimeResurgence
import com.wfmarket.api.model.OrderType
import com.wfmarket.api.model.Showcase
import com.wfmarket.api.model.assetUrl
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.humanize
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Browse groups for the catalogue. A group matches items carrying any of its tags and none of its
 * [excluded] ones. Tags describe what an item is for as well as what it is: a Warframe's mods are
 * tagged `warframe`, and a companion's mods `sentinel` or `kubrow`, so mods are kept to Mods.
 */
private enum class Category(val label: String, val tags: Set<String>, val icon: ImageVector, val excluded: Set<String> = emptySet()) {
    SETS("Sets", setOf("set"), Icons.Filled.Layers),
    PRIME("Prime", setOf("prime"), Icons.Filled.Diamond, setOf("mod")),
    WARFRAMES("Warframes", setOf("warframe"), Icons.Filled.SportsMartialArts, setOf("mod", "arcane_enhancement")),
    WEAPONS("Weapons", setOf("weapon"), Icons.Filled.GpsFixed, setOf("mod")),
    MODS("Mods", setOf("mod"), Icons.Filled.Extension),
    // Peculiar mods carry the arcane tag too, but they're mods.
    ARCANES("Arcanes", setOf("arcane_enhancement"), Icons.Filled.AutoFixHigh, setOf("mod")),
    RELICS("Relics", setOf("relic"), Icons.Filled.Hexagon),
    COMPANIONS("Companions", setOf("sentinel", "kubrow", "kavat", "companion"), Icons.Filled.Pets, setOf("mod")),
    SCENES("Scenes", setOf("scene"), Icons.Filled.Landscape),
    // Scenes are tagged misc as well, and have a group of their own.
    MISC("Misc", setOf("misc", "fish", "ayatan_sculpture", "gem", "plant"), Icons.Filled.Category, setOf("scene"));

    fun matches(item: Item): Boolean = item.tags.any { it in tags } && item.tags.none { it in excluded }
}

/** Each category gets its own expressive shape, so the row reads as a set of distinct places. */
private val Category.shape: RoundedPolygon
    get() = when (this) {
        Category.SETS -> MaterialShapes.Cookie9Sided
        Category.PRIME -> MaterialShapes.Gem
        Category.WARFRAMES -> MaterialShapes.Arch
        Category.WEAPONS -> MaterialShapes.SoftBurst
        Category.MODS -> MaterialShapes.Cookie4Sided
        Category.ARCANES -> MaterialShapes.Sunny
        Category.RELICS -> MaterialShapes.Cookie6Sided
        Category.COMPANIONS -> MaterialShapes.Clover4Leaf
        Category.SCENES -> MaterialShapes.Square
        Category.MISC -> MaterialShapes.Flower
    }

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // The chosen tile grows a little and takes the accent color, both eased rather than snapped.
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, Motion.move(250), label = "categoryScale")
    val fill by animateColorAsState(if (selected) colors.primary else colors.secondaryContainer, Motion.fadeIn(), label = "categoryFill")
    val ink by animateColorAsState(if (selected) colors.onPrimary else colors.onSecondaryContainer, Motion.fadeIn(), label = "categoryInk")
    val label by animateColorAsState(if (selected) colors.primary else colors.onSurface, Motion.fadeIn(), label = "categoryLabel")
    Column(
        Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(60.dp).scale(scale).clip(category.shape.toShape()).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.icon, null, Modifier.size(26.dp), tint = ink)
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelMedium,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

