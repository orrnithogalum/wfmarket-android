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

