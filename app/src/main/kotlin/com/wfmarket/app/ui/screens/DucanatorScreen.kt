@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wfmarket.api.model.DucatEntry
import com.wfmarket.api.model.Ducats
import com.wfmarket.api.model.assetUrl
import com.wfmarket.api.model.localized
import com.wfmarket.app.data.AppContainer
import com.wfmarket.app.data.Catalog
import com.wfmarket.app.data.DataKind
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class DucanatorViewModel(private val container: AppContainer) : ViewModel() {
    private val _data = MutableStateFlow<Load<Ducats>>(Load.Loading)
    val data = _data.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.changes.flow.collect { if (it == DataKind.SCOPE) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val hadData = _data.value is Load.Ready
            _refreshing.value = hadData
            val updated = _data.accept(runCatching { container.market.ducats() })
            if (hadData && !updated) container.refreshFailed()
            _refreshing.value = false
        }
    }
}

private enum class DucatSort(val label: String) {
    DPP("Ducats/Plat"),
    DPP_WA("Ducats/Plat (WA)"),
    WA_PRICE("WA price"),
    MEDIAN("Median"),
    DUCATS("Ducats"),
    VOLUME("Volume"),
    NAME("Name"),
    POS_DAY("Rank change, day"),
    POS_WEEK("Rank change, week"),
    POS_MONTH("Rank change, month"),
}

private enum class DucatPeriod(val label: String) { HOUR("Hour"), DAY("Day") }

