@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.wfmarket.api.model.*
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.components.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class ItemViewModel(private val container: AppContainer, slug: String) : ViewModel() {
    private val _slug = MutableStateFlow(slug)
    val slug = _slug.asStateFlow()
    private val _item = MutableStateFlow<Load<Item>>(Load.Loading)
    val item = _item.asStateFlow()
    private val _setItems = MutableStateFlow<List<Item>>(emptyList())
    val setItems = _setItems.asStateFlow()
    private val _orders = MutableStateFlow<Load<List<Order>>>(Load.Loading)
    val orders = _orders.asStateFlow()
    private val _statistics = MutableStateFlow<Load<ItemStatistics>>(Load.Loading)
    val statistics = _statistics.asStateFlow()
    private val _drops = MutableStateFlow<Load<List<DropSource>>>(Load.Loading)
    val drops = _drops.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    /** Parts already visited, so going back to one shows it at once while it updates. */
    private val visited = mutableMapOf<String, VisitedPart>()
    private var partJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            container.changes.flow.collect { kind ->
                when (kind) {
                    DataKind.ORDERS -> launch { fetchOrders() }
                    DataKind.SCOPE -> load()
                    else -> Unit
                }
            }
        }
    }

    /** Switches to another part of the set in place: only what differs between parts reloads. */
    fun select(part: Item) {
        if (part.slug == _slug.value) return
        remember()
        _slug.value = part.slug
        val known = visited[part.slug]
        _item.value = Load.Ready(known?.item ?: part)
        _orders.value = known?.orders ?: Load.Loading
        _statistics.value = known?.statistics ?: Load.Loading
        _drops.value = known?.drops ?: Load.Loading
        loadPart()
    }

    private fun remember() {
        val item = (_item.value as? Load.Ready)?.value ?: return
        visited[_slug.value] = VisitedPart(item, _orders.value, _statistics.value, _drops.value)
    }

    fun load() {
        viewModelScope.launch {
            runCatching { container.market.itemSet(_slug.value) }.onSuccess { _setItems.value = it.items }
        }
        loadPart()
    }

    private fun loadPart() {
        partJob?.cancel()
        partJob = viewModelScope.launch { fetchPart() }
    }

    private suspend fun fetchPart(): Boolean = coroutineScope {
        listOf(
            async { fetchItem() },
            async { fetchOrders() },
            async { fetchStatistics() },
            async { fetchDrops() },
        ).awaitAll().all { it }
    }

    private suspend fun fetchItem() = _item.accept(runCatching { container.market.item(_slug.value) })

    private suspend fun fetchOrders() = _orders.accept(runCatching { container.market.orders(_slug.value) })

    private suspend fun fetchStatistics() = _statistics.accept(runCatching { container.market.statistics(_slug.value) })

    private suspend fun fetchDrops() = _drops.accept(
        // Items with no known sources answer 404, which simply means an empty list here.
        runCatching { container.market.dropSources(_slug.value) }.recoverCatching { failure ->
            if ((failure as? com.wfmarket.api.WfmException)?.kind == com.wfmarket.api.WfmErrorKind.NOT_FOUND) emptyList() else throw failure
        },
    )


