package com.wfmarket.app.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wfmarket.app.WfMarketApp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Works with the app closed, with nothing on screen: it checks the inbox and notifies about anything
 * new, and runs auction alerts and followed auctions. Each run queues the next, because periodic
 * work can't run more often than every 15 minutes. It also brings back the background connection if
 * the system killed it.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as WfMarketApp).container
        val settings = container.settings.current
        val inbox = container.account.isSignedIn.value && settings.backgroundMessages
        val auctions = container.alerts.state.value.hasWork
        if (!inbox && !auctions && !container.serviceWanted.value) return Result.success()
        if (container.serviceWanted.value && !container.appForeground.value) RealtimeService.start(applicationContext)
        // The next run tries again whatever fails. Failing here would end the chain.
        if (inbox) {
            try {
                container.chats.syncUnread()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check the inbox", e)
            }
        }
        if (auctions) {
            try {
                container.alerts.check()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check auction alerts", e)
            }
        }
        enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

