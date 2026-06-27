package com.wfmarket.api

import com.wfmarket.api.model.AuctionSearch
import com.wfmarket.api.model.AuctionType
import com.wfmarket.api.socket.SocketEvent
import com.wfmarket.api.socket.WfmSockets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises every public endpoint against the live service, so contract drift shows up here first. */
class LiveContractTest {
    private val context = object : WfmContext {
        override val platform = "pc"
        override val language = "en"
        override val crossplay = true
        override val token: String? = null
    }
    private val api = WfmApi(context)

    @Test fun catalogue() = runBlocking {
        assertTrue(api.versions().collections.containsKey("items"))
        val items = api.items()
        assertTrue(items.size > 3000)
        assertTrue(items.any { it.vaulted == true })
        assertTrue(items.any { it.subtypes.isNotEmpty() })
        assertTrue(api.rivenWeapons().isNotEmpty())
        assertTrue(api.rivenAttributes().isNotEmpty())
        assertTrue(api.lichWeapons().isNotEmpty())
        assertTrue(api.lichEphemeras().isNotEmpty())
        assertTrue(api.lichQuirks().isNotEmpty())
        assertTrue(api.sisterWeapons().isNotEmpty())
        assertTrue(api.sisterEphemeras().isNotEmpty())
        assertTrue(api.sisterQuirks().isNotEmpty())
        assertTrue(api.locations().isNotEmpty())
        assertTrue(api.missions().isNotEmpty())
        assertTrue(api.npcs().isNotEmpty())
        assertTrue(api.achievements().isNotEmpty())
        assertTrue(api.showcase().items.isNotEmpty())
    }

    @Test fun itemDetails() = runBlocking {
        val item = api.item("mesa_prime_set")
        assertEquals("mesa_prime_set", item.slug)
        assertNotNull(item.tradingTax)
        assertNotNull(item.ducats)
        assertNotNull(item.i18n["en"]?.wikiLink)
        assertNotNull(item.i18n["en"]?.description)
        assertTrue(api.itemSet("mesa_prime_set").items.size > 1)
    }

    @Test fun orders() = runBlocking {
        val orders = api.ordersForItem("mesa_prime_set")
        assertTrue(orders.isNotEmpty())
        assertNotNull(orders.first().user)
        assertTrue(orders.first().user!!.ingameName.isNotBlank())
        val top = api.topOrders("mesa_prime_set")
        assertTrue(top.sell.isNotEmpty() && top.buy.isNotEmpty())
        assertTrue(api.recentOrders().isNotEmpty())
    }

    @Test fun statistics() = runBlocking {
        val stats = api.itemStatistics("mesa_prime_set")
        assertTrue(stats.closed.days90.isNotEmpty())
        assertTrue(stats.closed.hours48.isNotEmpty())
        assertNotNull(stats.closed.days90.first().openPrice)
        assertNotNull(stats.closed.days90.first().donchTop)
        assertTrue(stats.live.days90.any { it.orderType == "buy" })
        assertTrue(stats.live.days90.any { it.orderType == "sell" })
    }

    @Test fun dropSources() = runBlocking {
        val relic = api.dropSources("mesa_prime_chassis_blueprint")
        assertTrue(relic.any { it.type == "relic" && it.rates != null && it.relic != null })
        val mixed = api.dropSources("arcane_energize")
        assertTrue(mixed.any { it.type == "mission" && it.location != null && it.mission != null })
        assertTrue(mixed.any { it.type == "npc" && it.npc != null })
    }

    @Test fun profiles() = runBlocking {
        val slug = api.ordersForItem("mesa_prime_set").first().user!!.slug
        assertEquals(slug, api.user(slug).slug)
        assertTrue(api.ordersForUser(slug).isNotEmpty())
        api.closedOrders(slug)
        api.reviews(slug)
        api.userAuctions(slug)
        api.userAchievements(slug)
        Unit
    }

    @Test fun signInReportsReadableErrors() = runBlocking {
        val failure = runCatching {
            api.signIn("nobody-wfm-probe@example.invalid", "probe-not-a-real-password", "contract-test")
        }.exceptionOrNull() as WfmException
        assertEquals(WfmErrorKind.VALIDATION, failure.kind)
        assertEquals("Email not registered.", failure.summary)
    }

    @Test fun authenticatedRouteRejectsAnonymous() = runBlocking {
        val failure = runCatching { api.me() }.exceptionOrNull() as WfmException
        assertEquals(WfmErrorKind.UNAUTHORIZED, failure.kind)
    }
}
