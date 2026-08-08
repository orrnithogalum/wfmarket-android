package com.wfmarket.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.AuctionType
import com.wfmarket.api.model.Chat
import com.wfmarket.api.model.ChatMessage
import com.wfmarket.api.model.UserStatus
import com.wfmarket.app.MainActivity
import com.wfmarket.app.R
import com.wfmarket.app.data.AuctionAlert
import com.wfmarket.app.data.FollowedAuction
import com.wfmarket.app.data.humanize

/** Owns the notification channels and everything the user sees in the shade. */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val delivered = context.getSharedPreferences("delivered_chats", Context.MODE_PRIVATE)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New trade messages from other players"
                },
            ),
        )
    }

    private fun deepLink(path: String, requestCode: Int): PendingIntent = openUrl("https://warframe.market$path", requestCode)

