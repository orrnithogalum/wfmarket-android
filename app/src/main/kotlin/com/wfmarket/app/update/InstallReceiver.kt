package com.wfmarket.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import com.wfmarket.app.WfMarketApp

/**
 * Hears back from Android's package installer. When it needs the user's say-so, this shows its
 * confirmation, which can open because the app is on screen as the user taps Install.
 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val updater = (context.applicationContext as WfMarketApp).container.updater
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
            runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Log.w("InstallReceiver", "Couldn't show the install confirmation", it) }
            return
        }
        updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }

    companion object {
        const val ACTION_STATUS = "com.wfmarket.app.INSTALL_STATUS"
    }
}
