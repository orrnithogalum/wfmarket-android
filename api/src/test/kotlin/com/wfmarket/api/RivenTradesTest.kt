package com.wfmarket.api

import com.wfmarket.api.model.RivenClass
import com.wfmarket.api.model.RivenTrade
import com.wfmarket.api.model.RivenWeapon
import com.wfmarket.api.model.relaxedJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RivenTradesTest {
    @Test
    fun quotesTheKeys() {
        assertEquals("""{"a": 1, "b_c": 2}""", relaxedJson("{a: 1, b_c: 2}"))
    }

    @Test
    fun turnsSingleQuotesIntoDoubleQuotes() {
        val parsed = Json.parseToJsonElement(relaxedJson("{name: 'Torid', note: 'say \"hi\"'}")).jsonObject
        assertEquals("Torid", parsed["name"]!!.jsonPrimitive.content)
        assertEquals("say \"hi\"", parsed["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun dropsTrailingCommas() {
        assertEquals("[1, 2]", relaxedJson("[1, 2,]"))
        assertEquals(Json.parseToJsonElement("""{"a": [1]}"""), Json.parseToJsonElement(relaxedJson("{a: [1,],\n}")))
    }

    @Test
    fun leavesWordsInsideStringsAndValuesAlone() {
        val parsed = Json.parseToJsonElement(relaxedJson("{rerolled: true, compatibility: 'key: value'}")).jsonObject
        assertEquals("true", parsed["rerolled"]!!.jsonPrimitive.content)
        assertEquals("key: value", parsed["compatibility"]!!.jsonPrimitive.content)
    }

    @Test
    fun readsAWeekOfFigures() {
        val text = """[
            {itemType: 'Rifle Riven Mod', compatibility: 'Torid', rerolled: false, avg: 120.5, stddev: 30, min: 10, max: 500, pop: 100, median: 95,},
            {itemType: 'Shotgun Riven Mod', compatibility: null, rerolled: true, avg: 40, stddev: 5, min: 20, max: 60, pop: 12, median: 38},
        ]"""
        val trades = Json.decodeFromString(ListSerializer(RivenTrade.serializer()), relaxedJson(text))
        assertEquals(2, trades.size)
        assertEquals(RivenClass.RIFLE, trades[0].rivenClass)
        assertEquals(95.0, trades[0].median, 0.0)
        assertNull(trades[1].compatibility)
        assertEquals(RivenClass.SHOTGUN, trades[1].rivenClass)
    }

    @Test
    fun namesTheRivenFromTheModName() {
        assertEquals(RivenClass.PISTOL, RivenClass.ofModName("Pistol Riven Mod"))
        assertEquals(RivenClass.MELEE, RivenClass.ofModName("melee riven mod"))
        assertNull(RivenClass.ofModName("Primed Continuity"))
    }

    @Test
    fun guessesTheRivenForWeaponsTheFiguresDontName() {
        fun weapon(group: String?, type: String?) = RivenWeapon(id = "w", slug = "w", group = group, rivenType = type)
        assertEquals(RivenClass.ARCHGUN, weapon("archgun", "rifle").let(RivenClass::of))
        assertEquals(RivenClass.PISTOL, weapon("secondary", "shotgun").let(RivenClass::of))
        assertEquals(RivenClass.ZAW, weapon("melee", "zaw").let(RivenClass::of))
        assertNull(weapon("primary", null).let(RivenClass::of))
    }
}
