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

/** Languages the site publishes content in. */
enum class Language(val wire: String, val label: String) {
    EN("en", "English"),
    RU("ru", "Русский"),
    KO("ko", "한국어"),
    FR("fr", "Français"),
    SV("sv", "Svenska"),
    DE("de", "Deutsch"),
    ZH_HANT("zh-hant", "繁體中文"),
    ZH_HANS("zh-hans", "简体中文"),
    PT("pt", "Português"),
    ES("es", "Español"),
    PL("pl", "Polski"),
    CS("cs", "Čeština"),
    UK("uk", "Українська"),
    IT("it", "Italiano"),
    TR("tr", "Türkçe"),
    JA("ja", "日本語");

    companion object {
        fun of(wire: String?): Language = entries.firstOrNull { it.wire == wire } ?: EN
    }
}

/** A user's presence, as used on orders, profiles and the status socket. */
enum class UserStatus(val wire: String, val label: String) {
    INGAME("ingame", "In game"),
    ONLINE("online", "Online"),
    INVISIBLE("invisible", "Invisible"),
    OFFLINE("offline", "Offline");

    companion object {
        fun of(wire: String?): UserStatus = entries.firstOrNull { it.wire == wire } ?: OFFLINE
    }
}

object OrderType {
    const val SELL = "sell"
    const val BUY = "buy"
}

