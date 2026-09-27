package com.wfmarket.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of Warframe's official world state this app uses. */
@Serializable
data class WorldState(
    @SerialName("Events") val events: List<WorldEvent> = emptyList(),
    @SerialName("PrimeVaultTraders") val primeVaultTraders: List<PrimeVaultTrader> = emptyList(),
)

@Serializable
data class WorldDate(@SerialName("\$date") val date: WorldDateValue = WorldDateValue())

@Serializable
data class WorldDateValue(@SerialName("\$numberLong") val millis: Long = 0)

@Serializable
data class WorldEvent(
    @SerialName("Messages") val messages: List<WorldMessage> = emptyList(),
    @SerialName("Prop") val link: String? = null,
    @SerialName("ImageUrl") val imageUrl: String? = null,
    @SerialName("Date") val date: WorldDate = WorldDate(),
)

@Serializable
data class WorldMessage(
    @SerialName("LanguageCode") val language: String = "",
    @SerialName("Message") val message: String = "",
)

@Serializable
data class PrimeVaultTrader(
    @SerialName("Activation") val activation: WorldDate = WorldDate(),
    @SerialName("Expiry") val expiry: WorldDate = WorldDate(),
    @SerialName("Manifest") val manifest: List<PrimeVaultItem> = emptyList(),
)

@Serializable
data class PrimeVaultItem(@SerialName("ItemType") val itemType: String = "")

