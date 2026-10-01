package com.wfmarket.api.model

import kotlin.math.abs

/** The riven a weapon takes. Each kind rolls its stats from its own table. */
enum class RivenClass(val label: String) {
    RIFLE("Rifle"), SHOTGUN("Shotgun"), PISTOL("Pistol"), ARCHGUN("Archgun"), MELEE("Melee"), KITGUN("Kitgun"), ZAW("Zaw");

    companion object {
        /** Warframe's own name for the mod, as in `"Shotgun Riven Mod"`. */
        fun ofModName(name: String): RivenClass? = entries.firstOrNull { name.startsWith(it.label, ignoreCase = true) }

        /**
         * The site's guess, for weapons Warframe's trade data hasn't named. Its `rivenType` is wrong
         * for secondary shotguns like the Bronco, which take pistol rivens, so secondaries always do.
         */
        fun of(weapon: RivenWeapon): RivenClass? = when {
            weapon.group == "archgun" -> ARCHGUN
            weapon.group == "secondary" -> PISTOL
            else -> when (weapon.rivenType) {
                "rifle" -> RIFLE
                "shotgun" -> SHOTGUN
                "pistol" -> PISTOL
                "melee" -> MELEE
                "kitgun" -> KITGUN
                "zaw" -> ZAW
                else -> null
            }
        }
    }
}

/** How a stat's rolled number becomes the number shown. */
enum class RollScale {
    /** Shown in hundredths, as percentages are. Parry angle is shown this way too, without the sign. */
    PERCENT,

    /** Faction damage, shown as `x1.45`: one plus the roll. */
    MULTIPLIER,

    /** Shown as rolled, like Punch Through's metres or Initial Combo. */
    PLAIN,
}

/** A stat's base value in each riven table, or null where that riven can't roll it. */
class RivenAttributeScale(
    val scale: RollScale,
    rifle: Double?, shotgun: Double?, pistol: Double?, archgun: Double?, melee: Double?, kitgun: Double?, zaw: Double?,
) {
    private val values = arrayOf(rifle, shotgun, pistol, archgun, melee, kitgun, zaw)

    fun base(rivenClass: RivenClass): Double? = values[rivenClass.ordinal]
}

/** A stat's grade: how far its roll landed from the middle of the ±10% it can roll, in two-point bands. */
enum class RivenGrade(val label: String, val floor: Double) {
    S("S", 9.5), A_PLUS("A+", 7.5), A("A", 5.5), A_MINUS("A-", 3.5), B_PLUS("B+", 1.5), B("B", -1.5),
    B_MINUS("B-", -3.5), C_PLUS("C+", -5.5), C("C", -7.5), C_MINUS("C-", -9.5), F("F", Double.NEGATIVE_INFINITY);

    val isGood: Boolean get() = this <= A_MINUS
    val isBad: Boolean get() = this >= C_PLUS

    companion object {
        fun of(deviation: Double): RivenGrade = entries.first { deviation >= it.floor }
    }
}

/**
 * One stat's roll. [deviation] is in percent from the middle of its range, where a smaller curse
 * counts as better. [worst] and [best] are the ends of the range, as the listing shows values.
 */
data class StatRoll(
    val grade: RivenGrade?,
    val deviation: Double,
    val worst: Double,
    val best: Double,
    /** Further than any roll can reach, so most likely mistyped. */
    val outOfRange: Boolean,
    /** Shown so rounded, as small stats are at a low rank, that the grade could be anything. */
    val tooRounded: Boolean,
) {
    val min: Double get() = minOf(worst, best)
    val max: Double get() = maxOf(worst, best)

    /** Where the value sits in its range, from 0 for the worst roll to 1 for the best. */
    val position: Double get() = ((deviation + 10) / 20).coerceIn(0.0, 1.0)
}

/** A riven's stats graded together, since how many there are decides how strong each one is. */
data class RivenRolls(
    /** Aligned with the riven's attributes. Null where a stat couldn't be graded at all. */
    val stats: List<StatRoll?>,
    /** The rank the values were read at. Sellers often enter a maxed riven's stats at rank 0. */
    val rank: Int,
    /**
     * Set when the values only make sense with the weapon's old disposition, from before Riven
     * Disposition was retired. The riven's stats in game are now these times [nowFactor].
     */
    val oldDisposition: Double?,
) {
    val nowFactor: Double? get() = oldDisposition?.let { RivenGrading.DISPOSITION / it }
}

