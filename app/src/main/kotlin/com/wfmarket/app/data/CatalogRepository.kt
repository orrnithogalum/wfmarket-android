package com.wfmarket.app.data

import android.content.Context
import com.wfmarket.api.WfmApi
import com.wfmarket.api.WfmException
import com.wfmarket.api.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File

/** Everything the UI needs to turn ids and slugs into names and images. */
data class Catalog(
    val language: String = "en",
    val items: List<Item> = emptyList(),
    val rivenWeapons: List<RivenWeapon> = emptyList(),
    val rivenAttributes: List<RivenAttribute> = emptyList(),
    val lichWeapons: List<NemesisWeapon> = emptyList(),
    val lichEphemeras: List<Ephemera> = emptyList(),
    val lichQuirks: List<Quirk> = emptyList(),
    val sisterWeapons: List<NemesisWeapon> = emptyList(),
    val sisterEphemeras: List<Ephemera> = emptyList(),
    val sisterQuirks: List<Quirk> = emptyList(),
    val locations: List<Location> = emptyList(),
    val missions: List<NamedEntity> = emptyList(),
    val npcs: List<NamedEntity> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
) {
    val isLoaded: Boolean get() = items.isNotEmpty()

    val itemsById: Map<String, Item> by lazy { items.associateBy { it.id } }
    val itemsBySlug: Map<String, Item> by lazy { items.associateBy { it.slug } }
    val locationsById: Map<String, Location> by lazy { locations.associateBy { it.id } }
    val missionsById: Map<String, NamedEntity> by lazy { missions.associateBy { it.id } }
    val npcsById: Map<String, NamedEntity> by lazy { npcs.associateBy { it.id } }
    val attributesBySlug: Map<String, RivenAttribute> by lazy { rivenAttributes.associateBy { it.slug } }
    val rivenWeaponsBySlug: Map<String, RivenWeapon> by lazy { rivenWeapons.associateBy { it.slug } }

    /** Warframe's own data names weapons in English, whatever language the site is read in. */
    val rivenWeaponsByEnglishName: Map<String, RivenWeapon> by lazy {
        rivenWeapons.mapNotNull { weapon -> weapon.i18n["en"]?.name?.lowercase()?.let { it to weapon } }.toMap()
    }

    fun englishName(weapon: RivenWeapon): String = weapon.i18n["en"]?.name ?: weapon.slug.humanize()

    /**
     * Grades a riven's stats, or null for one the game couldn't roll. [classes] is Warframe's word on
     * which riven each weapon takes; the site's own is used where it has none.
     */
    fun rivenRolls(item: AuctionItem, classes: Map<String, RivenClass>, allowOldDisposition: Boolean = true): RivenRolls? {
        if (item.type != AuctionType.RIVEN || item.attributes.isEmpty()) return null
        val weapon = rivenWeaponsBySlug[item.weaponUrlName] ?: return null
        val rivenClass = classes[englishName(weapon).lowercase()] ?: RivenClass.of(weapon) ?: return null
        // The site still lists each weapon's disposition from before they were all set to 1.2, which
        // is what older listings were entered at.
        return RivenGrading.grade(rivenClass, item.attributes, item.modRank ?: 0, weapon.disposition.takeIf { allowOldDisposition })
    }

    private val setsBySlug: Map<String, Item> by lazy { items.filter { "set" in it.tags }.associateBy { it.slug } }

    /** Finds a part's set from its slug: `venato_prime_blade` belongs to `venato_prime_set`. */
    private fun setOf(item: Item): Item? {
        var prefix = item.slug
        while ('_' in prefix) {
            prefix = prefix.substringBeforeLast('_')
            setsBySlug["${prefix}_set"]?.let { return it }
        }
        return null
    }

    /** Some set parts have no image of their own, so they borrow their set's. */
    fun art(item: Item): ItemArt {
        fun usable(path: String?) = path?.takeIf { it.isNotBlank() && !it.contains("unknown") }
        val own = item.i18n.localized(language)
        val set = if (usable(own?.icon) == null) setOf(item)?.i18n?.localized(language) else null
        val full = usable(own?.icon) ?: usable(set?.icon)
        return ItemArt(
            thumb = assetUrl(usable(own?.thumb) ?: usable(set?.thumb) ?: full),
            full = assetUrl(full),
            badge = assetUrl(usable(own?.subIcon)),
        )
    }

    /**
     * The current Prime Access, found from the catalogue itself: an id begins with its creation time,
     * and everything in a Prime Access is added together on release day. Frame first, then gear.
     */
    val primeAccess: List<Item> by lazy {
        val sets = items.filter { "prime" in it.tags && "set" in it.tags }
        val newest = sets.filter { "warframe" in it.tags }.maxByOrNull { it.createdAtSeconds } ?: return@lazy emptyList()
        sets.filter { kotlin.math.abs(it.createdAtSeconds - newest.createdAtSeconds) < PRIME_RELEASE_WINDOW_SECONDS }
            .sortedBy { if ("warframe" in it.tags) 0 else 1 }
    }

    /** What a Prime Resurgence offers, as far as it's tradable: frames first, then weapons. */
    fun resurgenceItems(resurgence: PrimeResurgence): List<Item> {
        val byRef = items.filter { "set" in it.tags }.associateBy { it.gameRef }
        return resurgence.gameRefs.mapNotNull { byRef[it] }.sortedBy { if ("warframe" in it.tags) 0 else 1 }
    }

    fun itemName(item: Item): String = item.i18n.localized(language)?.name ?: item.slug

    fun i18n(map: Map<String, I18n>): I18n? = map.localized(language)

    /** The display name for a weapon in any auction type. */
    fun weaponName(type: String, slug: String): String {
        val i18n = when (type) {
            AuctionType.RIVEN -> rivenWeapons.firstOrNull { it.slug == slug }?.i18n
            AuctionType.LICH -> lichWeapons.firstOrNull { it.slug == slug }?.i18n
            else -> sisterWeapons.firstOrNull { it.slug == slug }?.i18n
        }
        return i18n?.localized(language)?.name ?: slug.humanize()
    }

    /** Full size, because weapons are shown wider than the 128px thumbnails. */
    fun weaponImage(type: String, slug: String): String? {
        val i18n = when (type) {
            AuctionType.RIVEN -> rivenWeapons.firstOrNull { it.slug == slug }?.i18n
            AuctionType.LICH -> lichWeapons.firstOrNull { it.slug == slug }?.i18n
            else -> sisterWeapons.firstOrNull { it.slug == slug }?.i18n
        }
        return assetUrl(i18n?.localized(language)?.icon)
    }

    fun attributeName(slug: String): String =
        attributesBySlug[slug]?.i18n?.localized(language)?.name ?: slug.humanize()


