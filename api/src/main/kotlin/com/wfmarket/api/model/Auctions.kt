package com.wfmarket.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object AuctionType {
    const val RIVEN = "riven"
    const val LICH = "lich"
    const val SISTER = "sister"
}

@Serializable
data class RivenStat(
    @SerialName("url_name") val urlName: String,
    val value: Double = 0.0,
    val positive: Boolean = true,
)

@Serializable
data class AuctionItem(
    val type: String = AuctionType.RIVEN,
    @SerialName("weapon_url_name") val weaponUrlName: String = "",
    // Riven
    val name: String? = null,
    @SerialName("mod_rank") val modRank: Int? = null,
    @SerialName("mastery_level") val masteryLevel: Int? = null,
    val polarity: String? = null,
    @SerialName("re_rolls") val reRolls: Int? = null,
    val attributes: List<RivenStat> = emptyList(),
    // Lich / sister
    val element: String? = null,
    @SerialName("having_ephemera") val havingEphemera: Boolean? = null,
    val damage: Int? = null,
    val quirk: String? = null,
)

@Serializable
data class Auction(
    val id: String = "",
    val visible: Boolean = true,
    val closed: Boolean = false,
    val private: Boolean = false,
    @SerialName("is_direct_sell") val isDirectSell: Boolean = false,
    @SerialName("starting_price") val startingPrice: Int = 0,
    @SerialName("buyout_price") val buyoutPrice: Int? = null,
    @SerialName("top_bid") val topBid: Int? = null,
    @SerialName("minimal_reputation") val minimalReputation: Int = 0,
    val note: String = "",
    @SerialName("note_raw") val noteRaw: String = "",
    val platform: String? = null,
    val crossplay: Boolean = false,
    val created: String? = null,
    val updated: String? = null,
    @SerialName("is_marked_for") val isMarkedFor: String? = null,
    @SerialName("marked_operation_at") val markedOperationAt: String? = null,
    @Serializable(with = V1UserOrIdSerializer::class) val owner: V1User = V1User(),
    @Serializable(with = V1UserOrIdSerializer::class) val winner: V1User? = null,
    val item: AuctionItem = AuctionItem(),
) {
    /** What the seller typed. Like chat messages, the raw note arrives HTML-escaped (`'` as `&#x27;`). */
    val noteText: String get() = decodeHtmlEntities(noteRaw)

    /** What it goes for now: the buyout of a direct sale, or an auction's top bid, else its start. */
    val currentPrice: Int get() = if (isDirectSell) buyoutPrice ?: startingPrice else topBid ?: startingPrice
}

@Serializable
data class Bid(
    val id: String = "",
    val auction: String = "",
    val value: Int = 0,
    val created: String? = null,
    val updated: String? = null,
    @Serializable(with = V1UserOrIdSerializer::class) val user: V1User = V1User(),
)

/** Body of `POST /v1/auctions/create`. */
@Serializable
data class CreateAuction(
    @SerialName("starting_price") val startingPrice: Int,
    @SerialName("buyout_price") val buyoutPrice: Int? = null,
    @SerialName("minimal_reputation") val minimalReputation: Int = 0,
    val visible: Boolean = true,
    val note: String = "",
    val item: AuctionItem,
)

/** Body of `PUT /v1/auctions/entry/{id}`. */
@Serializable
data class UpdateAuction(
    @SerialName("starting_price") val startingPrice: Int,
    @SerialName("buyout_price") val buyoutPrice: Int? = null,
    @SerialName("minimal_reputation") val minimalReputation: Int = 0,
    val visible: Boolean = true,
    val note: String = "",
)

/** The filters of the auction search page. Blank/null filters are omitted from the query. */
@Serializable
data class AuctionSearch(
    val type: String = AuctionType.RIVEN,
    val weaponUrlName: String? = null,
    val sortBy: String = "price_asc",
    val buyoutPolicy: String? = null,
    // Riven
    val positiveStats: List<String> = emptyList(),
    val negativeStats: List<String> = emptyList(),
    val polarity: String? = null,
    val masteryRankMin: Int? = null,
    val masteryRankMax: Int? = null,
    val reRollsMin: Int? = null,
    val reRollsMax: Int? = null,
    val modRankMaxed: Boolean = false,
    // Lich / sister
    val element: String? = null,
    val hasEphemera: Boolean = false,
    val quirk: String? = null,
    val damageMin: Int? = null,
    val damageMax: Int? = null,
    /**
     * Seller presence, as [UserStatus] wire values, where `offline` also covers invisible sellers.
     * Empty means anyone. The site has no such filter, so it's applied to the results here.
     */
    val sellerStatuses: Set<String> = emptySet(),
    /**
     * The range of [Auction.currentPrice], in platinum, either end open. The site has no price
     * filter either, so this too is applied to the results here.
     */
    val priceMin: Int? = null,
    val priceMax: Int? = null,
) {
    /** The site refuses open searches: a riven needs a weapon or a stat, a nemesis a weapon or element. */
    val canSearch: Boolean
        get() = when (type) {
            AuctionType.RIVEN -> weaponUrlName != null || positiveStats.isNotEmpty()
            else -> weaponUrlName != null || element != null
        }

    fun matchesSeller(status: String?): Boolean =
        sellerStatuses.isEmpty() || sellerStatus(status) in sellerStatuses


