package com.wfmarket.app.data

import com.wfmarket.api.WfmApi
import com.wfmarket.api.model.CreateOrder
import com.wfmarket.api.model.Order
import com.wfmarket.api.model.UpdateOrder

/** Item, order and profile data. A thin seam, so screens never touch the API client directly. */
class MarketRepository(private val api: WfmApi, private val changes: DataChanges) {
    /** Lists can lag behind a removal, and a removed order should never come back on screen. */
    private val removedOrders = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun List<Order>.withoutRemoved() = filterNot { it.id in removedOrders }

    suspend fun showcase() = api.showcase()
    suspend fun primeResurgence() = api.primeResurgence()
    suspend fun recentOrders() = api.recentOrders().withoutRemoved()
    suspend fun item(slug: String) = api.item(slug)
    suspend fun itemSet(slug: String) = api.itemSet(slug)
    suspend fun orders(slug: String) = api.ordersForItem(slug).withoutRemoved()
    suspend fun topOrders(slug: String) = api.topOrders(slug)


