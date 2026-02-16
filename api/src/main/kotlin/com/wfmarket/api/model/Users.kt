package com.wfmarket.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** A v2 user. Public responses fill the common fields; `/v2/me` also fills the private ones. */
@Serializable
data class User(
    val id: String = "",
    val slug: String = "",
    val ingameName: String = "",
    val avatar: String? = null,
    val background: String? = null,
    val about: String? = null,
    val reputation: Int = 0,
    val masteryRank: Int? = null,
    val platform: String = "pc",
    val crossplay: Boolean = false,
    val locale: String = "en",
    val status: String = "offline",
    val lastSeen: String? = null,
    val role: String? = null,
    val tier: String? = null,
    val banned: Boolean = false,
    val banUntil: String? = null,
    val banMessage: String? = null,
    val warned: Boolean = false,
    val warnMessage: String? = null,
    val achievementShowcase: List<Achievement> = emptyList(),
    // Private fields, present on /v2/me only.
    val aboutRaw: String? = null,
    val verification: Boolean = false,
    val checkCode: String? = null,
    val credits: Int? = null,
    val subscription: Boolean = false,
    val reviewsLeft: Int? = null,
    val unreadMessages: Int = 0,
    val ignoreList: List<String> = emptyList(),
    val deleteInProgress: Boolean = false,
    val deleteAt: String? = null,
    val linkedAccounts: Map<String, JsonElement> = emptyMap(),
    val hasEmail: Boolean = false,
    val createdAt: String? = null,
) {
    /** The about text as typed, for editing. The raw field may arrive HTML-escaped. */
    val aboutText: String? get() = aboutRaw?.let(::decodeHtmlEntities)
}

