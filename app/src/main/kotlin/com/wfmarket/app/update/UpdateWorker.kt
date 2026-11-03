package com.wfmarket.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wfmarket.app.WfMarketApp
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Looks for a new version about twice a day with the app closed, and downloads it if automatic
 * downloads are on, so it's ready to install with a tap on the notification.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as WfMarketApp).container
        val updater = container.updater
        if (!updater.supported || !container.settings.current.checkUpdates) return Result.success()
        return try {
            updater.check()
            // A download the check started is still going; the work waits for it, so Android lets it finish.
            if (updater.state.value is UpdateState.Downloading) updater.download()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
