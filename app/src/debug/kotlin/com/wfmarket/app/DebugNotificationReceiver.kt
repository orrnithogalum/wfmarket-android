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
 */
class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WfMarketApp).container
        intent.getStringExtra("inbox")?.let { inbox ->
            container.chats.announce(Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(Chat.serializer()), inbox))
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

