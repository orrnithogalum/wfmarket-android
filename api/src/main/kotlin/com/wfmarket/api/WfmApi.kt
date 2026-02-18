package com.wfmarket.api

import com.wfmarket.api.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplies per-request context, so the API layer needs no knowledge of how it's stored. */
interface WfmContext {
    val platform: String
    val language: String
    val crossplay: Boolean
    val token: String?

    /** Called when an authenticated request is rejected: the stored session is no longer valid. */
    fun onSessionExpired() {}
}

/**
 * The warframe.market HTTP API.
 *
 * The site is mid-migration: catalogue, orders and profile live on `/v2`, while sign-in, auctions,
 * chat, statistics, the ducanator and account settings are still on `/v1`. This class hides that
 * split behind one surface. It has no Android dependencies.
 */
class WfmApi(
    private val context: WfmContext,
    val http: OkHttpClient = defaultHttpClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    val json: Json get() = WfmJson

    // ---------------------------------------------------------------- Authentication

    /** Signs in and returns the session token. The password is sent once and never stored. */
    suspend fun signIn(email: String, password: String, deviceId: String): SignInResult {
        val body = buildJsonObject {
            put("auth_type", "header")
            put("email", email)
            put("password", password)
            put("device_id", deviceId)
        }
        val request = request(V1, "POST", "/auth/signin", emptyMap(), body, authenticated = false)
            .header("Authorization", "JWT")
            .build()
        return execute(request, authenticated = false) { response, text ->
            val fromHeader = response.header("Authorization")?.removePrefix("JWT ")?.removePrefix("Bearer ")?.trim()
            val fromCookie = response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("JWT=") }
                ?.substringAfter("JWT=")?.substringBefore(';')
            val token = fromHeader?.takeIf { it.isNotBlank() && it != "JWT" } ?: fromCookie
                ?: throw WfmException(WfmErrorKind.UNKNOWN, listOf("The server didn't return a session."))
            SignInResult(token = token, rawUser = WfmJson.parseToJsonElement(text).jsonObject["payload"])
        }
    }

    // ---------------------------------------------------------------- Catalogue (public, v2)

    // ---------------------------------------------------------------- Warframe's own data

    // ---------------------------------------------------------------- Statistics and tools (v1)

    // ---------------------------------------------------------------- Orders (v2)

    // ---------------------------------------------------------------- Users

    // ---------------------------------------------------------------- Account settings (v1)

    // ---------------------------------------------------------------- Auctions (v1)

    // ---------------------------------------------------------------- Chat (v1, sending is over the socket)

    // ---------------------------------------------------------------- Transport

    /** The auth scheme each API generation last accepted. Sign-in issues a token usable by both. */
    @Volatile private var v1Scheme = "JWT"
    @Volatile private var v2Scheme = "Bearer"


