package com.wfmarket.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One bucket of `/v1/items/{slug}/statistics`. Closed-trade buckets carry open/close prices and a
 * Donchian channel; live-order buckets carry [orderType] instead.
 */
@Serializable
data class StatPoint(
    val datetime: String,
    val volume: Int = 0,
    @SerialName("min_price") val minPrice: Double = 0.0,
    @SerialName("max_price") val maxPrice: Double = 0.0,
    @SerialName("open_price") val openPrice: Double? = null,
    @SerialName("closed_price") val closedPrice: Double? = null,
    @SerialName("avg_price") val avgPrice: Double = 0.0,
    @SerialName("wa_price") val waPrice: Double = 0.0,
    val median: Double = 0.0,
    @SerialName("moving_avg") val movingAvg: Double? = null,
    @SerialName("donch_top") val donchTop: Double? = null,
    @SerialName("donch_bot") val donchBot: Double? = null,
    @SerialName("order_type") val orderType: String? = null,
    @SerialName("mod_rank") val modRank: Int? = null,
    val subtype: String? = null,
    @SerialName("amber_stars") val amberStars: Int? = null,
    @SerialName("cyan_stars") val cyanStars: Int? = null,
    val charges: Int? = null,
)

@Serializable
data class StatRanges(
    @SerialName("48hours") val hours48: List<StatPoint> = emptyList(),
    @SerialName("90days") val days90: List<StatPoint> = emptyList(),
)

@Serializable
data class ItemStatistics(
    @SerialName("statistics_closed") val closed: StatRanges = StatRanges(),
    @SerialName("statistics_live") val live: StatRanges = StatRanges(),
)

@Serializable
data class DucatEntry(
    val datetime: String? = null,
    val item: String,
    val ducats: Int = 0,
    val volume: Int = 0,
    val median: Double = 0.0,
    @SerialName("wa_price") val waPrice: Double = 0.0,
    @SerialName("plat_worth") val platWorth: Double = 0.0,
    @SerialName("ducats_per_platinum") val ducatsPerPlatinum: Double = 0.0,
    @SerialName("ducats_per_platinum_wa") val ducatsPerPlatinumWa: Double = 0.0,
    @SerialName("position_change_day") val positionChangeDay: Int = 0,
    @SerialName("position_change_week") val positionChangeWeek: Int = 0,
    @SerialName("position_change_month") val positionChangeMonth: Int = 0,
)

