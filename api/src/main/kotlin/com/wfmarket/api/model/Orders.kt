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

