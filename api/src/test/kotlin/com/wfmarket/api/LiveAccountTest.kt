package com.wfmarket.api

import com.wfmarket.api.model.AuctionItem
import com.wfmarket.api.model.AuctionType
import com.wfmarket.api.model.CreateAuction
import com.wfmarket.api.model.CreateOrder
import com.wfmarket.api.model.RivenStat
import com.wfmarket.api.model.UpdateAuction
import com.wfmarket.api.model.OrderType
import com.wfmarket.api.model.UpdateOrder
import com.wfmarket.api.socket.WfmSockets
import com.wfmarket.api.socket.SocketEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Exercises signed-in routes with a real account. Skipped unless `WFM_EMAIL` and `WFM_PASSWORD` are
 * set. Only leaves no trace: the one order it creates is hidden and is removed again.
 */
class LiveAccountTest {
    private val email: String? = System.getenv("WFM_EMAIL")
    private val password: String? = System.getenv("WFM_PASSWORD")

    private class Session : WfmContext {
        override val platform = "pc"
        override val language = "en"
        override val crossplay = true
        @Volatile override var token: String? = null
    }

    private val session = Session()
    private val api = WfmApi(session)

    /** One sign-in for the whole run, because the site rate limits them. */
    private suspend fun signIn() {
        assumeTrue(email != null && password != null)
        session.token = sharedToken ?: api.signIn(email!!, password!!, "contract-test-device").token.also { sharedToken = it }
    }

    private companion object {
        @Volatile var sharedToken: String? = null
    }

    @Test fun signInAndReadAccount() = runBlocking {
        signIn()
        val me = api.me()
        println("me: verified=${me.verification} slugSet=${me.slug.isNotBlank()} platform=${me.platform} crossplay=${me.crossplay} " +
            "unread=${me.unreadMessages} linked=${me.linkedAccounts.keys} hasEmail=${me.hasEmail} ignored=${me.ignoreList.size}")
        assertTrue(me.id.isNotBlank())
        println("orders: ${api.myOrders().size}")
        println("my auctions: ${api.myAuctions().size}")
        println("bids: ${api.participatingAuctions().size}")
        val chats = api.chats()
        println("chats: ${chats.size}, unread=${chats.sumOf { it.unreadCount }}, withLast=${chats.count { it.messages.isNotEmpty() }}, named=${chats.count { it.chatName.isNotBlank() }}")
        if (me.slug.isNotBlank()) {
            println("history: ${api.closedOrders(me.slug).size}")
            println("reviews: ${api.reviews(me.slug).reviews.size}")
        }
    }

    @Test fun orderLifecycle() = runBlocking {
        signIn()
        val item = api.item("maiming_strike")
        val created = try {
            api.createOrder(
                CreateOrder(itemId = item.id, type = OrderType.BUY, platinum = 1, quantity = 1, visible = false, rank = 0),
            )
        } catch (e: WfmException) {
            println("rejected: fields=${e.fieldErrors} messages=${e.messages}")
            throw e
        }
        try {
            assertEquals(false, created.visible)
            assertTrue(api.me().id.isNotBlank())
            val updated = api.updateOrder(created.id, UpdateOrder(platinum = 2))
            assertEquals(2, updated.platinum)
            assertTrue(api.myOrders().any { it.id == created.id })
        } finally {
            api.deleteOrder(created.id)
        }
        assertTrue(api.myOrders().none { it.id == created.id })
    }

    @Test fun socketsAuthorize() = runBlocking {
        signIn()
        val sockets = WfmSockets(session, api.http)
        val frames = java.util.concurrent.ConcurrentLinkedQueue<String>()
        sockets.frameObserver = { source, type -> frames.add("$source:$type") }
        sockets.connect()
        try {
            assertNotNull("presence socket refused the session", withTimeoutOrNull(15_000) { sockets.state.first { it.statusAuthorized } })
            assertNotNull(withTimeoutOrNull(15_000) { sockets.state.first { it.chatConnected } })
            // A chat that can't exist: authorized sockets answer it, and no real player is involved.
            val error = async { withTimeoutOrNull(6_000) { sockets.events.filterIsInstance<SocketEvent.ServerError>().first() } }
            delay(200)
            sockets.sendChatMessage("000000000000000000000000", "contract test")
            println("socket error for a bogus chat: ${error.await()}")
            println("frame types: ${frames.toSet()}")
        } finally {
            sockets.disconnect()
        }
    }

    /** A hidden direct sell nobody can see, which closing removes outright. */
    @Test fun auctionLifecycle() = runBlocking {
        signIn()
        val item = AuctionItem(
            type = AuctionType.RIVEN,
            weaponUrlName = "soma",
            name = "hexa-critatis",
            masteryLevel = 8,
            modRank = 0,
            reRolls = 0,
            polarity = "vazarin",
            // A real riven's name and stats: the server checks that the name fits the stats.
            attributes = listOf(
                RivenStat("critical_chance", 154.6, true),
                RivenStat("critical_damage", 137.5, true),
                RivenStat("status_chance", 104.9, true),
                RivenStat("recoil", 86.5, false),
            ),
        )
        val created = try {
            api.createAuction(CreateAuction(startingPrice = 999, buyoutPrice = 999, visible = false, item = item))
        } catch (e: WfmException) {
            println("auction rejected: fields=${e.fieldErrors} messages=${e.messages}")
            throw e
        }
        try {
            assertEquals(false, created.visible)
            assertTrue(api.myAuctions().any { it.id == created.id })
            assertEquals(990, api.updateAuction(created.id, UpdateAuction(990, 990, visible = false)).startingPrice)
        } finally {
            api.closeAuction(created.id)
        }
        println("auctions left open: ${api.myAuctions().count { !it.closed }}")
    }

    @Test fun statusCanBeReadAndRestored() = runBlocking {
        signIn()
        val sockets = WfmSockets(session, api.http)
        val statuses = async {
            withTimeoutOrNull(12_000) { sockets.events.filterIsInstance<SocketEvent.StatusChanged>().first() }
        }
        delay(100)
        sockets.connect()
        try {
            val initial = statuses.await()
            println("status on connect: ${initial?.status}")
            assertNotNull(initial)
            val answer = async { withTimeoutOrNull(8_000) { sockets.events.filterIsInstance<SocketEvent.StatusChanged>().first() } }
            delay(100)
            assertTrue(sockets.setStatus(initial!!.status, null))
            println("status command answered: ${answer.await()?.status}")
            assertNotNull(answer.await())
        } finally {
            sockets.disconnect()
        }
    }

    /** Compares the rejection a bogus chat gets with and without a session. */
    @Test fun chatSocketAuthProbe() = runBlocking {
        signIn()
        suspend fun probe(context: WfmContext): SocketEvent.ServerError? {
            val sockets = WfmSockets(context, api.http)
            sockets.connect()
            try {
                withTimeoutOrNull(10_000) { sockets.state.first { it.chatConnected } }
                val answer = async { withTimeoutOrNull(6_000) { sockets.events.filterIsInstance<SocketEvent.ServerError>().first() } }
                delay(200)
                sockets.sendChatMessage("000000000000000000000000", "contract test")
                return answer.await()
            } finally {
                sockets.disconnect()
            }
        }
        println("bogus chat, signed in: ${probe(session)}")
        println("bogus chat, anonymous: ${probe(Session())}")
    }
}
