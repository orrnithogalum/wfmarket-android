package com.wfmarket.api

import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.model.AuctionType
import com.wfmarket.api.model.V1User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionModelTest {
    @Test
    fun aDirectSaleGoesForItsBuyout() {
        assertEquals(300, Auction(isDirectSell = true, startingPrice = 300, buyoutPrice = 300).currentPrice)
        assertEquals(250, Auction(isDirectSell = true, startingPrice = 250).currentPrice)
    }

    @Test
    fun anAuctionGoesForItsTopBidOrItsStart() {
        assertEquals(180, Auction(isDirectSell = false, startingPrice = 100, buyoutPrice = 500, topBid = 180).currentPrice)
        assertEquals(100, Auction(isDirectSell = false, startingPrice = 100).currentPrice)
    }

    @Test
    fun theSiteRefusesOpenSearches() {
        assertFalse(AuctionSearch().canSearch)
        assertTrue(AuctionSearch(weaponUrlName = "torid").canSearch)
        assertTrue(AuctionSearch(positiveStats = listOf("critical_chance")).canSearch)
        assertFalse(AuctionSearch(type = AuctionType.LICH).canSearch)
        assertTrue(AuctionSearch(type = AuctionType.LICH, element = "heat").canSearch)
    }

    @Test
    fun aRivenSearchSendsOnlyRivenFilters() {
        val query = AuctionSearch(
            weaponUrlName = "torid", positiveStats = listOf("critical_chance", "multishot"), negativeStats = listOf("zoom"),
            masteryRankMax = 12, modRankMaxed = true, element = "heat",
        ).toQuery()
        assertEquals("critical_chance,multishot", query["positive_stats"])
        assertEquals("zoom", query["negative_stats"])
        assertEquals("12", query["mastery_rank_max"])
        assertEquals("maxed", query["mod_rank"])
        assertFalse("element" in query)
    }

    @Test
    fun aLichSearchSendsOnlyLichFilters() {
        val query = AuctionSearch(type = AuctionType.LICH, weaponUrlName = "kuva_bramma", element = "toxin", hasEphemera = true, damageMin = 50, positiveStats = listOf("x")).toQuery()
        assertEquals("toxin", query["element"])
        assertEquals("true", query["has_ephemera"])
        assertEquals("50", query["damage_min"])
        assertFalse("positive_stats" in query)
    }

    @Test
    fun readsLinksTheWaySearchesAreBuilt() {
        val search = AuctionSearch.fromQuery(
            mapOf("type" to "riven", "polarity" to "any", "positive_stats" to "a,b,c,d", "negative_stats" to "x,y", "price_min" to "-5", "price_max" to "300"),
        )
        assertEquals(null, search.polarity)
        assertEquals(listOf("a", "b", "c"), search.positiveStats)
        assertEquals(listOf("x"), search.negativeStats)
        assertEquals(null, search.priceMin)
        assertEquals(300, search.priceMax)
    }

    @Test
    fun keepsOnlyTheSitesBuyoutPolicies() {
        assertEquals("direct", AuctionSearch.fromQuery(mapOf("buyout_policy" to "direct")).buyoutPolicy)
        assertEquals(null, AuctionSearch.fromQuery(mapOf("buyout_policy" to "free")).buyoutPolicy)
    }

    @Test
    fun matchesSellersAndPricesTogether() {
        val search = AuctionSearch(weaponUrlName = "torid", sellerStatuses = setOf("ingame"), priceMax = 200)
        val cheapInGame = Auction(isDirectSell = true, buyoutPrice = 150, owner = V1User(status = "ingame"))
        val cheapOffline = cheapInGame.copy(owner = V1User(status = "invisible"))
        val dearInGame = cheapInGame.copy(buyoutPrice = 250)
        assertTrue(search.matches(cheapInGame))
        assertFalse(search.matches(cheapOffline))
        assertFalse(search.matches(dearInGame))
    }

    @Test
    fun anEmptySellerFilterMatchesEveryone() {
        assertTrue(AuctionSearch().matchesSeller(null))
        assertTrue(AuctionSearch().matchesSeller("invisible"))
    }
}
