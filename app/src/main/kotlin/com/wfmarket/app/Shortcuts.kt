package com.wfmarket.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

/**
 * The launcher icon's long-press menu. Published at runtime rather than declared in XML, because
 * static shortcuts name the package, and the debug build's package is different.
 */
object Shortcuts {
    /** Links within the app that have no page on the site. */
    const val SCHEME = "wfmarket"

    /** Which shortcut opened the app, so the launcher can learn which ones get used. */
    private const val EXTRA_ID = "com.wfmarket.app.SHORTCUT"

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "messages", "Messages", "Messages", "https://warframe.market/im/chats", R.drawable.ic_shortcut_messages),
            shortcut(context, "auction_search", "Search auctions", "Search auctions", "$SCHEME://auctions/search", R.drawable.ic_shortcut_search),
            shortcut(context, "new_auction", "New auction", "Create an auction", "$SCHEME://auctions/new", R.drawable.ic_shortcut_add),
            shortcut(context, "status_ingame", "In game", "Set your status to In game", "$SCHEME://status/ingame", R.drawable.ic_shortcut_ingame),
        ).mapIndexed { index, builder -> builder.setRank(index).build() }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, short: String, long: String, uri: String, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java).putExtra(EXTRA_ID, id))

    /** Tells the launcher a shortcut was used, which it weighs when ranking and suggesting them. */
    fun reportUsed(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }
}
