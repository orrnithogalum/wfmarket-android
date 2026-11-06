package com.wfmarket.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.edit
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

/**
 * Owns the notification channels and everything the user sees in the shade. Every post checks
 * [canPost] first, which lint can't follow, hence the suppression.
 */
@SuppressLint("MissingPermission")
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
                NotificationChannel(CHANNEL_AUCTIONS, "Auctions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Bids and updates on your auctions and the ones you follow"
                },
                NotificationChannel(CHANNEL_ALERTS, "Auction alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New auctions matching your saved searches"
                },
                NotificationChannel(CHANNEL_ACCOUNT, "Account", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warnings and bans on your account"
                },
                NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "New versions of Warframe Market, as they download and once they're ready to install"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Silent notice Android requires while the app stays connected: to keep your status, or for instant delivery"
                    setShowBadge(false)
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

    /**
     * Whether notifications can show: the runtime permission on Android 13 and later, which doesn't
     * exist before it, and the app's notification setting everywhere.
     */
    private val canPost: Boolean
        get() = manager.areNotificationsEnabled()

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

    fun showAuction(title: String?, body: String?, auctionId: String?) {
        if (!canPost) return
        val notification = NotificationCompat.Builder(context, CHANNEL_AUCTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title ?: "Auction update")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(deepLink(auctionId?.let { "/auction/$it" } ?: "/auctions", (auctionId ?: "auctions").hashCode()))
            .build()
        runCatching { manager.notify(auctionId ?: "auctions", ID_AUCTION, notification) }
    }

    fun showAccountNotice(kind: String, message: String?) {
        if (!canPost) return
        val title = when (kind) {
            "banned" -> "Your account was banned"
            "banLifted" -> "Your ban was lifted"
            "warned" -> "You received a warning"
            "warningLifted" -> "Your warning was lifted"
            else -> "Account update"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ACCOUNT)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(deepLink("/settings", ID_ACCOUNT))
            .build()
        runCatching { manager.notify(ID_ACCOUNT, notification) }
    }

    /** One line per auction: what it is, what it costs, and whether the seller can trade now. */
    private fun describe(auction: Auction): String {
        val item = auction.item
        val name = when (item.type) {
            AuctionType.RIVEN -> listOfNotNull(item.weaponUrlName.humanize(), item.name?.replaceFirstChar { it.uppercase() }).joinToString(" ")
            else -> listOfNotNull(item.weaponUrlName.humanize(), item.element?.humanize(), item.damage?.let { "$it%" }).joinToString(" ")
        }
        val price = when {
            auction.isDirectSell -> "${auction.buyoutPrice ?: auction.startingPrice}p"
            auction.topBid != null -> "bid ${auction.topBid}p"
            else -> "from ${auction.startingPrice}p"
        }
        return "$name · $price · ${UserStatus.of(auction.owner.status).label}"
    }

    private fun Auction.price() = if (isDirectSell) buyoutPrice ?: startingPrice else topBid ?: startingPrice

    fun showAlertMatches(alert: AuctionAlert, fresh: List<Auction>) {
        if (!canPost) return
        val sorted = fresh.sortedBy { it.price() }
        val single = sorted.singleOrNull()
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
        if (single != null) {
            builder.setContentTitle("New: ${alert.title}")
                .setContentText(describe(single))
                .setContentIntent(deepLink("/auction/${single.id}", single.id.hashCode()))
        } else {
            val style = NotificationCompat.InboxStyle().setSummaryText("From ${sorted.first().price()}p")
            sorted.take(5).forEach { style.addLine(describe(it)) }
            builder.setContentTitle("${sorted.size} new: ${alert.title}")
                .setContentText(describe(sorted.first()))
                .setStyle(style)
                .setNumber(sorted.size)
                .setContentIntent(openUrl(alert.search.toUrl(), alert.id.hashCode()))
        }
        runCatching { manager.notify("alert:${alert.id}", ID_ALERT, builder.build()) }
    }

    fun showFollowedBid(auction: FollowedAuction, topBid: Int) {
        if (!canPost) return
        val notification = NotificationCompat.Builder(context, CHANNEL_AUCTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("New bid on ${auction.title}")
            .setContentText("The top bid is now ${topBid}p")
            .setAutoCancel(true)
            .setContentIntent(deepLink("/auction/${auction.id}", auction.id.hashCode()))
            .build()
        runCatching { manager.notify("follow:${auction.id}", ID_AUCTION, notification) }
    }

    fun showFollowedEnded(auction: FollowedAuction, removed: Boolean) {
        if (!canPost) return
        val notification = NotificationCompat.Builder(context, CHANNEL_AUCTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("${auction.title} has ended")
            .setContentText(
                when {
                    removed -> "The seller removed it. You're no longer following it."
                    auction.topBid != null -> "The last top bid was ${auction.topBid}p. You're no longer following it."
                    else -> "It closed without bids. You're no longer following it."
                },
            )
            .setAutoCancel(true)
            .setContentIntent(deepLink(if (removed) "/auctions" else "/auction/${auction.id}", auction.id.hashCode()))
            .build()
        runCatching { manager.notify("follow:${auction.id}", ID_AUCTION, notification) }
    }

    /**
     * The notice Android requires while the connection stays open. When it's open to keep a status,
     * it shows the status and offers the other one, so it's useful rather than just in the way.
     */
    fun serviceNotification(status: String?, until: Long?, instant: Boolean): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (status == null) {
            return builder.setContentTitle("Instant message delivery is on")
                .setContentIntent(deepLink("/im/chats", ID_SERVICE))
                .build()
        }
        val untilText = until?.let {
            "Until " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(it))
        } ?: "Until you change it"
        val other = if (status == UserStatus.INGAME.wire) UserStatus.ONLINE else UserStatus.INGAME
        return builder.setContentTitle("Status: ${UserStatus.of(status).label}")
            .setContentText(if (instant) "$untilText · Messages arrive instantly" else untilText)
            .setContentIntent(openUrl("wfmarket://status", ID_SERVICE))
            .addAction(statusAction(other.wire, other.label))
            .addAction(statusAction(StatusActionReceiver.OFFLINE, "Go offline"))
            .build()
    }

    private fun statusAction(status: String, label: String): NotificationCompat.Action {
        val intent = Intent(context, StatusActionReceiver::class.java)
            .setAction(StatusActionReceiver.ACTION_SET_STATUS)
            .putExtra(StatusActionReceiver.EXTRA_STATUS, status)
        val pending = PendingIntent.getBroadcast(context, status.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_notification, label, pending).build()
    }

    fun updateServiceNotification(notification: android.app.Notification) {
        if (canPost) runCatching { manager.notify(ID_SERVICE, notification) }
    }

    /** A download running with the app closed, as a bar in the shade. */
    fun showUpdateProgress(version: String, bytes: Long, total: Long) {
        if (!canPost) return
        val percent = if (total > 0) (bytes * 100 / total).toInt() else 0
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Warframe Market $version")
            .setContentText("$percent%")
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openUrl(UPDATES_LINK, ID_UPDATE))
            .build()
        runCatching { manager.notify(ID_UPDATE, notification) }
    }

    fun cancelUpdateProgress() = manager.cancel(ID_UPDATE)

    /** A new version downloaded with the app closed. Install runs it straight from the shade. */
    fun showUpdateReady(version: String) {
        if (!canPost) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Warframe Market $version is ready to install")
            .setContentText("Tap to see what's new")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(openUrl(UPDATES_LINK, ID_UPDATE))
            .addAction(R.drawable.ic_stat_notification, "Install", openUrl(INSTALL_LINK, ID_UPDATE + 1))
            .build()
        runCatching { manager.notify(ID_UPDATE, notification) }
    }

    /** Android closes the app to update it, so this says it's done and opens the new version. */
    fun showUpdated(version: String) {
        if (!canPost) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Updated to Warframe Market $version")
            .setContentText("Tap to open it")
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60 * 1000L)
            .setContentIntent(PendingIntent.getActivity(context, ID_UPDATE + 2, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        runCatching { manager.notify(ID_UPDATE, notification) }
    }

    fun cancelChat(chatId: String) = manager.cancel(chatTag(chatId), ID_CHAT)

    fun cancelAllChats() {
        runCatching { manager.activeNotifications.filter { it.id == ID_CHAT }.forEach { manager.cancel(it.tag, it.id) } }
        delivered.edit { clear() }
    }

    /** Dedupes the socket and poll paths, and survives restarts so old messages never re-alert. */
    fun markDelivered(chatId: String, date: String?) {
        date?.let { delivered.edit { putString(chatId, it) } }
    }

    fun isNewerThanDelivered(chatId: String, date: String?): Boolean {
        date ?: return false
        val last = delivered.getString(chatId, null) ?: return true
        return normalizeDate(date) > normalizeDate(last)
    }

    /** `+00:00` and `Z` both occur, and `Instant.parse` and string comparison need one form. */
    private fun normalizeDate(date: String) = date.replace("+00:00", "Z")

    private fun chatTag(chatId: String) = "chat:$chatId"

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_AUCTIONS = "auctions"
        const val CHANNEL_ALERTS = "auction_alerts"
        const val CHANNEL_ACCOUNT = "account"
        const val CHANNEL_SERVICE = "service"
        const val CHANNEL_UPDATES = "updates"
        const val GROUP_MESSAGES = "messages"
        const val ID_CHAT = 10
        const val ID_AUCTION = 11
        const val ID_ACCOUNT = 12
        const val ID_SERVICE = 13
        const val ID_ALERT = 14
        const val ID_UPDATE = 15

        const val UPDATES_LINK = "wfmarket://settings"
        const val INSTALL_LINK = "wfmarket://update/install"
    }
}
