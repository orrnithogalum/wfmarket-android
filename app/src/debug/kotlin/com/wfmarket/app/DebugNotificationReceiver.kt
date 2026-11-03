package com.wfmarket.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wfmarket.api.model.Chat
import com.wfmarket.api.model.ChatMessage
import com.wfmarket.app.notifications.RealtimeService
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Exercises the notification paths without another player sending a real message:
 * `adb shell am broadcast -a com.wfmarket.app.DEBUG_NOTIFY -p com.wfmarket.app.debug --es text hello`
 *
 * With `--es inbox '<chats json>'` it runs what the background inbox check does with a server response.
 * With `--ez alerts true` it runs the auction alert check now instead of at the worker's next run.
 * With `--ez sample true` it puts a made-up conversation, `sample`, in the inbox, for trying out how
 * runs of messages group. It's never sent anywhere. Open it with
 * `adb shell am start -a android.intent.action.VIEW -d https://warframe.market/im/chats/sample -p com.wfmarket.app.debug`.
 * With `--es update_feed <url>` the updater reads that URL instead of GitHub's latest release and
 * checks it now, so a stand-in release, such as one served from the computer over `adb reverse`,
 * can be downloaded and installed over this build. It lasts until the app's process ends.
 */
class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WfMarketApp).container
        intent.getStringExtra("inbox")?.let { inbox ->
            container.chats.announce(Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Chat.serializer()), inbox))
            return
        }
        intent.getStringExtra("update_feed")?.let { feed ->
            container.updater.testFeed = feed
            container.updater.checkNow()
            return
        }
        if (intent.getBooleanExtra("alerts", false)) {
            val pending = goAsync()
            container.scope.launch {
                try { container.alerts.check() } finally { pending.finish() }
            }
            return
        }
        if (intent.getBooleanExtra("sample", false)) {
            showSample(container)
            return
        }
        if (intent.getBooleanExtra("service", false)) RealtimeService.start(context)
        val message = ChatMessage(
            id = "debug",
            chatId = "debugchat",
            message = intent.getStringExtra("text") ?: "WTS Mesa Prime Set 50p?",
            messageFrom = "debug-user",
            sendDate = java.time.Instant.now().toString(),
        )
        container.notifier.showMessage(Chat(id = "debugchat", chatName = "TestTenno"), message, listOf(message))
    }
}

/** Runs from both sides, a run broken by an hour's pause, and a long message, two hours old to now. */
private fun showSample(container: com.wfmarket.app.data.AppContainer) {
    val me = container.account.me.value?.id.orEmpty()
    val them = "sample-them"
    val start = java.time.Instant.now().minus(java.time.Duration.ofMinutes(150))
    var n = 0
    fun at(minutes: Long, from: String, text: String) = ChatMessage(
        id = "sample-${n++}",
        chatId = "sample",
        message = text,
        messageFrom = from,
        sendDate = start.plus(java.time.Duration.ofMinutes(minutes)).toString(),
    )
    val messages = listOf(
        at(0, them, "Hey, is the Torid riven still for sale?"),
        at(0, them, "The crit chance roll looks great"),
        at(2, them, "Would you take 300?"),
        at(4, me, "Hi! It's 350, but I can do 330"),
        at(5, me, "I'm in game for the next hour or so"),
        at(80, them, "Sorry, got pulled away. Still around?"),
        at(81, them, "330 works for me"),
        at(82, them, "Inviting you now, I'm the one with the Citrine Prime fashion that's way too shiny, you can't miss it in the dojo"),
        at(140, me, "Got it, thanks for the trade!"),
    )
    container.chats.showSample(com.wfmarket.api.model.Chat(id = "sample", chatName = "SampleTenno"), messages)
}
