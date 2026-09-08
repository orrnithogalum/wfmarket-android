@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.wfmarket.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import com.wfmarket.app.ui.theme.LocalSemanticColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuctionDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    private val _auction = MutableStateFlow<Load<Auction>>(Load.Loading)
    val auction = _auction.asStateFlow()
    private val _bids = MutableStateFlow<List<Bid>>(emptyList())
    val bids = _bids.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        container.auctions.handedOff(id)?.let { _auction.value = Load.Ready(it) }
        load()
        container.auctions.watch(id)
        // Someone else bidding should show up without a manual refresh.
        viewModelScope.launch {
            container.auctions.bidChanges.collect { change -> if (change.auctionId == id) load() }
        }
        viewModelScope.launch {
            container.changes.flow.collect { kind -> if (kind == DataKind.AUCTIONS || kind == DataKind.SCOPE) load() }
        }
    }


