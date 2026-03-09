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

/** Translates the site's `app.*` error keys into the English text its own UI shows. */
object ErrorMessages {
    private val table: Map<String, String> by lazy {
        runCatching {
            val text = ErrorMessages::class.java.getResourceAsStream("/wfm/messages_en.json")!!
                .bufferedReader().use { it.readText() }
            Json.parseToJsonElement(text).let { root ->
                (root as JsonObject).mapValues { (it.value as JsonPrimitive).content }
            }
        }.getOrDefault(emptyMap())
    }

    fun translate(key: String): String = table[key] ?: humanize(key)

    private fun humanize(key: String): String {
        if (!key.startsWith("app.")) return key
        return key.substringAfterLast('.')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
}

/**
 * Normalises both API generations' error envelopes:
 * - v2: `{"error":{"request":["app.x"],"inputs":{"field":"app.y"}}}`
 * - v1: `{"error":"app.x"}`, `{"error":["app.x"]}` or `{"error":{"field":["app.y"]}}`
 */
internal fun parseErrors(error: JsonElement?): Pair<List<String>, Map<String, String>> {
    fun strings(element: JsonElement?): List<String> = when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray -> element.flatMap { strings(it) }
        is JsonObject -> element.values.flatMap { strings(it) }
        else -> emptyList()
    }

    return when (error) {
        null -> emptyList<String>() to emptyMap()
        is JsonPrimitive, is JsonArray -> strings(error).map(ErrorMessages::translate) to emptyMap()
        is JsonObject -> {
            val request = strings(error["request"]) + strings(error["_form"])
            val inputs = (error["inputs"] as? JsonObject)
                ?: JsonObject(error.filterKeys { it != "request" && it != "_form" })
            request.map(ErrorMessages::translate) to fieldErrors(inputs)
        }
    }
}

