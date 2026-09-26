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

