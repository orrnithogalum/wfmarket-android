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

/** Creates a ViewModel backed by the app's repositories, keyed for per-argument instances. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Navigation actions any screen can trigger, so screens don't depend on the nav graph's routes. */
interface AppNavigator {
    fun back()
    fun item(slug: String)
    fun auction(id: String)

    /** Opens an auction in place of this screen, so back skips the form that created it. */
    fun replaceWithAuction(id: String)
    fun editAuction(id: String?)
    fun chat(id: String)
    fun user(slug: String)
    fun settings()
    fun accountSettings()
    fun signIn()


