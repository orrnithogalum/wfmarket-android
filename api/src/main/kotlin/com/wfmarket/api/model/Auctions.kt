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

