package com.wfmarket.app

import android.Manifest
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wfmarket.app.data.ThemeMode
import com.wfmarket.app.ui.nav.AppRoot
import com.wfmarket.app.ui.theme.WfMarketTheme

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<Uri?>(null)

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLink = intent?.data
        val container = (application as WfMarketApp).container
        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle()
            val signedIn by container.account.isSignedIn.collectAsStateWithLifecycle()
            // Asked right after sign-in, when the reason for it is obvious.
            LaunchedEffect(signedIn) {
                if (signedIn && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // The splash shows before any code runs, so Android is told in advance what the next one
            // should look like, including the app's own theme choice if it differs from the system's.
            LaunchedEffect(settings.theme) {
                getSystemService(UiModeManager::class.java).setApplicationNightMode(
                    when (settings.theme) {
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
            val dark = settings.theme == ThemeMode.DARK || (settings.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
            LaunchedEffect(dark, settings.amoled, settings.dynamicColor) {
                splashScreen.setSplashScreenTheme(
                    when {
                        dark && settings.amoled -> R.style.Theme_WfMarket_SplashAmoled
                        settings.dynamicColor -> R.style.Theme_WfMarket
                        else -> R.style.Theme_WfMarket_SplashStatic
                    },
                )
            }
            WfMarketTheme(settings.theme, settings.dynamicColor, settings.amoled) {
                AppRoot(deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { deepLink = it }
    }
}
