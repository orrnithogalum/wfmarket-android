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

/** Fields of `PATCH /v2/me`. Null fields are left unchanged. */
@Serializable
data class UpdateMe(
    val about: String? = null,
    val platform: String? = null,
    val crossplay: Boolean? = null,
    val locale: String? = null,
)

/** The snake_case user embedded in v1 auction, chat and review responses. */
@Serializable
data class V1User(
    val id: String = "",
    val slug: String? = null,
    @SerialName("ingame_name") val ingameName: String? = null,
    val avatar: String? = null,
    val reputation: Int = 0,
    val platform: String? = null,
    val crossplay: Boolean = false,
    val locale: String? = null,
    val status: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
)

@Serializable
data class Review(
    val id: String = "",
    val text: String = "",
    @SerialName("user_from") val userFrom: V1User = V1User(),
    @SerialName("review_type") val reviewType: Int = 1,
    val date: String? = null,
    val hidden: Boolean = false,
)

@Serializable
data class ReviewsPage(
    val reviews: List<Review> = emptyList(),
    @SerialName("own_review") val ownReview: Review? = null,
    @SerialName("total_review_count") val totalReviewCount: Int? = null,
)

@Serializable
data class ClosedOrder(
    val id: String = "",
    val quantity: Int = 1,
    val platinum: Double = 0.0,
    @SerialName("order_type") val orderType: String = OrderType.SELL,
    val item: ClosedOrderItem = ClosedOrderItem(),
    @SerialName("mod_rank") val modRank: Int? = null,
    val subtype: String? = null,
    @SerialName("amber_stars") val amberStars: Int? = null,
    @SerialName("cyan_stars") val cyanStars: Int? = null,
    @SerialName("closed_date") val closedDate: String? = null,
)

@Serializable
data class ClosedOrderItem(
    val id: String = "",
    @SerialName("url_name") val urlName: String = "",
    val thumb: String? = null,
    val icon: String? = null,
)
