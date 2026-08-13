package com.wfmarket.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.wfmarket.app.WfMarketApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * warframe.market's push channel is Web Push, which couldn't reach a closed app without a relay
 * server to Firebase. And the site shows you offline as soon as your connection closes. So keeping
 * a status, or getting messages in real time, with the app closed means keeping the same websocket
 * open that the site uses.
 *
 * An app with unrestricted battery use may keep a plain background service running, so then this
 * runs as one, with nothing on screen. Without that exemption Android stops background services
 * within about a minute, so it runs in the foreground instead, which takes a notification.
 *
 * It uses the `specialUse` type because `dataSync` is limited to six hours a day from Android 15.
 */
class RealtimeService : Service() {
    private val container get() = (application as WfMarketApp).container
    private var updates: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** Whether this run has a notification, which only the foreground needs. */
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        if (isExempt(this)) return
        foreground = true
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifier.ID_SERVICE,
                notification(container.serviceNotice.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }.onFailure {
            // Android refuses background starts in some states. The worker will retry.
            Log.w(TAG, "Couldn't enter the foreground", it)
            stopSelf()
            return
        }
        // The notice shows the status, so it follows every change to it.
        updates = container.scope.launch {
            container.serviceNotice.drop(1).collect { notice ->
                if (foreground && notice != null) container.notifier.updateServiceNotification(notification(notice))
            }
        }
    }

    private fun notification(notice: ServiceNotice?) =
        container.notifier.serviceNotification(notice?.status, notice?.until, notice?.instant ?: true)


