package com.wfmarket.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Versions(val collections: Map<String, String> = emptyMap())

@Serializable
data class Item(
    val id: String,
    val slug: String,
    val gameRef: String? = null,
    val tags: List<String> = emptyList(),
    val i18n: Map<String, I18n> = emptyMap(),
    val setRoot: Boolean = false,
    val setParts: List<String> = emptyList(),
    val quantityInSet: Int? = null,
    val rarity: String? = null,
    val bulkTradable: Boolean = false,
    val subtypes: List<String> = emptyList(),
    val maxRank: Int? = null,
    val maxCharges: Int? = null,
    val maxAmberStars: Int? = null,
    val maxCyanStars: Int? = null,
    val baseEndo: Int? = null,
    val endoMultiplier: Double? = null,
    val ducats: Int? = null,
    val vosfor: Int? = null,
    val reqMasteryRank: Int? = null,
    val vaulted: Boolean? = null,
    val tradingTax: Int? = null,
    val tradable: Boolean? = null,
)

