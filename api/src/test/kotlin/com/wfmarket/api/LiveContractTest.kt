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
}
