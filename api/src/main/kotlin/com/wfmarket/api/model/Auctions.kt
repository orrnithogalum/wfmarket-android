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

    val hasPriceRange: Boolean get() = priceMin != null || priceMax != null

    fun matchesPrice(auction: Auction): Boolean {
        val price = auction.currentPrice
        return (priceMin == null || price >= priceMin) && (priceMax == null || price <= priceMax)
    }

    /** Everything the app filters on itself, after the site has searched. */
    fun matches(auction: Auction): Boolean = matchesSeller(auction.owner.status) && matchesPrice(auction)

    /** The same search as a warframe.market link, which the app also opens. */
    fun toUrl(): String = buildString {
        append("https://warframe.market/auctions/search?")
        val query = toQuery() + buildMap {
            if (sellerStatuses.isNotEmpty()) put(SELLER_STATUS, sellerStatuses.sorted().joinToString(","))
            priceMin?.let { put(PRICE_MIN, it.toString()) }
            priceMax?.let { put(PRICE_MAX, it.toString()) }
        }
        append(query.entries.joinToString("&") { (key, value) -> "$key=${java.net.URLEncoder.encode(value, "UTF-8")}" })
    }

    fun toQuery(): Map<String, String> = buildMap {
        put("type", type)
        put("sort_by", sortBy)
        weaponUrlName?.let { put("weapon_url_name", it) }
        buyoutPolicy?.let { put("buyout_policy", it) }
        if (type == AuctionType.RIVEN) {
            if (positiveStats.isNotEmpty()) put("positive_stats", positiveStats.joinToString(","))
            if (negativeStats.isNotEmpty()) put("negative_stats", negativeStats.joinToString(","))
            polarity?.let { put("polarity", it) }
            masteryRankMin?.let { put("mastery_rank_min", it.toString()) }
            masteryRankMax?.let { put("mastery_rank_max", it.toString()) }
            reRollsMin?.let { put("re_rolls_min", it.toString()) }
            reRollsMax?.let { put("re_rolls_max", it.toString()) }
            if (modRankMaxed) put("mod_rank", "maxed")
        } else {
            element?.let { put("element", it) }
            if (hasEphemera) put("has_ephemera", "true")
            quirk?.let { put("quirk", it) }
            damageMin?.let { put("damage_min", it.toString()) }
            damageMax?.let { put("damage_max", it.toString()) }
        }
    }

    companion object {
        /** This app's own parameters, which the site ignores. */
        private const val SELLER_STATUS = "seller_status"
        private const val PRICE_MIN = "price_min"
        private const val PRICE_MAX = "price_max"

        /** Reads a search from a link's query parameters, as written by [toUrl] or the site's search page. */
        fun fromQuery(params: Map<String, String>): AuctionSearch {
            fun list(key: String) = params[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            fun int(key: String) = params[key]?.toIntOrNull()
            fun text(key: String) = params[key]?.trim()?.takeIf { it.isNotEmpty() && it != "any" }
            val type = text("type") ?: AuctionType.RIVEN
            return AuctionSearch(
                type = type,
                weaponUrlName = text("weapon_url_name"),
                sortBy = text("sort_by") ?: "price_asc",
                buyoutPolicy = text("buyout_policy")?.takeIf { it == "direct" || it == "auction" },
                positiveStats = list("positive_stats").take(3),
                negativeStats = list("negative_stats").take(1),
                polarity = text("polarity"),
                masteryRankMin = int("mastery_rank_min"),
                masteryRankMax = int("mastery_rank_max"),
                reRollsMin = int("re_rolls_min"),
                reRollsMax = int("re_rolls_max"),
                modRankMaxed = params["mod_rank"] == "maxed",
                element = text("element"),
                hasEphemera = params["has_ephemera"] == "true",
                quirk = text("quirk"),
                damageMin = int("damage_min"),
                damageMax = int("damage_max"),
                sellerStatuses = list(SELLER_STATUS).map(::sellerStatus).toSet(),
                priceMin = int(PRICE_MIN)?.takeIf { it >= 0 },
                priceMax = int(PRICE_MAX)?.takeIf { it >= 0 },
            )
        }
    }
}

