package com.wfmarket.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.wfmarket.app.WfMarketApp
import kotlinx.coroutines.launch

/** Starting the app's process is enough: the container resumes whatever delivery is switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

