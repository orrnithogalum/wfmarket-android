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

/** Handles the notification's Reply and Mark as read actions without opening the app. */
class ChatActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as WfMarketApp).container
        val chatId = intent.getStringExtra(EXTRA_CHAT) ?: return
        val pending = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            val name = container.chats.chats.value.firstOrNull { it.id == chatId }?.chatName
                            val sent = runCatching { container.chats.send(chatId, text) }.isSuccess
                            if (sent) container.notifier.showReplySent(chatId, name, text)
                        }
                    }
                    ACTION_MARK_READ -> container.chats.markChatRead(chatId)
                }
            } finally {
                pending.finish()
            }
        }
    }


