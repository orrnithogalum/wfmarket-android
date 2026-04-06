package com.wfmarket.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wfmarket.app.WfMarketApp
import com.wfmarket.app.data.AppContainer

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as WfMarketApp).container

