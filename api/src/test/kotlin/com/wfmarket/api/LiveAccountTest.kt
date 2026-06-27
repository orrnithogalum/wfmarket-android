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
}
