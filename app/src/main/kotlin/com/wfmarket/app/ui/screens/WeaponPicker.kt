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

