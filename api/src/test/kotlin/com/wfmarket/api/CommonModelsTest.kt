package com.wfmarket.api

import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.I18n
import com.wfmarket.api.model.Language
import com.wfmarket.api.model.Platform
import com.wfmarket.api.model.STATIC_ASSETS
import com.wfmarket.api.model.UserStatus
import com.wfmarket.api.model.V1UserOrIdSerializer
import com.wfmarket.api.model.assetUrl
import com.wfmarket.api.model.localized
import com.wfmarket.api.model.sellerStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommonModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun unknownWireValuesFallBackToTheDefaults() {
        assertEquals(Platform.PC, Platform.of("dreamcast"))
        assertEquals(Platform.PC, Platform.of(null))
        assertEquals(Language.EN, Language.of("tlh"))
        assertEquals(UserStatus.OFFLINE, UserStatus.of(null))
    }

    @Test
    fun knownWireValuesMapToTheirEntries() {
        assertEquals(Platform.PLAYSTATION, Platform.of("ps4"))
        assertEquals(Language.ZH_HANT, Language.of("zh-hant"))
        assertEquals(UserStatus.INGAME, UserStatus.of("ingame"))
    }

    @Test
    fun translationsFallBackToEnglishThenAnything() {
        val names = mapOf("en" to I18n(name = "Mesa"), "fr" to I18n(name = "Mesa (fr)"))
        assertEquals("Mesa (fr)", names.localized("fr")?.name)
        assertEquals("Mesa", names.localized("de")?.name)
        assertEquals("Only", mapOf("ko" to I18n(name = "Only")).localized("de")?.name)
        assertNull(emptyMap<String, I18n>().localized("en"))
    }

    @Test
    fun assetPathsResolveAgainstTheStaticHost() {
        assertEquals(STATIC_ASSETS + "items/images/en/mesa.png", assetUrl("items/images/en/mesa.png"))
        assertEquals(STATIC_ASSETS + "items/images/en/mesa.png", assetUrl("/items/images/en/mesa.png"))
        assertEquals("https://cdn.example/a.png", assetUrl("https://cdn.example/a.png"))
        assertNull(assetUrl(""))
        assertNull(assetUrl(null))
    }

    @Test
    fun aUserCanArriveAsAnIdOrAnObject() {
        assertEquals("abc123", json.decodeFromString(V1UserOrIdSerializer, "\"abc123\"").id)
        val full = json.decodeFromString(V1UserOrIdSerializer, """{"id":"u1","ingame_name":"Tenno","reputation":12,"status":"ingame"}""")
        assertEquals("Tenno", full.ingameName)
        assertEquals(12, full.reputation)
    }

    @Test
    fun auctionsDecodeTheirOwnerEitherWay() {
        val byId = json.decodeFromString(Auction.serializer(), """{"id":"a1","owner":"u9","starting_price":50}""")
        assertEquals("u9", byId.owner.id)
        val byObject = json.decodeFromString(Auction.serializer(), """{"id":"a2","owner":{"id":"u9","ingame_name":"Seller"}}""")
        assertEquals("Seller", byObject.owner.ingameName)
    }

    @Test
    fun invisibleAndUnknownSellersFilterAsOffline() {
        assertEquals("ingame", sellerStatus("ingame"))
        assertEquals("online", sellerStatus("online"))
        assertEquals("offline", sellerStatus("invisible"))
        assertEquals("offline", sellerStatus(null))
    }
}
