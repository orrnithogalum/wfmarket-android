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

@Serializable
data class ItemSet(val id: String? = null, val items: List<Item> = emptyList())

@Serializable
data class RivenWeapon(
    val id: String,
    val slug: String,
    val group: String? = null,
    val rivenType: String? = null,
    val disposition: Double? = null,
    val reqMasteryRank: Int? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

@Serializable
data class RivenAttribute(
    val id: String,
    val slug: String,
    val group: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val exclusiveTo: List<String> = emptyList(),
    val positiveIsNegative: Boolean = false,
    val positiveOnly: Boolean = false,
    val negativeOnly: Boolean = false,
    val unit: String? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

/** Kuva lich and Sister of Parvos weapons share one shape. */
@Serializable
data class NemesisWeapon(
    val id: String,
    val slug: String,
    val reqMasteryRank: Int? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

@Serializable
data class Ephemera(
    val id: String,
    val slug: String,
    val element: String? = null,
    val animation: String? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

@Serializable
data class Quirk(
    val id: String,
    val slug: String,
    val group: String? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

@Serializable
data class Location(
    val id: String,
    val slug: String,
    val faction: String? = null,
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val i18n: Map<String, I18n> = emptyMap(),
)

/** Missions and NPCs share one shape. */
@Serializable
data class NamedEntity(
    val id: String,
    val slug: String,
    val i18n: Map<String, I18n> = emptyMap(),
)

