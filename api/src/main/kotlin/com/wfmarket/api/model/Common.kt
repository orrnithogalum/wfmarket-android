package com.wfmarket.api.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** Platforms accepted by the `Platform` request header. */
enum class Platform(val wire: String, val label: String) {
    PC("pc", "PC"),
    PLAYSTATION("ps4", "PlayStation"),
    XBOX("xbox", "Xbox"),
    SWITCH("switch", "Switch"),
    MOBILE("mobile", "Mobile");

    companion object {
        fun of(wire: String?): Platform = entries.firstOrNull { it.wire == wire } ?: PC
    }
}

