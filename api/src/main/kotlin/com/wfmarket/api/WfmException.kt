package com.wfmarket.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class WfmErrorKind { NETWORK, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, VALIDATION, RATE_LIMITED, SERVER, UNKNOWN }

/**
 * Every API failure surfaces as this exception. [messages] are already human readable, and
 * [fieldErrors] maps form fields to the reason they were rejected.
 */
class WfmException(
    val kind: WfmErrorKind,
    val messages: List<String>,
    val fieldErrors: Map<String, String> = emptyMap(),
    val status: Int = 0,
    cause: Throwable? = null,
) : Exception(messages.firstOrNull() ?: fieldErrors.values.firstOrNull() ?: kind.name, cause) {

    /** Everything the server complained about, on one line per problem. */
    val summary: String
        get() = (messages + fieldErrors.values).distinct().joinToString("\n").ifBlank {
            when (kind) {
                WfmErrorKind.NETWORK -> "Can't reach warframe.market. Check your connection."
                WfmErrorKind.UNAUTHORIZED -> "Please sign in again."
                WfmErrorKind.FORBIDDEN -> "You aren't allowed to do that."
                WfmErrorKind.NOT_FOUND -> "Not found."
                WfmErrorKind.RATE_LIMITED -> "Too many requests. Try again in a moment."
                WfmErrorKind.SERVER -> "warframe.market is having problems. Try again later."
                else -> "Something went wrong."
            }
        }
}

