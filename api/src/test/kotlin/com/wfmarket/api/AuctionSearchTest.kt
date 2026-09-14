package com.wfmarket.api

import com.wfmarket.api.model.Auction
import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.model.AuctionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class AuctionSearchTest {
    private fun params(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun decodesTheApostropheInANote() {
        assertEquals("it's a beam weapon", Auction(noteRaw = "it&#x27;s a beam weapon").noteText)
    }

    @Test
    fun aLinkGivesBackTheSameSearch() {
        val search = AuctionSearch(
            weaponUrlName = "torid",
            sortBy = "price_desc",
            positiveStats = listOf("critical_chance", "multishot"),
            negativeStats = listOf("zoom"),
            reRollsMax = 10,
            modRankMaxed = true,
            sellerStatuses = setOf("ingame", "online"),
            priceMin = 50,
            priceMax = 400,
        )
        assertEquals(search, AuctionSearch.fromQuery(params(search.toUrl())))

        val lich = AuctionSearch(type = AuctionType.LICH, element = "heat", hasEphemera = true, damageMin = 55)
        assertEquals(lich, AuctionSearch.fromQuery(params(lich.toUrl())))
    }

    @Test
    fun readsTheSitesOwnSearchLinks() {
        val search = AuctionSearch.fromQuery(params("https://warframe.market/auctions/search?type=riven&weapon_url_name=torid&polarity=any&sort_by=price_desc"))
        assertEquals("torid", search.weaponUrlName)
        assertEquals(null, search.polarity)
        assertEquals("price_desc", search.sortBy)
        assertTrue(search.sellerStatuses.isEmpty())
    }
}
