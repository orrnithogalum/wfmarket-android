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

    private fun openUrl(url: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val canPost: Boolean
        get() = ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** A conversation-style notification with inline reply, used for messages seen live. */
    fun showMessage(chat: Chat?, message: ChatMessage, recent: List<ChatMessage>) {
        if (!canPost) return
        val name = chat?.chatName?.ifBlank { null } ?: "New message"
        val sender = Person.Builder().setName(name).setKey(message.messageFrom).build()
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(null)
        val history = recent.ifEmpty { listOf(message) }
        history.forEach { entry ->
            val time = entry.sendDate?.let { runCatching { java.time.Instant.parse(normalizeDate(it)).toEpochMilli() }.getOrNull() }
                ?: System.currentTimeMillis()
            val from = if (entry.messageFrom == message.messageFrom) sender else null
            style.addMessage(entry.text, time, from ?: me)
        }
        post(message.chatId, style, name, message.text)
    }

