@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wfmarket.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wfmarket.api.model.Item
import com.wfmarket.api.model.ItemStatistics
import com.wfmarket.api.model.OrderType
import com.wfmarket.api.model.StatPoint
import com.wfmarket.app.data.humanize
import com.wfmarket.app.ui.charts.*
import com.wfmarket.app.ui.components.*
import com.wfmarket.app.ui.theme.LocalSemanticColors
import java.util.Locale

private enum class Range(val label: String) { HOURS_48("48 hours"), DAYS_90("90 days") }

