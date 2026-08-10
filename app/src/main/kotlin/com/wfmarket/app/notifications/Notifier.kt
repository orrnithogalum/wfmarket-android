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

    /** The background poll sees only a chat's latest message, so it shows that. */
    fun showChatSummary(chat: Chat, last: ChatMessage?) {
        if (!canPost) return
        val name = chat.chatName.ifBlank { "New message" }
        val sender = Person.Builder().setName(name).build()
        val text = last?.text ?: "${chat.unreadCount} unread messages"
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage(text, System.currentTimeMillis(), sender)
        post(chat.id, style, name, text)
    }

    private fun post(chatId: String, style: NotificationCompat.MessagingStyle, title: String, body: String) {
        val replyIntent = Intent(context, ChatActionReceiver::class.java)
            .setAction(ChatActionReceiver.ACTION_REPLY)
            .putExtra(ChatActionReceiver.EXTRA_CHAT, chatId)
        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_notification,
            "Reply",
            PendingIntent.getBroadcast(
                context,
                chatId.hashCode(),
                replyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).addRemoteInput(RemoteInput.Builder(ChatActionReceiver.KEY_REPLY).setLabel("Reply").build())
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        val readIntent = Intent(context, ChatActionReceiver::class.java)
            .setAction(ChatActionReceiver.ACTION_MARK_READ)
            .putExtra(ChatActionReceiver.EXTRA_CHAT, chatId)
        val read = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_notification,
            "Mark as read",
            PendingIntent.getBroadcast(
                context,
                chatId.hashCode() + 1,
                readIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(deepLink("/im/chats/$chatId", chatId.hashCode() + 2))
            .addAction(reply)
            .addAction(read)
            .setGroup(GROUP_MESSAGES)
            .build()
        runCatching { manager.notify(chatTag(chatId), ID_CHAT, notification) }
    }

    /** Updates the conversation with the user's own reply, confirming the send. */
    fun showReplySent(chatId: String, chatName: String?, text: String) {
        if (!canPost) return
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me).addMessage(text, System.currentTimeMillis(), me)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(chatName)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .setContentIntent(deepLink("/im/chats/$chatId", chatId.hashCode() + 2))
            .build()
        runCatching { manager.notify(chatTag(chatId), ID_CHAT, notification) }
    }

    fun cancelChat(chatId: String) = manager.cancel(chatTag(chatId), ID_CHAT)

    fun cancelAllChats() {
        runCatching { manager.activeNotifications.filter { it.id == ID_CHAT }.forEach { manager.cancel(it.tag, it.id) } }
        delivered.edit().clear().apply()
    }

    /** Dedupes the socket and poll paths, and survives restarts so old messages never re-alert. */
    fun markDelivered(chatId: String, date: String?) {
        date?.let { delivered.edit().putString(chatId, it).apply() }
    }

    fun isNewerThanDelivered(chatId: String, date: String?): Boolean {
        date ?: return false
        val last = delivered.getString(chatId, null) ?: return true
        return normalizeDate(date) > normalizeDate(last)
    }

    /** `+00:00` and `Z` both occur, and `Instant.parse` and string comparison need one form. */
    private fun normalizeDate(date: String) = date.replace("+00:00", "Z")

