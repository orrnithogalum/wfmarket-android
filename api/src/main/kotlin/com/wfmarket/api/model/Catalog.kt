package com.wfmarket.api.model

import kotlinx.serialization.Serializable

@Serializable
data class Versions(val collections: Map<String, String> = emptyMap())

