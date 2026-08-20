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


