package com.wfmarket.api.model

import kotlinx.serialization.Serializable

/**
 * One line of Warframe's weekly riven trade figures: the platinum paid for one weapon's rivens in
 * trades between players. Veiled rivens have no [compatibility]. [pop] is how often it traded,
 * scaled so the week's most traded riven is 100.
 */
@Serializable
data class RivenTrade(
    val itemType: String = "",
    val compatibility: String? = null,
    val rerolled: Boolean = false,
    val avg: Double = 0.0,
    val stddev: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val pop: Double = 0.0,
    val median: Double = 0.0,
) {
    val rivenClass: RivenClass? get() = RivenClass.ofModName(itemType)
}

