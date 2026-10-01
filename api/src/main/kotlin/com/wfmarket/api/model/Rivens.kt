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

