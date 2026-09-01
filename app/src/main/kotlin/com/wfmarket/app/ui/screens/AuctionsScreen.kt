@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.EditNotifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.wfmarket.app.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private enum class AuctionTab(val label: String, val needsAccount: Boolean = false) {
    SEARCH("Search"), RECENT("Recent"), POPULAR("Popular"), PRICES("Riven prices"), MINE("My auctions", true), BIDS("My bids", true)
}

class AuctionsViewModel(private val container: AppContainer) : ViewModel() {
    private val _lists = MutableStateFlow<Map<String, Load<List<Auction>>>>(emptyMap())
    val lists = _lists.asStateFlow()
    private val _search = MutableStateFlow(AuctionSearch())
    val search = _search.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()

    init {
        // Every tab that's been opened could show what changed, not just the one on screen.
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                if (kind == DataKind.AUCTIONS || kind == DataKind.SCOPE) _lists.value.keys.forEach { update(it) }
            }
        }
    }


