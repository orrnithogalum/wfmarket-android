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

