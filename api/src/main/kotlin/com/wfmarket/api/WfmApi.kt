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

    suspend fun signOut() {
        runCatching { v1(JsonElement.serializer(), "POST", "/auth/signout", body = JsonObject(emptyMap())) }
    }

    // ---------------------------------------------------------------- Catalogue (public, v2)

    suspend fun versions(): Versions = v2(Versions.serializer(), "GET", "/versions")
    suspend fun items(): List<Item> = v2(ListSerializer(Item.serializer()), "GET", "/items")
    suspend fun item(slug: String): Item = v2(Item.serializer(), "GET", "/item/$slug")
    suspend fun itemSet(slug: String): ItemSet = v2(ItemSet.serializer(), "GET", "/item/$slug/set")
    suspend fun showcase(): Showcase = v2(Showcase.serializer(), "GET", "/dashboard/showcase")
    suspend fun rivenWeapons(): List<RivenWeapon> = v2(ListSerializer(RivenWeapon.serializer()), "GET", "/riven/weapons")
    suspend fun rivenAttributes(): List<RivenAttribute> = v2(ListSerializer(RivenAttribute.serializer()), "GET", "/riven/attributes")
    suspend fun lichWeapons(): List<NemesisWeapon> = v2(ListSerializer(NemesisWeapon.serializer()), "GET", "/lich/weapons")
    suspend fun lichEphemeras(): List<Ephemera> = v2(ListSerializer(Ephemera.serializer()), "GET", "/lich/ephemeras")
    suspend fun lichQuirks(): List<Quirk> = v2(ListSerializer(Quirk.serializer()), "GET", "/lich/quirks")
    suspend fun sisterWeapons(): List<NemesisWeapon> = v2(ListSerializer(NemesisWeapon.serializer()), "GET", "/sister/weapons")
    suspend fun sisterEphemeras(): List<Ephemera> = v2(ListSerializer(Ephemera.serializer()), "GET", "/sister/ephemeras")
    suspend fun sisterQuirks(): List<Quirk> = v2(ListSerializer(Quirk.serializer()), "GET", "/sister/quirks")
    suspend fun locations(): List<Location> = v2(ListSerializer(Location.serializer()), "GET", "/locations")
    suspend fun missions(): List<NamedEntity> = v2(ListSerializer(NamedEntity.serializer()), "GET", "/missions")
    suspend fun npcs(): List<NamedEntity> = v2(ListSerializer(NamedEntity.serializer()), "GET", "/npcs")
    suspend fun achievements(): List<Achievement> = v2(ListSerializer(Achievement.serializer()), "GET", "/achievements")

    // ---------------------------------------------------------------- Warframe's own data

    /**
     * The current Prime Resurgence, from the game's official world state, or null between rotations.
     * warframe.market's own showcase isn't used for this because it's maintained by hand and lags.
     */
    suspend fun primeResurgence(nowMillis: Long = System.currentTimeMillis()): PrimeResurgence? {
        val request = Request.Builder().url(WORLD_STATE).header("User-Agent", userAgent).get().build()
        val state = execute(request, authenticated = false) { _, text ->
            WfmJson.decodeFromString(WorldState.serializer(), text)
        }
        val trader = state.primeVaultTraders.firstOrNull {
            it.activation.date.millis <= nowMillis && nowMillis < it.expiry.date.millis
        } ?: return null
        val news = state.events
            .filter { event -> event.link?.contains("prime-resurgence") == true }
            .maxByOrNull { it.date.date.millis }
        val gameLanguage = when (context.language) {
            "zh-hant" -> "tc"
            "zh-hans" -> "zh"
            else -> context.language
        }
        return PrimeResurgence(
            expiresAtMillis = trader.expiry.date.millis,
            gameRefs = trader.manifest.map { it.itemType.replace("/StoreItems/", "/") },
            headline = news?.messages?.let { list ->
                (list.firstOrNull { it.language == gameLanguage } ?: list.firstOrNull { it.language == "en" })?.message
            },
            bannerUrl = news?.imageUrl,
            link = news?.link,
        )
    }

    /**
     * Last week's riven trades on [platform], which Warframe publishes every Monday, or null for
     * mobile, which it publishes none for.
     */
    suspend fun rivenTrades(platform: String): RivenTrades? {
        val file = RIVEN_TRADE_FILES[platform] ?: return null
        val request = Request.Builder().url("$RIVEN_TRADES$file.json").header("User-Agent", userAgent).get().build()
        return execute(request, authenticated = false) { response, text ->
            RivenTrades(
                platform = platform,
                publishedMillis = response.headers.getDate("Last-Modified")?.time ?: System.currentTimeMillis(),
                trades = WfmJson.decodeFromString(ListSerializer(RivenTrade.serializer()), relaxedJson(text)),
            )
        }
    }

    // ---------------------------------------------------------------- Statistics and tools (v1)

    suspend fun itemStatistics(slug: String): ItemStatistics = v1(ItemStatistics.serializer(), "GET", "/items/$slug/statistics")
    suspend fun dropSources(slug: String): List<DropSource> =
        v1(DropSourcesPayload.serializer(), "GET", "/items/$slug/dropsources").dropsources
    suspend fun ducats(): Ducats = v1(Ducats.serializer(), "GET", "/tools/ducats")

    // ---------------------------------------------------------------- Orders (v2)

    suspend fun recentOrders(): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/recent")
    suspend fun ordersForItem(slug: String): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/item/$slug")
    suspend fun topOrders(slug: String): TopOrders = v2(TopOrders.serializer(), "GET", "/orders/item/$slug/top")
    suspend fun ordersForUser(slug: String): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/user/$slug")
    suspend fun myOrders(): List<Order> = v2(ListSerializer(Order.serializer()), "GET", "/orders/my")

    suspend fun createOrder(order: CreateOrder): Order =
        v2(Order.serializer(), "POST", "/order", body = WfmJson.encodeToJsonElement(order))

    // ---------------------------------------------------------------- Users

    // ---------------------------------------------------------------- Account settings (v1)

    // ---------------------------------------------------------------- Auctions (v1)

    // ---------------------------------------------------------------- Chat (v1, sending is over the socket)

    // ---------------------------------------------------------------- Transport

    /** The auth scheme each API generation last accepted. Sign-in issues a token usable by both. */
    @Volatile private var v1Scheme = "JWT"
    @Volatile private var v2Scheme = "Bearer"


