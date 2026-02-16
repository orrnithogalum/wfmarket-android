package com.wfmarket.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val type: String,
    val platinum: Int,
    val quantity: Int,
    val perTrade: Int = 1,
    val rank: Int? = null,
    val charges: Int? = null,
    val subtype: String? = null,
    val amberStars: Int? = null,
    val cyanStars: Int? = null,
    val visible: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val itemId: String = "",
    val group: String? = null,
    val user: User? = null,
)

@Serializable
data class TopOrders(val sell: List<Order> = emptyList(), val buy: List<Order> = emptyList())

/** Body of `POST /v2/order`. Null fields are omitted. */
@Serializable
data class CreateOrder(
    val itemId: String,
    val type: String,
    val platinum: Int,
    val quantity: Int,
    /** Only for bulk-tradable items: the server rejects it on anything else. */
    val perTrade: Int? = null,
    val visible: Boolean = true,
    val rank: Int? = null,
    val charges: Int? = null,
    val subtype: String? = null,
    val amberStars: Int? = null,
    val cyanStars: Int? = null,
)

/** Body of `PATCH /v2/order/{id}`. Null fields are left unchanged. */
@Serializable
data class UpdateOrder(
    val platinum: Int? = null,
    val quantity: Int? = null,
    val perTrade: Int? = null,
    val visible: Boolean? = null,
    val rank: Int? = null,
    val charges: Int? = null,
    val subtype: String? = null,
    val amberStars: Int? = null,
    val cyanStars: Int? = null,
)
