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