/**
 * Riven stat grades, as Warframe's riven graders give them.
 *
 * A stat's value at rank `r` is `base × 10 × (r + 1) × disposition × layout × roll`, where the roll
 * is 0.9–1.1 and the layout multiplier depends on how many buffs and curses the riven has. The
 * disposition is the weapon's own, as the site lists it. The base values are Warframe's public
 * export (`ExportUpgrades`, the `Lotus…RandomModRare` entries), keyed by the site's slugs.
 */
object RivenGrading {
    const val DISPOSITION = 1.2
    const val MAX_RANK = 8

    /** S and F reach 1.5 past the ±10% a roll can reach, to allow for the game's rounding. */
    private const val LIMIT = 11.5

    /** Above this many points of doubt from rounding, half a grade either way, a letter would mislead. */
    private const val MAX_DOUBT = 2.0

    /** Recoil is the stat whose buff is a negative number. */
    private val negativeBuffs = setOf("recoil")

    private fun layout(buffs: Int, curses: Int): Pair<Double, Double>? = when (buffs to curses) {
        2 to 0 -> 0.99 to 0.0
        2 to 1 -> 1.2375 to 0.495
        3 to 0 -> 0.75 to 0.0
        3 to 1 -> 0.9375 to 0.75
        else -> null
    }

    fun scaleOf(slug: String): RivenAttributeScale? = scales[slug]

    /**
     * Grades a riven's stats. The values are tried at the listed rank and at max rank, and with the
     * weapon's old disposition when given, and read whichever way the most stats make sense.
     */
    fun grade(
        rivenClass: RivenClass,
        stats: List<RivenStat>,
        rank: Int,
        oldDisposition: Double? = null,
    ): RivenRolls? {
        val buffs = stats.count { it.positive }
        val (buffScale, curseScale) = layout(buffs, stats.size - buffs) ?: return null
        val ranks = listOf(rank.coerceIn(0, MAX_RANK), MAX_RANK).distinct()
        val dispositions = listOfNotNull(DISPOSITION, oldDisposition?.takeIf { abs(it - DISPOSITION) > 0.005 })
        val readings = dispositions.flatMap { disposition ->
            ranks.map { r ->
                val rolls = stats.map { roll(it, rivenClass, r, disposition, if (it.positive) buffScale else curseScale) }
                RivenRolls(rolls, r, disposition.takeIf { it != DISPOSITION })
            }
        }
        // The first reading wins ties: the listed rank and today's disposition.
        return readings.maxBy { reading -> reading.stats.count { it != null && !it.outOfRange } }
    }

    private fun roll(stat: RivenStat, rivenClass: RivenClass, rank: Int, disposition: Double, layout: Double): StatRoll? {
        val attribute = scales[stat.urlName] ?: return null
        val base = attribute.base(rivenClass) ?: return null
        val shown = when (attribute.scale) {
            RollScale.PERCENT -> 100.0
            else -> 1.0
        }
        val middle = base * 10 * (rank + 1) * disposition * layout * shown
        if (middle <= 0) return null
        val magnitude = when (attribute.scale) {
            RollScale.MULTIPLIER -> abs(stat.value - 1)
            else -> abs(stat.value)
        }
        val rolled = (magnitude / middle - 1) * 100
        // A bigger curse is a worse one.
        val deviation = if (stat.positive) rolled else -rolled
        // Listings carry what the game shows: one decimal, or two for multipliers.
        val halfStep = if (attribute.scale == RollScale.MULTIPLIER) 0.005 else 0.05
        val doubt = halfStep / middle * 100
        val outOfRange = abs(deviation) > LIMIT + doubt
        val tooRounded = doubt > MAX_DOUBT
        val sign = if (stat.positive != (stat.urlName in negativeBuffs)) 1.0 else -1.0
        fun shownValue(fraction: Double): Double {
            val value = middle * fraction
            return if (attribute.scale == RollScale.MULTIPLIER) 1 + sign * value else sign * value
        }
        // Worst first: the smallest buff, or the biggest curse.
        val (worst, best) = if (stat.positive) shownValue(0.9) to shownValue(1.1) else shownValue(1.1) to shownValue(0.9)
        return StatRoll(
            grade = if (outOfRange || tooRounded) null else RivenGrade.of(deviation),
            deviation = deviation,
            worst = worst,
            best = best,
            outOfRange = outOfRange,
            tooRounded = tooRounded,
        )
    }

