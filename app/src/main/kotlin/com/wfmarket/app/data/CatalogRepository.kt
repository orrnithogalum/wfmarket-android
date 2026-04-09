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