    /** Columns: rifle, shotgun, pistol, archgun, melee, kitgun, zaw. */
    private val scales: Map<String, RivenAttributeScale> = mapOf(
        "ammo_efficiency" to RivenAttributeScale(RollScale.PERCENT, 0.005, 0.005, 0.005, 0.005, null, 0.005, null),
        "ammo_maximum" to RivenAttributeScale(RollScale.PERCENT, 0.00555, 0.01, 0.01, 0.0111, null, 0.01, null),
        "base_damage_/_melee_damage" to RivenAttributeScale(RollScale.PERCENT, 0.0183333, 0.0183, 0.0244, 0.0111, 0.0183, 0.0244, 0.0183),
        "blast" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "chance_to_gain_combo_count" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.01165, null, 0.01165),
        "chance_to_gain_extra_combo_count" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.00653, null, 0.00653),
        "channeling_damage" to RivenAttributeScale(RollScale.PLAIN, null, null, null, null, 0.27224, null, 0.27224),
        "channeling_efficiency" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.00816, null, 0.00816),
        "cold_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.0133, 0.01, 0.01, 0.01),
        "combo_duration" to RivenAttributeScale(RollScale.PLAIN, null, null, null, null, 0.09, null, 0.09),
        "corrosive" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "critical_chance" to RivenAttributeScale(RollScale.PERCENT, 0.016666, 0.01, 0.016666, 0.0111, 0.02, 0.016666, 0.02),
        "critical_chance_on_slide_attack" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.013334, null, 0.013334),
        "critical_damage" to RivenAttributeScale(RollScale.PERCENT, 0.013333, 0.01, 0.01, 0.0089, 0.01, 0.01, 0.01),
        "damage_to_orokin" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "damage_to_scaldra" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "damage_to_techrot" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "damage_vs_corpus" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "damage_vs_grineer" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "damage_vs_infested" to RivenAttributeScale(RollScale.MULTIPLIER, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005),
        "electric_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.0133, 0.01, 0.01, 0.01),
        "finisher_damage" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.0133, null, 0.0133),
        "fire_rate_/_attack_speed" to RivenAttributeScale(RollScale.PERCENT, 0.00667, 0.01, 0.0083, 0.00667, 0.0061, 0.0083, 0.0061),
        "gas" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "heat_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.0133, 0.01, 0.01, 0.01),
        "heavy_attack_wind_up_speed" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.0133, null, 0.0133),
        "impact_damage" to RivenAttributeScale(RollScale.PERCENT, 0.013333, 0.013333, 0.013333, 0.01, 0.0133, 0.013333, 0.0133),
        "magazine_capacity" to RivenAttributeScale(RollScale.PERCENT, 0.005555, 0.005555, 0.005555, 0.0067, null, 0.005555, null),
        "magazine_reloaded_s_when_holstered" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, null, null, 0.01, null),
        "magnetic" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "melee_damage_on_heavy_attack" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.0133, null, 0.0133),
        "multishot" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.0133, 0.0133, 0.0067, null, 0.0133, null),
        "parry_angle" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.009, null, 0.009),
        "projectile_speed" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, null, null, 0.01, null),
        "punch_through" to RivenAttributeScale(RollScale.PLAIN, 0.03, 0.03, 0.03, 0.03, null, 0.03, null),
        "puncture_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01333, 0.01333, 0.01333, 0.01, 0.0133, 0.01333, 0.0133),
        "radiation" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "range" to RivenAttributeScale(RollScale.PLAIN, null, null, null, null, 0.02158, null, 0.02158),
        "recoil" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, null, 0.01, null),
        "reload_speed" to RivenAttributeScale(RollScale.PERCENT, 0.005555, 0.005555, 0.005555, 0.0111, null, 0.005555, null),
        "slam_attack_damage" to RivenAttributeScale(RollScale.PERCENT, null, null, null, null, 0.0133, null, 0.0133),
        "slash_damage" to RivenAttributeScale(RollScale.PERCENT, 0.013333, 0.013333, 0.013333, 0.01, 0.0133, 0.013333, 0.0133),
        "status_chance" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.0067, 0.01, 0.01, 0.01),
        "status_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "status_duration" to RivenAttributeScale(RollScale.PERCENT, 0.01111, 0.01111, 0.01111, 0.01111, 0.01111, 0.01111, 0.01111),
        "toxin_damage" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.0133, 0.01, 0.01, 0.01),
        "viral" to RivenAttributeScale(RollScale.PERCENT, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01),
        "weak_point_critical_chance" to RivenAttributeScale(RollScale.PERCENT, 0.0275, 0.0275, 0.0275, 0.0275, null, 0.0275, null),
        "weak_point_damage" to RivenAttributeScale(RollScale.PERCENT, 0.025, 0.025, 0.025, 0.025, null, 0.025, null),
        "zoom" to RivenAttributeScale(RollScale.PERCENT, 0.006666, 0.004666, 0.0089, 0.006666, null, 0.0089, null),
    )
}
